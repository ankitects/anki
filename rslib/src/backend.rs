// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto as pt;
use crate::backend_proto::backend_input::Value;
use crate::backend_proto::RenderedTemplateReplacement;
use crate::cloze::expand_clozes_to_reveal_latex;
use crate::err::{AnkiError, Result};
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

pub struct Backend {
    #[allow(dead_code)]
    col_path: PathBuf,
    media_manager: Option<MediaManager>,
}

/// Convert an Anki error to a protobuf error.
impl std::convert::From<AnkiError> for pt::BackendError {
    fn from(err: AnkiError) -> Self {
        use pt::backend_error::Value as V;
        let value = match err {
            AnkiError::InvalidInput { info } => V::InvalidInput(pt::StringError { info }),
            AnkiError::TemplateError { info, q_side } => {
                V::TemplateParse(pt::TemplateParseError { info, q_side })
            },
            AnkiError::IOError { info } => V::IoError(pt::StringError { info }),
            AnkiError::DBError { info } => V::DbError(pt::StringError { info }),
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
        let media_manager = match (media_folder.is_empty(), media_db.is_empty()) {
            (false, false) => Some(MediaManager::new(media_folder, media_db)?),
            _ => None,
        };

        Ok(Backend {
            col_path: col_path.into(),
            media_manager,
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
            Value::ExpandClozesToRevealLatex(input) => {
                OValue::ExpandClozesToRevealLatex(expand_clozes_to_reveal_latex(&input))
            }
            Value::AddFileToMediaFolder(input) => {
                OValue::AddFileToMediaFolder(self.add_file_to_media_folder(input)?)
            }
        })
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

    fn add_file_to_media_folder(&mut self, input: pt::AddFileToMediaFolderIn) -> Result<String> {
        Ok(self
            .media_manager
            .as_mut()
            .unwrap()
            .add_file(&input.desired_name, &input.data)?
            .into())
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
