// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend::dbproxy::db_command_bytes,
    backend_proto as pb,
    backend_proto::builtin_search_order::BuiltinSortKind,
    backend_proto::{AddOrUpdateDeckConfigIn, Empty, RenderedTemplateReplacement, SyncMediaIn},
    card::{Card, CardID},
    card::{CardQueue, CardType},
    cloze::add_cloze_numbers_in_string,
    collection::{open_collection, Collection},
    config::SortKind,
    deckconf::{DeckConf, DeckConfID, DeckConfSchema11},
    decks::{Deck, DeckID, DeckSchema11},
    err::{AnkiError, NetworkErrorKind, Result, SyncErrorKind},
    i18n::{tr_args, I18n, TR},
    latex::{extract_latex, extract_latex_expanding_clozes, ExtractedLatex},
    log,
    log::{default_logger, Logger},
    media::check::MediaChecker,
    media::sync::MediaSyncProgress,
    media::MediaManager,
    notes::{Note, NoteID},
    notetype::{
        all_stock_notetypes, CardTemplateSchema11, NoteType, NoteTypeID, NoteTypeSchema11,
        RenderCardOutput,
    },
    sched::cutoff::local_minutes_west_for_stamp,
    sched::timespan::{answer_button_time, learning_congrats, studied_today, time_span},
    search::SortMode,
    template::RenderedNode,
    text::{extract_av_tags, strip_av_tags, AVTag},
    timestamp::TimestampSecs,
    types::Usn,
};
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
        AnkiError::JSONError { info } => V::JsonError(info),
        AnkiError::ProtoError { info } => V::ProtoError(info),
        AnkiError::NotFound => V::NotFoundError(Empty {}),
        AnkiError::Existing => V::Exists(Empty {}),
        AnkiError::DeckIsFiltered => V::DeckIsFiltered(Empty {}),
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
            Value::SchedTimingToday(_) => OValue::SchedTimingToday(self.sched_timing_today()?),
            Value::DeckTree(input) => OValue::DeckTree(self.deck_tree(input)?),
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
            Value::GetChangedNotetypes(_) => {
                OValue::GetChangedNotetypes(self.get_changed_notetypes()?)
            }
            Value::GetAllDecks(_) => OValue::GetAllDecks(self.get_all_decks()?),
            Value::GetStockNotetypeLegacy(kind) => {
                OValue::GetStockNotetypeLegacy(self.get_stock_notetype_legacy(kind)?)
            }
            Value::GetNotetypeLegacy(id) => {
                OValue::GetNotetypeLegacy(self.get_notetype_legacy(id)?)
            }
            Value::GetNotetypeNames(_) => OValue::GetNotetypeNames(self.get_notetype_names()?),
            Value::GetNotetypeNamesAndCounts(_) => {
                OValue::GetNotetypeNamesAndCounts(self.get_notetype_use_counts()?)
            }

            Value::GetNotetypeIdByName(name) => {
                OValue::GetNotetypeIdByName(self.get_notetype_id_by_name(name)?)
            }
            Value::AddOrUpdateNotetype(input) => {
                OValue::AddOrUpdateNotetype(self.add_or_update_notetype_legacy(input)?)
            }
            Value::RemoveNotetype(id) => {
                self.remove_notetype(id)?;
                OValue::RemoveNotetype(pb::Empty {})
            }
            Value::NewNote(ntid) => OValue::NewNote(self.new_note(ntid)?),
            Value::AddNote(input) => OValue::AddNote(self.add_note(input)?),
            Value::UpdateNote(note) => {
                self.update_note(note)?;
                OValue::UpdateNote(pb::Empty {})
            }
            Value::GetNote(nid) => OValue::GetNote(self.get_note(nid)?),
            Value::GetEmptyCards(_) => OValue::GetEmptyCards(self.get_empty_cards()?),
            Value::GetDeckLegacy(did) => OValue::GetDeckLegacy(self.get_deck_legacy(did)?),
            Value::GetDeckIdByName(name) => {
                OValue::GetDeckIdByName(self.get_deck_id_by_name(&name)?)
            }
            Value::GetDeckNames(input) => OValue::GetDeckNames(self.get_deck_names(input)?),
            Value::AddOrUpdateDeckLegacy(input) => {
                OValue::AddOrUpdateDeckLegacy(self.add_or_update_deck_legacy(input)?)
            }
            Value::NewDeckLegacy(filtered) => {
                OValue::NewDeckLegacy(self.new_deck_legacy(filtered)?)
            }

            Value::RemoveDeck(did) => OValue::RemoveDeck({
                self.remove_deck(did)?;
                pb::Empty {}
            }),
            Value::CheckDatabase(_) => OValue::CheckDatabase(self.check_database()?),
            Value::DeckTreeLegacy(_) => OValue::DeckTreeLegacy(self.deck_tree_legacy()?),
            Value::FieldNamesForNotes(input) => {
                OValue::FieldNamesForNotes(self.field_names_for_notes(input)?)
            }
            Value::FindAndReplace(input) => OValue::FindAndReplace(self.find_and_replace(input)?),
            Value::AfterNoteUpdates(input) => {
                OValue::AfterNoteUpdates(self.after_note_updates(input)?)
            }
            Value::AddNoteTags(input) => OValue::AddNoteTags(self.add_note_tags(input)?),
            Value::UpdateNoteTags(input) => OValue::UpdateNoteTags(self.update_note_tags(input)?),
            Value::SetLocalMinutesWest(mins) => OValue::SetLocalMinutesWest({
                self.set_local_mins_west(mins)?;
                pb::Empty {}
            }),
            Value::GetPreferences(_) => OValue::GetPreferences(self.get_preferences()?),
            Value::SetPreferences(prefs) => OValue::SetPreferences({
                self.set_preferences(prefs)?;
                pb::Empty {}
            }),
            Value::RenderExistingCard(input) => {
                OValue::RenderExistingCard(self.render_existing_card(input)?)
            }
            Value::RenderUncommittedCard(input) => {
                OValue::RenderUncommittedCard(self.render_uncommitted_card(input)?)
            }
            Value::ClozeNumbersInNote(note) => {
                OValue::ClozeNumbersInNote(self.cloze_numbers_in_note(note))
            }
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

    fn sched_timing_today(&self) -> Result<pb::SchedTimingTodayOut> {
        self.with_col(|col| col.timing_today().map(Into::into))
    }

    fn deck_tree(&self, input: pb::DeckTreeIn) -> Result<pb::DeckTreeNode> {
        let lim = if input.top_deck_id > 0 {
            Some(DeckID(input.top_deck_id))
        } else {
            None
        };
        self.with_col(|col| col.deck_tree(input.include_counts, lim))
    }

    fn render_existing_card(&self, input: pb::RenderExistingCardIn) -> Result<pb::RenderCardOut> {
        self.with_col(|col| {
            col.render_existing_card(CardID(input.card_id), input.browser)
                .map(Into::into)
        })
    }

    fn render_uncommitted_card(
        &self,
        input: pb::RenderUncommittedCardIn,
    ) -> Result<pb::RenderCardOut> {
        let schema11: CardTemplateSchema11 = serde_json::from_slice(&input.template)?;
        let template = schema11.into();
        let mut note = input
            .note
            .ok_or_else(|| AnkiError::invalid_input("missing note"))?
            .into();
        let ord = input.card_ord as u16;
        let fill_empty = input.fill_empty;
        self.with_col(|col| {
            col.render_uncommitted_card(&mut note, &template, ord, fill_empty)
                .map(Into::into)
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
            let cids = col.search_cards(&input.search, order)?;
            Ok(pb::SearchCardsOut {
                card_ids: cids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn search_notes(&self, input: pb::SearchNotesIn) -> Result<pb::SearchNotesOut> {
        self.with_col(|col| {
            let nids = col.search_notes(&input.search)?;
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
            let conf: DeckConfSchema11 = conf.into();
            Ok(serde_json::to_vec(&conf)?)
        })
    }

    fn add_or_update_deck_config(&self, input: AddOrUpdateDeckConfigIn) -> Result<i64> {
        let conf: DeckConfSchema11 = serde_json::from_slice(&input.config)?;
        let mut conf: DeckConf = conf.into();
        self.with_col(|col| {
            col.transact(None, |col| {
                col.add_or_update_deck_config(&mut conf, input.preserve_usn_and_mtime)?;
                Ok(conf.id.0)
            })
        })
    }

    fn all_deck_config(&self) -> Result<Vec<u8>> {
        self.with_col(|col| {
            let conf: Vec<DeckConfSchema11> = col
                .storage
                .all_deck_config()?
                .into_iter()
                .map(Into::into)
                .collect();
            serde_json::to_vec(&conf).map_err(Into::into)
        })
    }

    fn new_deck_config(&self) -> Result<Vec<u8>> {
        serde_json::to_vec(&DeckConfSchema11::default()).map_err(Into::into)
    }

    fn remove_deck_config(&self, dcid: i64) -> Result<()> {
        self.with_col(|col| col.transact(None, |col| col.remove_deck_config(DeckConfID(dcid))))
    }

    fn before_upload(&self) -> Result<()> {
        self.with_col(|col| col.before_upload())
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
                            col.set_config(input.key.as_str(), &val)
                        }
                        pb::set_config_json::Op::Remove(_) => col.remove_config(input.key.as_str()),
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

    fn get_changed_notetypes(&self) -> Result<Vec<u8>> {
        todo!("filter by usn");
        // self.with_col(|col| {
        //     let nts = col.storage.get_all_notetypes_as_schema11()?;
        //     serde_json::to_vec(&nts).map_err(Into::into)
        // })
    }

    fn get_all_decks(&self) -> Result<Vec<u8>> {
        self.with_col(|col| {
            let decks = col.storage.get_all_decks_as_schema11()?;
            serde_json::to_vec(&decks).map_err(Into::into)
        })
    }

    fn get_notetype_names(&self) -> Result<pb::NoteTypeNames> {
        self.with_col(|col| {
            let entries: Vec<_> = col
                .storage
                .get_all_notetype_names()?
                .into_iter()
                .map(|(id, name)| pb::NoteTypeNameId { id: id.0, name })
                .collect();
            Ok(pb::NoteTypeNames { entries })
        })
    }

    fn get_notetype_use_counts(&self) -> Result<pb::NoteTypeUseCounts> {
        self.with_col(|col| {
            let entries: Vec<_> = col
                .storage
                .get_notetype_use_counts()?
                .into_iter()
                .map(|(id, name, use_count)| pb::NoteTypeNameIdUseCount {
                    id: id.0,
                    name,
                    use_count,
                })
                .collect();
            Ok(pb::NoteTypeUseCounts { entries })
        })
    }

    fn get_notetype_legacy(&self, id: i64) -> Result<Vec<u8>> {
        self.with_col(|col| {
            let schema11: NoteTypeSchema11 = col
                .storage
                .get_notetype(NoteTypeID(id))?
                .ok_or(AnkiError::NotFound)?
                .into();
            Ok(serde_json::to_vec(&schema11)?)
        })
    }

    fn get_notetype_id_by_name(&self, name: String) -> Result<i64> {
        self.with_col(|col| {
            col.storage
                .get_notetype_id(&name)
                .map(|nt| nt.unwrap_or(NoteTypeID(0)).0)
        })
    }

    fn add_or_update_notetype_legacy(&self, input: pb::AddOrUpdateNotetypeIn) -> Result<i64> {
        self.with_col(|col| {
            let legacy: NoteTypeSchema11 = serde_json::from_slice(&input.json)?;
            let mut nt: NoteType = legacy.into();
            if nt.id.0 == 0 {
                col.add_notetype(&mut nt)?;
            } else {
                col.update_notetype(&mut nt, input.preserve_usn_and_mtime)?;
            }
            Ok(nt.id.0)
        })
    }

    fn remove_notetype(&self, id: i64) -> Result<()> {
        self.with_col(|col| col.remove_notetype(NoteTypeID(id)))
    }

    fn new_note(&self, ntid: i64) -> Result<pb::Note> {
        self.with_col(|col| {
            let nt = col
                .get_notetype(NoteTypeID(ntid))?
                .ok_or(AnkiError::NotFound)?;
            Ok(nt.new_note().into())
        })
    }

    fn add_note(&self, input: pb::AddNoteIn) -> Result<i64> {
        self.with_col(|col| {
            let mut note: Note = input.note.ok_or(AnkiError::NotFound)?.into();
            col.add_note(&mut note, DeckID(input.deck_id))
                .map(|_| note.id.0)
        })
    }

    fn update_note(&self, pbnote: pb::Note) -> Result<()> {
        self.with_col(|col| {
            let mut note: Note = pbnote.into();
            col.update_note(&mut note)
        })
    }

    fn get_note(&self, nid: i64) -> Result<pb::Note> {
        self.with_col(|col| {
            col.storage
                .get_note(NoteID(nid))?
                .ok_or(AnkiError::NotFound)
                .map(Into::into)
        })
    }

    fn get_empty_cards(&self) -> Result<pb::EmptyCardsReport> {
        self.with_col(|col| {
            let mut empty = col.empty_cards()?;
            let report = col.empty_cards_report(&mut empty)?;

            let mut outnotes = vec![];
            for (_ntid, notes) in empty {
                outnotes.extend(notes.into_iter().map(|e| pb::NoteWithEmptyCards {
                    note_id: e.nid.0,
                    will_delete_note: e.empty.len() == e.current_count,
                    card_ids: e.empty.into_iter().map(|(_ord, id)| id.0).collect(),
                }))
            }
            Ok(pb::EmptyCardsReport {
                report,
                notes: outnotes,
            })
        })
    }

    fn get_deck_legacy(&self, did: i64) -> Result<Vec<u8>> {
        self.with_col(|col| {
            let deck: DeckSchema11 = col
                .storage
                .get_deck(DeckID(did))?
                .ok_or(AnkiError::NotFound)?
                .into();
            serde_json::to_vec(&deck).map_err(Into::into)
        })
    }

    fn get_deck_id_by_name(&self, human_name: &str) -> Result<i64> {
        self.with_col(|col| {
            col.get_deck_id(human_name)
                .map(|d| d.map(|d| d.0).unwrap_or_default())
        })
    }

    fn get_deck_names(&self, input: pb::GetDeckNamesIn) -> Result<pb::DeckNames> {
        self.with_col(|col| {
            let names = if input.include_filtered {
                col.get_all_deck_names(input.skip_empty_default)?
            } else {
                col.get_all_normal_deck_names()?
            };
            Ok(pb::DeckNames {
                entries: names
                    .into_iter()
                    .map(|(id, name)| pb::DeckNameId { id: id.0, name })
                    .collect(),
            })
        })
    }

    fn add_or_update_deck_legacy(&self, input: pb::AddOrUpdateDeckLegacyIn) -> Result<i64> {
        self.with_col(|col| {
            let schema11: DeckSchema11 = serde_json::from_slice(&input.deck)?;
            let mut deck: Deck = schema11.into();
            if input.preserve_usn_and_mtime {
                col.transact(None, |col| {
                    let usn = col.usn()?;
                    col.add_or_update_single_deck(&mut deck, usn)
                })?;
            } else {
                col.add_or_update_deck(&mut deck)?;
            }
            Ok(deck.id.0)
        })
    }

    fn new_deck_legacy(&self, filtered: bool) -> Result<Vec<u8>> {
        let deck = if filtered {
            Deck::new_filtered()
        } else {
            Deck::new_normal()
        };
        let schema11: DeckSchema11 = deck.into();
        serde_json::to_vec(&schema11).map_err(Into::into)
    }

    fn remove_deck(&self, did: i64) -> Result<()> {
        self.with_col(|col| col.remove_deck_and_child_decks(DeckID(did)))
    }

    fn check_database(&self) -> Result<pb::CheckDatabaseOut> {
        self.with_col(|col| {
            col.check_database().map(|problems| pb::CheckDatabaseOut {
                problems: problems.to_i18n_strings(&col.i18n),
            })
        })
    }

    fn deck_tree_legacy(&self) -> Result<Vec<u8>> {
        self.with_col(|col| {
            let tree = col.legacy_deck_tree()?;
            serde_json::to_vec(&tree).map_err(Into::into)
        })
    }

    fn field_names_for_notes(
        &self,
        input: pb::FieldNamesForNotesIn,
    ) -> Result<pb::FieldNamesForNotesOut> {
        self.with_col(|col| {
            let nids: Vec<_> = input.nids.into_iter().map(NoteID).collect();
            col.storage
                .field_names_for_notes(&nids)
                .map(|fields| pb::FieldNamesForNotesOut { fields })
        })
    }

    fn find_and_replace(&self, input: pb::FindAndReplaceIn) -> Result<u32> {
        let mut search = if input.regex {
            input.search
        } else {
            regex::escape(&input.search)
        };
        if !input.match_case {
            search = format!("(?i){}", search);
        }
        let nids = input.nids.into_iter().map(NoteID).collect();
        let field_name = if input.field_name.is_empty() {
            None
        } else {
            Some(input.field_name)
        };
        let repl = input.replacement;
        self.with_col(|col| {
            col.find_and_replace(nids, &search, &repl, field_name)
                .map(|cnt| cnt as u32)
        })
    }

    fn after_note_updates(&self, input: pb::AfterNoteUpdatesIn) -> Result<pb::Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.after_note_updates(
                    &to_nids(input.nids),
                    input.generate_cards,
                    input.mark_notes_modified,
                )?;
                Ok(pb::Empty {})
            })
        })
    }

    fn add_note_tags(&self, input: pb::AddNoteTagsIn) -> Result<u32> {
        self.with_col(|col| {
            col.add_tags_for_notes(&to_nids(input.nids), &input.tags)
                .map(|n| n as u32)
        })
    }

    fn update_note_tags(&self, input: pb::UpdateNoteTagsIn) -> Result<u32> {
        self.with_col(|col| {
            col.replace_tags_for_notes(
                &to_nids(input.nids),
                &input.tags,
                &input.replacement,
                input.regex,
            )
            .map(|n| n as u32)
        })
    }

    fn set_local_mins_west(&self, mins: i32) -> Result<()> {
        self.with_col(|col| col.transact(None, |col| col.set_local_mins_west(mins)))
    }

    fn get_preferences(&self) -> Result<pb::Preferences> {
        self.with_col(|col| col.get_preferences())
    }

    fn set_preferences(&self, prefs: pb::Preferences) -> Result<()> {
        self.with_col(|col| col.transact(None, |col| col.set_preferences(prefs)))
    }

    fn cloze_numbers_in_note(&self, note: pb::Note) -> pb::ClozeNumbersInNoteOut {
        let mut set = HashSet::with_capacity(4);
        for field in &note.fields {
            add_cloze_numbers_in_string(field, &mut set);
        }
        pb::ClozeNumbersInNoteOut {
            numbers: set.into_iter().map(|n| n as u32).collect(),
        }
    }

    fn get_stock_notetype_legacy(&self, kind: i32) -> Result<Vec<u8>> {
        // fixme: use individual functions instead of full vec
        let mut all = all_stock_notetypes(&self.i18n);
        let idx = (kind as usize).min(all.len() - 1);
        let nt = all.swap_remove(idx);
        let schema11: NoteTypeSchema11 = nt.into();
        serde_json::to_vec(&schema11).map_err(Into::into)
    }
}

fn to_nids(ids: Vec<i64>) -> Vec<NoteID> {
    ids.into_iter().map(NoteID).collect()
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

impl From<RenderCardOutput> for pb::RenderCardOut {
    fn from(o: RenderCardOutput) -> Self {
        pb::RenderCardOut {
            question_nodes: rendered_nodes_to_proto(o.qnodes),
            answer_nodes: rendered_nodes_to_proto(o.anodes),
        }
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
    match BuiltinSortKind::from_i32(kind) {
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

impl From<crate::sched::cutoff::SchedTimingToday> for pb::SchedTimingTodayOut {
    fn from(t: crate::sched::cutoff::SchedTimingToday) -> pb::SchedTimingTodayOut {
        pb::SchedTimingTodayOut {
            days_elapsed: t.days_elapsed,
            next_day_at: t.next_day_at,
        }
    }
}
