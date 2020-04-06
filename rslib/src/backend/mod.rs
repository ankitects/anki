// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend::dbproxy::db_command_bytes;
use crate::backend_proto::{
    AddOrUpdateDeckConfigIn, BuiltinSortKind, Empty, RenderedTemplateReplacement, SyncMediaIn,
};
use crate::card::{Card, CardID};
use crate::card::{CardQueue, CardType};
use crate::collection::{open_collection, Collection};
use crate::config::SortKind;
use crate::deckconf::{DeckConf, DeckConfID};
use crate::decks::DeckID;
use crate::err::{AnkiError, NetworkErrorKind, Result, SyncErrorKind};
use crate::i18n::{tr_args, I18n, TR};
use crate::latex::{extract_latex, extract_latex_expanding_clozes, ExtractedLatex};
use crate::log::{default_logger, Logger};
use crate::media::check::MediaChecker;
use crate::media::sync::MediaSyncProgress;
use crate::media::MediaManager;
use crate::notes::NoteID;
use crate::sched::cutoff::{local_minutes_west_for_stamp, sched_timing_today};
use crate::sched::timespan::{answer_button_time, learning_congrats, studied_today, time_span};
use crate::search::{search_cards, search_notes, SortMode};
use crate::template::{
    render_card, without_legacy_template_directives, FieldMap, FieldRequirements, ParsedTemplate,
    RenderedNode,
};
use crate::text::{extract_av_tags, strip_av_tags, AVTag};
use crate::timestamp::TimestampSecs;
use crate::types::Usn;
use crate::{backend_proto as pb, log};
use fluent::FluentValue;
use futures::future::{AbortHandle, Abortable};
use log::error;
use pb::backend_input::Value;
use prost::Message;
use serde_json::Value as JsonValue;
use std::collections::{HashMap, HashSet};
use std::convert::TryFrom;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use tokio::runtime::Runtime;

mod dbproxy;

pub type ProtoProgressCallback = Box<dyn Fn(Vec<u8>) -> bool + Send>;

pub struct Backend {
    col: Arc<Mutex<Option<Collection>>>,
    progress_callback: Option<ProtoProgressCallback>,
    i18n: I18n,
    server: bool,
    media_sync_abort: Option<AbortHandle>,
}

enum Progress<'a> {
    MediaSync(&'a MediaSyncProgress),
    MediaCheck(u32),
}

/// Convert an Anki error to a protobuf error.
fn anki_error_to_proto_error(err: AnkiError, i18n: &I18n) -> pb::BackendError {
    use pb::backend_error::Value as V;
    let localized = err.localized_description(i18n);
    let value = match err {
        AnkiError::InvalidInput { .. } => V::InvalidInput(pb::Empty {}),
        AnkiError::TemplateError { .. } => V::TemplateParse(pb::Empty {}),
        AnkiError::IOError { .. } => V::IoError(pb::Empty {}),
        AnkiError::DBError { .. } => V::DbError(pb::Empty {}),
        AnkiError::NetworkError { kind, .. } => {
            V::NetworkError(pb::NetworkError { kind: kind.into() })
        }
        AnkiError::SyncError { kind, .. } => V::SyncError(pb::SyncError { kind: kind.into() }),
        AnkiError::Interrupted => V::Interrupted(Empty {}),
        AnkiError::CollectionNotOpen => V::InvalidInput(pb::Empty {}),
        AnkiError::CollectionAlreadyOpen => V::InvalidInput(pb::Empty {}),
        AnkiError::SchemaChange => V::InvalidInput(pb::Empty {}),
        AnkiError::JSONError { info } => V::JsonError(info),
    };

    pb::BackendError {
        value: Some(value),
        localized,
    }
}

// Convert an Anki error to a protobuf output.
impl std::convert::From<pb::BackendError> for pb::backend_output::Value {
    fn from(err: pb::BackendError) -> Self {
        pb::backend_output::Value::Error(err)
    }
}

impl std::convert::From<NetworkErrorKind> for i32 {
    fn from(e: NetworkErrorKind) -> Self {
        use pb::network_error::NetworkErrorKind as V;
        (match e {
            NetworkErrorKind::Offline => V::Offline,
            NetworkErrorKind::Timeout => V::Timeout,
            NetworkErrorKind::ProxyAuth => V::ProxyAuth,
            NetworkErrorKind::Other => V::Other,
        }) as i32
    }
}

impl std::convert::From<SyncErrorKind> for i32 {
    fn from(e: SyncErrorKind) -> Self {
        use pb::sync_error::SyncErrorKind as V;
        (match e {
            SyncErrorKind::Conflict => V::Conflict,
            SyncErrorKind::ServerError => V::ServerError,
            SyncErrorKind::ClientTooOld => V::ClientTooOld,
            SyncErrorKind::AuthFailed => V::AuthFailed,
            SyncErrorKind::ServerMessage => V::ServerMessage,
            SyncErrorKind::ResyncRequired => V::ResyncRequired,
            SyncErrorKind::Other => V::Other,
        }) as i32
    }
}

pub fn init_backend(init_msg: &[u8]) -> std::result::Result<Backend, String> {
    let input: pb::BackendInit = match pb::BackendInit::decode(init_msg) {
        Ok(req) => req,
        Err(_) => return Err("couldn't decode init request".into()),
    };

    let i18n = I18n::new(
        &input.preferred_langs,
        input.locale_folder_path,
        log::terminal(),
    );

    Ok(Backend::new(i18n, input.server))
}

impl Backend {
    pub fn new(i18n: I18n, server: bool) -> Backend {
        Backend {
            col: Arc::new(Mutex::new(None)),
            progress_callback: None,
            i18n,
            server,
            media_sync_abort: None,
        }
    }

    pub fn i18n(&self) -> &I18n {
        &self.i18n
    }

    /// Decode a request, process it, and return the encoded result.
    pub fn run_command_bytes(&mut self, req: &[u8]) -> Vec<u8> {
        let mut buf = vec![];

        let req = match pb::BackendInput::decode(req) {
            Ok(req) => req,
            Err(_e) => {
                // unable to decode
                let err = AnkiError::invalid_input("couldn't decode backend request");
                let oerr = anki_error_to_proto_error(err, &self.i18n);
                let output = pb::BackendOutput {
                    value: Some(oerr.into()),
                };
                output.encode(&mut buf).expect("encode failed");
                return buf;
            }
        };

        let resp = self.run_command(req);
        resp.encode(&mut buf).expect("encode failed");
        buf
    }

    /// If collection is open, run the provided closure while holding
    /// the mutex.
    /// If collection is not open, return an error.
    fn with_col<F, T>(&self, func: F) -> Result<T>
    where
        F: FnOnce(&mut Collection) -> Result<T>,
    {
        func(
            self.col
                .lock()
                .unwrap()
                .as_mut()
                .ok_or(AnkiError::CollectionNotOpen)?,
        )
    }

    fn run_command(&mut self, input: pb::BackendInput) -> pb::BackendOutput {
        let oval = if let Some(ival) = input.value {
            match self.run_command_inner(ival) {
                Ok(output) => output,
                Err(err) => anki_error_to_proto_error(err, &self.i18n).into(),
            }
        } else {
            anki_error_to_proto_error(
                AnkiError::invalid_input("unrecognized backend input value"),
                &self.i18n,
            )
            .into()
        };

        pb::BackendOutput { value: Some(oval) }
    }

    fn run_command_inner(
        &mut self,
        ival: pb::backend_input::Value,
    ) -> Result<pb::backend_output::Value> {
        use pb::backend_output::Value as OValue;
        Ok(match ival {
            Value::TemplateRequirements(input) => {
                OValue::TemplateRequirements(self.template_requirements(input)?)
            }
            Value::SchedTimingToday(input) => {
                OValue::SchedTimingToday(self.sched_timing_today(input))
            }
            Value::DeckTree(_) => todo!(),
            Value::RenderCard(input) => OValue::RenderCard(self.render_template(input)?),
            Value::LocalMinutesWest(stamp) => {
                OValue::LocalMinutesWest(local_minutes_west_for_stamp(stamp))
            }
            Value::StripAvTags(text) => OValue::StripAvTags(strip_av_tags(&text).into()),
            Value::ExtractAvTags(input) => OValue::ExtractAvTags(self.extract_av_tags(input)),
            Value::ExtractLatex(input) => OValue::ExtractLatex(self.extract_latex(input)),
            Value::AddMediaFile(input) => OValue::AddMediaFile(self.add_media_file(input)?),
            Value::SyncMedia(input) => {
                self.sync_media(input)?;
                OValue::SyncMedia(Empty {})
            }
            Value::CheckMedia(_) => OValue::CheckMedia(self.check_media()?),
            Value::TrashMediaFiles(input) => {
                self.remove_media_files(&input.fnames)?;
                OValue::TrashMediaFiles(Empty {})
            }
            Value::TranslateString(input) => OValue::TranslateString(self.translate_string(input)),
            Value::FormatTimeSpan(input) => OValue::FormatTimeSpan(self.format_time_span(input)),
            Value::StudiedToday(input) => OValue::StudiedToday(studied_today(
                input.cards as usize,
                input.seconds as f32,
                &self.i18n,
            )),
            Value::CongratsLearnMsg(input) => OValue::CongratsLearnMsg(learning_congrats(
                input.remaining as usize,
                input.next_due,
                &self.i18n,
            )),
            Value::EmptyTrash(_) => {
                self.empty_trash()?;
                OValue::EmptyTrash(Empty {})
            }
            Value::RestoreTrash(_) => {
                self.restore_trash()?;
                OValue::RestoreTrash(Empty {})
            }
            Value::OpenCollection(input) => {
                self.open_collection(input)?;
                OValue::OpenCollection(Empty {})
            }
            Value::CloseCollection(input) => {
                self.close_collection(input.downgrade_to_schema11)?;
                OValue::CloseCollection(Empty {})
            }
            Value::SearchCards(input) => OValue::SearchCards(self.search_cards(input)?),
            Value::SearchNotes(input) => OValue::SearchNotes(self.search_notes(input)?),
            Value::GetCard(cid) => OValue::GetCard(self.get_card(cid)?),
            Value::UpdateCard(card) => {
                self.update_card(card)?;
                OValue::UpdateCard(pb::Empty {})
            }
            Value::AddCard(card) => OValue::AddCard(self.add_card(card)?),
            Value::GetDeckConfig(dcid) => OValue::GetDeckConfig(self.get_deck_config(dcid)?),
            Value::AddOrUpdateDeckConfig(input) => {
                OValue::AddOrUpdateDeckConfig(self.add_or_update_deck_config(input)?)
            }
            Value::AllDeckConfig(_) => OValue::AllDeckConfig(self.all_deck_config()?),
            Value::NewDeckConfig(_) => OValue::NewDeckConfig(self.new_deck_config()?),
            Value::RemoveDeckConfig(dcid) => {
                self.remove_deck_config(dcid)?;
                OValue::RemoveDeckConfig(pb::Empty {})
            }
            Value::AbortMediaSync(_) => {
                self.abort_media_sync();
                OValue::AbortMediaSync(pb::Empty {})
            }
            Value::BeforeUpload(_) => {
                self.before_upload()?;
                OValue::BeforeUpload(pb::Empty {})
            }
            Value::CanonifyTags(input) => OValue::CanonifyTags(self.canonify_tags(input)?),
            Value::AllTags(_) => OValue::AllTags(self.all_tags()?),
            Value::RegisterTags(input) => OValue::RegisterTags(self.register_tags(input)?),
            Value::GetChangedTags(usn) => OValue::GetChangedTags(self.get_changed_tags(usn)?),
            Value::GetConfigJson(key) => OValue::GetConfigJson(self.get_config_json(&key)?),
            Value::SetConfigJson(input) => OValue::SetConfigJson({
                self.set_config_json(input)?;
                pb::Empty {}
            }),

            Value::SetAllConfig(input) => OValue::SetConfigJson({
                self.set_all_config(&input)?;
                pb::Empty {}
            }),
            Value::GetAllConfig(_) => OValue::GetAllConfig(self.get_all_config()?),
        })
    }

    fn open_collection(&self, input: pb::OpenCollectionIn) -> Result<()> {
        let mut col = self.col.lock().unwrap();
        if col.is_some() {
            return Err(AnkiError::CollectionAlreadyOpen);
        }

        let mut path = input.collection_path.clone();
        path.push_str(".log");

        let log_path = match input.log_path.as_str() {
            "" => None,
            path => Some(path),
        };
        let logger = default_logger(log_path)?;

        let new_col = open_collection(
            input.collection_path,
            input.media_folder_path,
            input.media_db_path,
            self.server,
            self.i18n.clone(),
            logger,
        )?;

        *col = Some(new_col);

        Ok(())
    }

    fn close_collection(&self, downgrade: bool) -> Result<()> {
        let mut col = self.col.lock().unwrap();
        if col.is_none() {
            return Err(AnkiError::CollectionNotOpen);
        }

        if !col.as_ref().unwrap().can_close() {
            return Err(AnkiError::invalid_input("can't close yet"));
        }

        let col_inner = col.take().unwrap();
        if downgrade {
            let log = log::terminal();
            if let Err(e) = col_inner.close(downgrade) {
                error!(log, " failed: {:?}", e);
            }
        }

        Ok(())
    }

    fn fire_progress_callback(&self, progress: Progress) -> bool {
        if let Some(cb) = &self.progress_callback {
            let bytes = progress_to_proto_bytes(progress, &self.i18n);
            cb(bytes)
        } else {
            true
        }
    }

    pub fn set_progress_callback(&mut self, progress_cb: Option<ProtoProgressCallback>) {
        self.progress_callback = progress_cb;
    }

    fn template_requirements(
        &self,
        input: pb::TemplateRequirementsIn,
    ) -> Result<pb::TemplateRequirementsOut> {
        let map: FieldMap = input
            .field_names_to_ordinals
            .iter()
            .map(|(name, ord)| (name.as_str(), *ord as u16))
            .collect();
        // map each provided template into a requirements list
        use crate::backend_proto::template_requirement::Value;
        let all_reqs = input
            .template_front
            .into_iter()
            .map(|template| {
                let normalized = without_legacy_template_directives(&template);
                if let Ok(tmpl) = ParsedTemplate::from_text(normalized.as_ref()) {
                    // convert the rust structure into a protobuf one
                    let val = match tmpl.requirements(&map) {
                        FieldRequirements::Any(ords) => Value::Any(pb::TemplateRequirementAny {
                            ords: ords_hash_to_set(ords),
                        }),
                        FieldRequirements::All(ords) => Value::All(pb::TemplateRequirementAll {
                            ords: ords_hash_to_set(ords),
                        }),
                        FieldRequirements::None => Value::None(pb::Empty {}),
                    };
                    Ok(pb::TemplateRequirement { value: Some(val) })
                } else {
                    // template parsing failures make card unsatisfiable
                    Ok(pb::TemplateRequirement {
                        value: Some(Value::None(pb::Empty {})),
                    })
                }
            })
            .collect::<Result<Vec<_>>>()?;
        Ok(pb::TemplateRequirementsOut {
            requirements: all_reqs,
        })
    }

    fn sched_timing_today(&self, input: pb::SchedTimingTodayIn) -> pb::SchedTimingTodayOut {
        let today = sched_timing_today(
            TimestampSecs(input.created_secs),
            TimestampSecs(input.now_secs),
            input.created_mins_west.map(|v| v.val),
            input.now_mins_west.map(|v| v.val),
            input.rollover_hour.map(|v| v.val as i8),
        );
        pb::SchedTimingTodayOut {
            days_elapsed: today.days_elapsed,
            next_day_at: today.next_day_at,
        }
    }

    fn render_template(&self, input: pb::RenderCardIn) -> Result<pb::RenderCardOut> {
        // convert string map to &str
        let fields: HashMap<_, _> = input
            .fields
            .iter()
            .map(|(k, v)| (k.as_ref(), v.as_ref()))
            .collect();

        // render
        let (qnodes, anodes) = render_card(
            &input.question_template,
            &input.answer_template,
            &fields,
            input.card_ordinal as u16,
            &self.i18n,
        )?;

        // return
        Ok(pb::RenderCardOut {
            question_nodes: rendered_nodes_to_proto(qnodes),
            answer_nodes: rendered_nodes_to_proto(anodes),
        })
    }

    fn extract_av_tags(&self, input: pb::ExtractAvTagsIn) -> pb::ExtractAvTagsOut {
        let (text, tags) = extract_av_tags(&input.text, input.question_side);
        let pt_tags = tags
            .into_iter()
            .map(|avtag| match avtag {
                AVTag::SoundOrVideo(file) => pb::AvTag {
                    value: Some(pb::av_tag::Value::SoundOrVideo(file)),
                },
                AVTag::TextToSpeech {
                    field_text,
                    lang,
                    voices,
                    other_args,
                    speed,
                } => pb::AvTag {
                    value: Some(pb::av_tag::Value::Tts(pb::TtsTag {
                        field_text,
                        lang,
                        voices,
                        other_args,
                        speed,
                    })),
                },
            })
            .collect();

        pb::ExtractAvTagsOut {
            text: text.into(),
            av_tags: pt_tags,
        }
    }

    fn extract_latex(&self, input: pb::ExtractLatexIn) -> pb::ExtractLatexOut {
        let func = if input.expand_clozes {
            extract_latex_expanding_clozes
        } else {
            extract_latex
        };
        let (text, extracted) = func(&input.text, input.svg);

        pb::ExtractLatexOut {
            text,
            latex: extracted
                .into_iter()
                .map(|e: ExtractedLatex| pb::ExtractedLatex {
                    filename: e.fname,
                    latex_body: e.latex,
                })
                .collect(),
        }
    }

    fn add_media_file(&mut self, input: pb::AddMediaFileIn) -> Result<String> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            Ok(mgr
                .add_file(&mut ctx, &input.desired_name, &input.data)?
                .into())
        })
    }

    fn sync_media(&mut self, input: SyncMediaIn) -> Result<()> {
        let mut guard = self.col.lock().unwrap();

        let col = guard.as_mut().unwrap();
        col.set_media_sync_running()?;

        let folder = col.media_folder.clone();
        let db = col.media_db.clone();
        let log = col.log.clone();

        drop(guard);

        let res = self.sync_media_inner(input, folder, db, log);

        self.with_col(|col| col.set_media_sync_finished())?;

        res
    }

    fn sync_media_inner(
        &mut self,
        input: pb::SyncMediaIn,
        folder: PathBuf,
        db: PathBuf,
        log: Logger,
    ) -> Result<()> {
        let (abort_handle, abort_reg) = AbortHandle::new_pair();
        self.media_sync_abort = Some(abort_handle);

        let callback = |progress: &MediaSyncProgress| {
            self.fire_progress_callback(Progress::MediaSync(progress))
        };

        let mgr = MediaManager::new(&folder, &db)?;
        let mut rt = Runtime::new().unwrap();
        let sync_fut = mgr.sync_media(callback, &input.endpoint, &input.hkey, log);
        let abortable_sync = Abortable::new(sync_fut, abort_reg);
        let ret = match rt.block_on(abortable_sync) {
            Ok(sync_result) => sync_result,
            Err(_) => {
                // aborted sync
                Err(AnkiError::Interrupted)
            }
        };
        self.media_sync_abort = None;
        ret
    }

    fn abort_media_sync(&mut self) {
        if let Some(handle) = self.media_sync_abort.take() {
            handle.abort();
        }
    }

    fn check_media(&self) -> Result<pb::MediaCheckOut> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, callback);
                let mut output = checker.check()?;

                let report = checker.summarize_output(&mut output);

                Ok(pb::MediaCheckOut {
                    unused: output.unused,
                    missing: output.missing,
                    report,
                    have_trash: output.trash_count > 0,
                })
            })
        })
    }

    fn remove_media_files(&self, fnames: &[String]) -> Result<()> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            mgr.remove_files(&mut ctx, fnames)
        })
    }

    fn translate_string(&self, input: pb::TranslateStringIn) -> String {
        let key = match pb::FluentString::from_i32(input.key) {
            Some(key) => key,
            None => return "invalid key".to_string(),
        };

        let map = input
            .args
            .iter()
            .map(|(k, v)| (k.as_str(), translate_arg_to_fluent_val(&v)))
            .collect();

        self.i18n.trn(key, map)
    }

    fn format_time_span(&self, input: pb::FormatTimeSpanIn) -> String {
        let context = match pb::format_time_span_in::Context::from_i32(input.context) {
            Some(context) => context,
            None => return "".to_string(),
        };
        match context {
            pb::format_time_span_in::Context::Precise => time_span(input.seconds, &self.i18n, true),
            pb::format_time_span_in::Context::Intervals => {
                time_span(input.seconds, &self.i18n, false)
            }
            pb::format_time_span_in::Context::AnswerButtons => {
                answer_button_time(input.seconds, &self.i18n)
            }
        }
    }

    fn empty_trash(&self) -> Result<()> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, callback);

                checker.empty_trash()
            })
        })
    }

    fn restore_trash(&self) -> Result<()> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;

            col.transact(None, |ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, callback);

                checker.restore_trash()
            })
        })
    }

    pub fn db_command(&self, input: &[u8]) -> Result<String> {
        self.with_col(|col| db_command_bytes(&col.storage, input))
    }

    fn search_cards(&self, input: pb::SearchCardsIn) -> Result<pb::SearchCardsOut> {
        self.with_col(|col| {
            let order = if let Some(order) = input.order {
                use pb::sort_order::Value as V;
                match order.value {
                    Some(V::None(_)) => SortMode::NoOrder,
                    Some(V::Custom(s)) => SortMode::Custom(s),
                    Some(V::FromConfig(_)) => SortMode::FromConfig,
                    Some(V::Builtin(b)) => SortMode::Builtin {
                        kind: sort_kind_from_pb(b.kind),
                        reverse: b.reverse,
                    },
                    None => SortMode::FromConfig,
                }
            } else {
                SortMode::FromConfig
            };
            let cids = search_cards(col, &input.search, order)?;
            Ok(pb::SearchCardsOut {
                card_ids: cids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn search_notes(&self, input: pb::SearchNotesIn) -> Result<pb::SearchNotesOut> {
        self.with_col(|col| {
            let nids = search_notes(col, &input.search)?;
            Ok(pb::SearchNotesOut {
                note_ids: nids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn get_card(&self, cid: i64) -> Result<pb::GetCardOut> {
        let card = self.with_col(|col| col.storage.get_card(CardID(cid)))?;
        Ok(pb::GetCardOut {
            card: card.map(card_to_pb),
        })
    }

    fn update_card(&self, pbcard: pb::Card) -> Result<()> {
        let mut card = pbcard_to_native(pbcard)?;
        self.with_col(|col| {
            col.transact(None, |ctx| {
                let orig = ctx
                    .storage
                    .get_card(card.id)?
                    .ok_or_else(|| AnkiError::invalid_input("missing card"))?;
                ctx.update_card(&mut card, &orig)
            })
        })
    }

    fn add_card(&self, pbcard: pb::Card) -> Result<i64> {
        let mut card = pbcard_to_native(pbcard)?;
        self.with_col(|col| col.transact(None, |ctx| ctx.add_card(&mut card)))?;
        Ok(card.id.0)
    }

    fn get_deck_config(&self, dcid: i64) -> Result<Vec<u8>> {
        self.with_col(|col| {
            let conf = col.get_deck_config(DeckConfID(dcid), true)?.unwrap();
            Ok(serde_json::to_vec(&conf)?)
        })
    }

    fn add_or_update_deck_config(&self, input: AddOrUpdateDeckConfigIn) -> Result<i64> {
        let mut conf: DeckConf = serde_json::from_slice(&input.config)?;
        self.with_col(|col| {
            col.transact(None, |col| {
                col.add_or_update_deck_config(&mut conf, input.preserve_usn_and_mtime)?;
                Ok(conf.id.0)
            })
        })
    }

    fn all_deck_config(&self) -> Result<Vec<u8>> {
        self.with_col(|col| serde_json::to_vec(&col.storage.all_deck_config()?).map_err(Into::into))
    }

    fn new_deck_config(&self) -> Result<Vec<u8>> {
        serde_json::to_vec(&DeckConf::default()).map_err(Into::into)
    }

    fn remove_deck_config(&self, dcid: i64) -> Result<()> {
        self.with_col(|col| col.transact(None, |col| col.remove_deck_config(DeckConfID(dcid))))
    }

    fn before_upload(&self) -> Result<()> {
        self.with_col(|col| col.transact(None, |col| col.before_upload()))
    }

    fn canonify_tags(&self, tags: String) -> Result<pb::CanonifyTagsOut> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.canonify_tags(&tags, col.usn()?)
                    .map(|(tags, added)| pb::CanonifyTagsOut {
                        tags,
                        tag_list_changed: added,
                    })
            })
        })
    }

    fn all_tags(&self) -> Result<pb::AllTagsOut> {
        let tags = self.with_col(|col| col.storage.all_tags())?;
        let tags: Vec<_> = tags
            .into_iter()
            .map(|(tag, usn)| pb::TagUsnTuple { tag, usn: usn.0 })
            .collect();
        Ok(pb::AllTagsOut { tags })
    }

    fn register_tags(&self, input: pb::RegisterTagsIn) -> Result<bool> {
        self.with_col(|col| {
            col.transact(None, |col| {
                let usn = if input.preserve_usn {
                    Usn(input.usn)
                } else {
                    col.usn()?
                };
                col.register_tags(&input.tags, usn, input.clear_first)
            })
        })
    }

    fn get_changed_tags(&self, usn: i32) -> Result<pb::GetChangedTagsOut> {
        self.with_col(|col| {
            col.transact(None, |col| {
                Ok(pb::GetChangedTagsOut {
                    tags: col.storage.get_changed_tags(Usn(usn))?,
                })
            })
        })
    }

    fn get_config_json(&self, key: &str) -> Result<Vec<u8>> {
        self.with_col(|col| {
            let val: Option<JsonValue> = col.get_config_optional(key);
            match val {
                None => Ok(vec![]),
                Some(val) => Ok(serde_json::to_vec(&val)?),
            }
        })
    }

    fn set_config_json(&self, input: pb::SetConfigJson) -> Result<()> {
        self.with_col(|col| {
            col.transact(None, |col| {
                if let Some(op) = input.op {
                    match op {
                        pb::set_config_json::Op::Val(val) => {
                            // ensure it's a well-formed object
                            let val: JsonValue = serde_json::from_slice(&val)?;
                            col.set_config(&input.key, &val)
                        }
                        pb::set_config_json::Op::Remove(_) => col.remove_config(&input.key),
                    }
                } else {
                    Err(AnkiError::invalid_input("no op received"))
                }
            })
        })
    }

    fn set_all_config(&self, conf: &[u8]) -> Result<()> {
        let val: HashMap<String, JsonValue> = serde_json::from_slice(conf)?;
        self.with_col(|col| {
            col.transact(None, |col| {
                col.storage
                    .set_all_config(val, col.usn()?, TimestampSecs::now())
            })
        })
    }

    fn get_all_config(&self) -> Result<Vec<u8>> {
        self.with_col(|col| {
            let conf = col.storage.get_all_config()?;
            serde_json::to_vec(&conf).map_err(Into::into)
        })
    }
}

fn translate_arg_to_fluent_val(arg: &pb::TranslateArgValue) -> FluentValue {
    use pb::translate_arg_value::Value as V;
    match &arg.value {
        Some(val) => match val {
            V::Str(s) => FluentValue::String(s.into()),
            V::Number(f) => FluentValue::Number(f.into()),
        },
        None => FluentValue::String("".into()),
    }
}

fn ords_hash_to_set(ords: HashSet<u16>) -> Vec<u32> {
    ords.iter().map(|ord| *ord as u32).collect()
}

fn rendered_nodes_to_proto(nodes: Vec<RenderedNode>) -> Vec<pb::RenderedTemplateNode> {
    nodes
        .into_iter()
        .map(|n| pb::RenderedTemplateNode {
            value: Some(rendered_node_to_proto(n)),
        })
        .collect()
}

fn rendered_node_to_proto(node: RenderedNode) -> pb::rendered_template_node::Value {
    match node {
        RenderedNode::Text { text } => pb::rendered_template_node::Value::Text(text),
        RenderedNode::Replacement {
            field_name,
            current_text,
            filters,
        } => pb::rendered_template_node::Value::Replacement(RenderedTemplateReplacement {
            field_name,
            current_text,
            filters,
        }),
    }
}

fn progress_to_proto_bytes(progress: Progress, i18n: &I18n) -> Vec<u8> {
    let proto = pb::Progress {
        value: Some(match progress {
            Progress::MediaSync(p) => pb::progress::Value::MediaSync(media_sync_progress(p, i18n)),
            Progress::MediaCheck(n) => {
                let s = i18n.trn(TR::MediaCheckChecked, tr_args!["count"=>n]);
                pb::progress::Value::MediaCheck(s)
            }
        }),
    };

    let mut buf = vec![];
    proto.encode(&mut buf).expect("encode failed");
    buf
}

fn media_sync_progress(p: &MediaSyncProgress, i18n: &I18n) -> pb::MediaSyncProgress {
    pb::MediaSyncProgress {
        checked: i18n.trn(TR::SyncMediaCheckedCount, tr_args!["count"=>p.checked]),
        added: i18n.trn(
            TR::SyncMediaAddedCount,
            tr_args!["up"=>p.uploaded_files,"down"=>p.downloaded_files],
        ),
        removed: i18n.trn(
            TR::SyncMediaRemovedCount,
            tr_args!["up"=>p.uploaded_deletions,"down"=>p.downloaded_deletions],
        ),
    }
}

fn sort_kind_from_pb(kind: i32) -> SortKind {
    use SortKind as SK;
    match pb::BuiltinSortKind::from_i32(kind) {
        Some(pbkind) => match pbkind {
            BuiltinSortKind::NoteCreation => SK::NoteCreation,
            BuiltinSortKind::NoteMod => SK::NoteMod,
            BuiltinSortKind::NoteField => SK::NoteField,
            BuiltinSortKind::NoteTags => SK::NoteTags,
            BuiltinSortKind::NoteType => SK::NoteType,
            BuiltinSortKind::CardMod => SK::CardMod,
            BuiltinSortKind::CardReps => SK::CardReps,
            BuiltinSortKind::CardDue => SK::CardDue,
            BuiltinSortKind::CardEase => SK::CardEase,
            BuiltinSortKind::CardLapses => SK::CardLapses,
            BuiltinSortKind::CardInterval => SK::CardInterval,
            BuiltinSortKind::CardDeck => SK::CardDeck,
            BuiltinSortKind::CardTemplate => SK::CardTemplate,
        },
        _ => SortKind::NoteCreation,
    }
}

fn card_to_pb(c: Card) -> pb::Card {
    pb::Card {
        id: c.id.0,
        nid: c.nid.0,
        did: c.did.0,
        ord: c.ord as u32,
        mtime: c.mtime.0,
        usn: c.usn.0,
        ctype: c.ctype as u32,
        queue: c.queue as i32,
        due: c.due,
        ivl: c.ivl,
        factor: c.factor as u32,
        reps: c.reps,
        lapses: c.lapses,
        left: c.left,
        odue: c.odue,
        odid: c.odid.0,
        flags: c.flags as u32,
        data: c.data,
    }
}

fn pbcard_to_native(c: pb::Card) -> Result<Card> {
    let ctype = CardType::try_from(c.ctype as u8)
        .map_err(|_| AnkiError::invalid_input("invalid card type"))?;
    let queue = CardQueue::try_from(c.queue as i8)
        .map_err(|_| AnkiError::invalid_input("invalid card queue"))?;
    Ok(Card {
        id: CardID(c.id),
        nid: NoteID(c.nid),
        did: DeckID(c.did),
        ord: c.ord as u16,
        mtime: TimestampSecs(c.mtime),
        usn: Usn(c.usn),
        ctype,
        queue,
        due: c.due,
        ivl: c.ivl,
        factor: c.factor as u16,
        reps: c.reps,
        lapses: c.lapses,
        left: c.left,
        odue: c.odue,
        odid: DeckID(c.odid),
        flags: c.flags as u8,
        data: c.data,
    })
}
