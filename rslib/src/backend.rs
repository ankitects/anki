// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto::backend_input::Value;
use crate::backend_proto::{Empty, RenderedTemplateReplacement, SyncMediaIn};
use crate::err::{AnkiError, NetworkErrorKind, Result, SyncErrorKind};
use crate::i18n::{tr_args, FString, I18n};
use crate::latex::{extract_latex, extract_latex_expanding_clozes, ExtractedLatex};
use crate::log::{default_logger, Logger};
use crate::media::check::MediaChecker;
use crate::media::sync::MediaSyncProgress;
use crate::media::MediaManager;
use crate::sched::cutoff::{local_minutes_west_for_stamp, sched_timing_today};
use crate::sched::timespan::{answer_button_time, learning_congrats, studied_today, time_span};
use crate::template::{
    render_card, without_legacy_template_directives, FieldMap, FieldRequirements, ParsedTemplate,
    RenderedNode,
};
use crate::text::{extract_av_tags, strip_av_tags, AVTag};
use crate::{backend_proto as pb, log};
use fluent::FluentValue;
use prost::Message;
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use tokio::runtime::Runtime;

pub type ProtoProgressCallback = Box<dyn Fn(Vec<u8>) -> bool + Send>;

pub struct Backend {
    #[allow(dead_code)]
    col_path: PathBuf,
    media_folder: PathBuf,
    media_db: String,
    progress_callback: Option<ProtoProgressCallback>,
    i18n: I18n,
    log: Logger,
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

    let mut path = input.collection_path.clone();
    path.push_str(".log");

    let log_path = match input.log_path.as_str() {
        "" => None,
        path => Some(path),
    };
    let logger =
        default_logger(log_path).map_err(|e| format!("Unable to open log file: {:?}", e))?;

    let i18n = I18n::new(
        &input.preferred_langs,
        input.locale_folder_path,
        log::terminal(),
    );

    match Backend::new(
        &input.collection_path,
        &input.media_folder_path,
        &input.media_db_path,
        i18n,
        logger,
    ) {
        Ok(backend) => Ok(backend),
        Err(e) => Err(format!("{:?}", e)),
    }
}

impl Backend {
    pub fn new(
        col_path: &str,
        media_folder: &str,
        media_db: &str,
        i18n: I18n,
        log: Logger,
    ) -> Result<Backend> {
        Ok(Backend {
            col_path: col_path.into(),
            media_folder: media_folder.into(),
            media_db: media_db.into(),
            progress_callback: None,
            i18n,
            log,
        })
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
            Value::FindCards(_) => todo!(),
            Value::BrowserRows(_) => todo!(),
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
        })
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
            input.created_secs as i64,
            input.created_mins_west,
            input.now_secs as i64,
            input.now_mins_west,
            input.rollover_hour as i8,
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
        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        let mut ctx = mgr.dbctx();
        Ok(mgr
            .add_file(&mut ctx, &input.desired_name, &input.data)?
            .into())
    }

    fn sync_media(&self, input: SyncMediaIn) -> Result<()> {
        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;

        let callback = |progress: &MediaSyncProgress| {
            self.fire_progress_callback(Progress::MediaSync(progress))
        };

        let mut rt = Runtime::new().unwrap();
        rt.block_on(mgr.sync_media(callback, &input.endpoint, &input.hkey, self.log.clone()))
    }

    fn check_media(&self) -> Result<pb::MediaCheckOut> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        let mut checker = MediaChecker::new(&mgr, &self.col_path, callback, &self.i18n, &self.log);
        let mut output = checker.check()?;

        let report = checker.summarize_output(&mut output);

        Ok(pb::MediaCheckOut {
            unused: output.unused,
            missing: output.missing,
            report,
            have_trash: output.trash_count > 0,
        })
    }

    fn remove_media_files(&self, fnames: &[String]) -> Result<()> {
        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        let mut ctx = mgr.dbctx();
        mgr.remove_files(&mut ctx, fnames)
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

        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        let mut checker = MediaChecker::new(&mgr, &self.col_path, callback, &self.i18n, &self.log);

        checker.empty_trash()
    }

    fn restore_trash(&self) -> Result<()> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        let mut checker = MediaChecker::new(&mgr, &self.col_path, callback, &self.i18n, &self.log);

        checker.restore_trash()
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
                let s = i18n.trn(FString::MediaCheckChecked, tr_args!["count"=>n]);
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
        checked: i18n.trn(FString::SyncMediaCheckedCount, tr_args!["count"=>p.checked]),
        added: i18n.trn(
            FString::SyncMediaAddedCount,
            tr_args!["up"=>p.uploaded_files,"down"=>p.downloaded_files],
        ),
        removed: i18n.trn(
            FString::SyncMediaRemovedCount,
            tr_args!["up"=>p.uploaded_deletions,"down"=>p.downloaded_deletions],
        ),
    }
}

/// Standalone I18n backend
/// This is a hack to allow translating strings in the GUI
/// when a collection is not open, and in the future it should
/// either be shared with or merged into the backend object.
///////////////////////////////////////////////////////

pub struct I18nBackend {
    i18n: I18n,
}

pub fn init_i18n_backend(init_msg: &[u8]) -> Result<I18nBackend> {
    let input: pb::I18nBackendInit = match pb::I18nBackendInit::decode(init_msg) {
        Ok(req) => req,
        Err(_) => return Err(AnkiError::invalid_input("couldn't decode init msg")),
    };

    let log = log::terminal();

    let i18n = I18n::new(&input.preferred_langs, input.locale_folder_path, log);

    Ok(I18nBackend { i18n })
}

impl I18nBackend {
    pub fn translate(&self, req: &[u8]) -> String {
        let req = match pb::TranslateStringIn::decode(req) {
            Ok(req) => req,
            Err(_e) => return "decoding error".into(),
        };

        self.translate_string(req)
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
}
