// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto as pt;
use crate::backend_proto::backend_input::Value;
use crate::backend_proto::{Empty, RenderedTemplateReplacement, SyncMediaIn};
use crate::err::{AnkiError, NetworkErrorKind, Result, SyncErrorKind};
use crate::latex::{extract_latex, ExtractedLatex};
use crate::media::check::MediaChecker;
use crate::media::files::remove_files;
use crate::media::sync::MediaSyncProgress;
use crate::media::MediaManager;
use crate::sched::{local_minutes_west_for_stamp, sched_timing_today};
use crate::template::{
    render_card, without_legacy_template_directives, FieldMap, FieldRequirements, ParsedTemplate,
    RenderedNode,
};
use crate::text::{extract_av_tags, strip_av_tags, AVTag};
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
}

enum Progress<'a> {
    MediaSync(&'a MediaSyncProgress),
    MediaCheck(u32),
}

/// Convert an Anki error to a protobuf error.
impl std::convert::From<AnkiError> for pt::BackendError {
    fn from(err: AnkiError) -> Self {
        use pt::backend_error::Value as V;
        let value = match err {
            AnkiError::InvalidInput { info } => V::InvalidInput(pt::StringError { info }),
            AnkiError::TemplateError { info, q_side } => {
                V::TemplateParse(pt::TemplateParseError { info, q_side })
            }
            AnkiError::IOError { info } => V::IoError(pt::StringError { info }),
            AnkiError::DBError { info } => V::DbError(pt::StringError { info }),
            AnkiError::NetworkError { info, kind } => V::NetworkError(pt::NetworkError {
                info,
                kind: kind.into(),
            }),
            AnkiError::SyncError { info, kind } => V::SyncError(pt::SyncError {
                info,
                kind: kind.into(),
            }),
            AnkiError::Interrupted => V::Interrupted(Empty {}),
        };

        pt::BackendError { value: Some(value) }
    }
}

// Convert an Anki error to a protobuf output.
impl std::convert::From<AnkiError> for pt::backend_output::Value {
    fn from(err: AnkiError) -> Self {
        pt::backend_output::Value::Error(err.into())
    }
}

impl std::convert::From<NetworkErrorKind> for i32 {
    fn from(e: NetworkErrorKind) -> Self {
        use pt::network_error::NetworkErrorKind as V;
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
        use pt::sync_error::SyncErrorKind as V;
        (match e {
            SyncErrorKind::Conflict => V::Conflict,
            SyncErrorKind::ServerError => V::ServerError,
            SyncErrorKind::ClientTooOld => V::ClientTooOld,
            SyncErrorKind::AuthFailed => V::AuthFailed,
            SyncErrorKind::ServerMessage => V::ServerMessage,
            SyncErrorKind::MediaCheckRequired => V::MediaCheckRequired,
            SyncErrorKind::ResyncRequired => V::ResyncRequired,
            SyncErrorKind::Other => V::Other,
        }) as i32
    }
}

pub fn init_backend(init_msg: &[u8]) -> std::result::Result<Backend, String> {
    let input: pt::BackendInit = match pt::BackendInit::decode(init_msg) {
        Ok(req) => req,
        Err(_) => return Err("couldn't decode init request".into()),
    };

    match Backend::new(
        &input.collection_path,
        &input.media_folder_path,
        &input.media_db_path,
    ) {
        Ok(backend) => Ok(backend),
        Err(e) => Err(format!("{:?}", e)),
    }
}

impl Backend {
    pub fn new(col_path: &str, media_folder: &str, media_db: &str) -> Result<Backend> {
        Ok(Backend {
            col_path: col_path.into(),
            media_folder: media_folder.into(),
            media_db: media_db.into(),
            progress_callback: None,
        })
    }

    /// Decode a request, process it, and return the encoded result.
    pub fn run_command_bytes(&mut self, req: &[u8]) -> Vec<u8> {
        let mut buf = vec![];

        let req = match pt::BackendInput::decode(req) {
            Ok(req) => req,
            Err(_e) => {
                // unable to decode
                let err = AnkiError::invalid_input("couldn't decode backend request");
                let output = pt::BackendOutput {
                    value: Some(err.into()),
                };
                output.encode(&mut buf).expect("encode failed");
                return buf;
            }
        };

        let resp = self.run_command(req);
        resp.encode(&mut buf).expect("encode failed");
        buf
    }

    fn run_command(&mut self, input: pt::BackendInput) -> pt::BackendOutput {
        let oval = if let Some(ival) = input.value {
            match self.run_command_inner(ival) {
                Ok(output) => output,
                Err(err) => err.into(),
            }
        } else {
            AnkiError::invalid_input("unrecognized backend input value").into()
        };

        pt::BackendOutput { value: Some(oval) }
    }

    fn run_command_inner(
        &mut self,
        ival: pt::backend_input::Value,
    ) -> Result<pt::backend_output::Value> {
        use pt::backend_output::Value as OValue;
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
            Value::AddFileToMediaFolder(input) => {
                OValue::AddFileToMediaFolder(self.add_file_to_media_folder(input)?)
            }
            Value::SyncMedia(input) => {
                self.sync_media(input)?;
                OValue::SyncMedia(Empty {})
            }
            Value::CheckMedia(_) => OValue::CheckMedia(self.check_media()?),
            Value::TrashMediaFiles(input) => {
                self.remove_media_files(&input.fnames)?;
                OValue::TrashMediaFiles(Empty {})
            }
        })
    }

    fn fire_progress_callback(&self, progress: Progress) -> bool {
        if let Some(cb) = &self.progress_callback {
            let bytes = progress_to_proto_bytes(progress);
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
        input: pt::TemplateRequirementsIn,
    ) -> Result<pt::TemplateRequirementsOut> {
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
                        FieldRequirements::Any(ords) => Value::Any(pt::TemplateRequirementAny {
                            ords: ords_hash_to_set(ords),
                        }),
                        FieldRequirements::All(ords) => Value::All(pt::TemplateRequirementAll {
                            ords: ords_hash_to_set(ords),
                        }),
                        FieldRequirements::None => Value::None(pt::Empty {}),
                    };
                    Ok(pt::TemplateRequirement { value: Some(val) })
                } else {
                    // template parsing failures make card unsatisfiable
                    Ok(pt::TemplateRequirement {
                        value: Some(Value::None(pt::Empty {})),
                    })
                }
            })
            .collect::<Result<Vec<_>>>()?;
        Ok(pt::TemplateRequirementsOut {
            requirements: all_reqs,
        })
    }

    fn sched_timing_today(&self, input: pt::SchedTimingTodayIn) -> pt::SchedTimingTodayOut {
        let today = sched_timing_today(
            input.created_secs as i64,
            input.created_mins_west,
            input.now_secs as i64,
            input.now_mins_west,
            input.rollover_hour as i8,
        );
        pt::SchedTimingTodayOut {
            days_elapsed: today.days_elapsed,
            next_day_at: today.next_day_at,
        }
    }

    fn render_template(&self, input: pt::RenderCardIn) -> Result<pt::RenderCardOut> {
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
        )?;

        // return
        Ok(pt::RenderCardOut {
            question_nodes: rendered_nodes_to_proto(qnodes),
            answer_nodes: rendered_nodes_to_proto(anodes),
        })
    }

    fn extract_av_tags(&self, input: pt::ExtractAvTagsIn) -> pt::ExtractAvTagsOut {
        let (text, tags) = extract_av_tags(&input.text, input.question_side);
        let pt_tags = tags
            .into_iter()
            .map(|avtag| match avtag {
                AVTag::SoundOrVideo(file) => pt::AvTag {
                    value: Some(pt::av_tag::Value::SoundOrVideo(file)),
                },
                AVTag::TextToSpeech {
                    field_text,
                    lang,
                    voices,
                    other_args,
                    speed,
                } => pt::AvTag {
                    value: Some(pt::av_tag::Value::Tts(pt::TtsTag {
                        field_text,
                        lang,
                        voices,
                        other_args,
                        speed,
                    })),
                },
            })
            .collect();

        pt::ExtractAvTagsOut {
            text: text.into(),
            av_tags: pt_tags,
        }
    }

    fn extract_latex(&self, input: pt::ExtractLatexIn) -> pt::ExtractLatexOut {
        let (text, extracted) = extract_latex(&input.text, input.svg);

        pt::ExtractLatexOut {
            text,
            latex: extracted
                .into_iter()
                .map(|e: ExtractedLatex| pt::ExtractedLatex {
                    filename: e.fname,
                    latex_body: e.latex,
                })
                .collect(),
        }
    }

    fn add_file_to_media_folder(&mut self, input: pt::AddFileToMediaFolderIn) -> Result<String> {
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
        rt.block_on(mgr.sync_media(callback, &input.endpoint, &input.hkey))
    }

    fn check_media(&self) -> Result<pt::MediaCheckOut> {
        let callback =
            |progress: usize| self.fire_progress_callback(Progress::MediaCheck(progress as u32));

        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        let mut checker = MediaChecker::new(&mgr, &self.col_path, callback);
        let output = checker.check()?;

        Ok(pt::MediaCheckOut {
            unused: output.unused,
            missing: output.missing,
            renamed: output.renamed,
            dirs: output.dirs,
            oversize: output.oversize,
        })
    }

    fn remove_media_files(&self, fnames: &[String]) -> Result<()> {
        remove_files(&self.media_folder, fnames)
    }
}

fn ords_hash_to_set(ords: HashSet<u16>) -> Vec<u32> {
    ords.iter().map(|ord| *ord as u32).collect()
}

fn rendered_nodes_to_proto(nodes: Vec<RenderedNode>) -> Vec<pt::RenderedTemplateNode> {
    nodes
        .into_iter()
        .map(|n| pt::RenderedTemplateNode {
            value: Some(rendered_node_to_proto(n)),
        })
        .collect()
}

fn rendered_node_to_proto(node: RenderedNode) -> pt::rendered_template_node::Value {
    match node {
        RenderedNode::Text { text } => pt::rendered_template_node::Value::Text(text),
        RenderedNode::Replacement {
            field_name,
            current_text,
            filters,
        } => pt::rendered_template_node::Value::Replacement(RenderedTemplateReplacement {
            field_name,
            current_text,
            filters,
        }),
    }
}

fn progress_to_proto_bytes(progress: Progress) -> Vec<u8> {
    let proto = pt::Progress {
        value: Some(match progress {
            Progress::MediaSync(p) => pt::progress::Value::MediaSync(pt::MediaSyncProgress {
                checked: p.checked as u32,
                downloaded_files: p.downloaded_files as u32,
                downloaded_deletions: p.downloaded_deletions as u32,
                uploaded_files: p.uploaded_files as u32,
                uploaded_deletions: p.uploaded_deletions as u32,
            }),
            Progress::MediaCheck(n) => pt::progress::Value::MediaCheck(n),
        }),
    };

    let mut buf = vec![];
    proto.encode(&mut buf).expect("encode failed");
    buf
}
