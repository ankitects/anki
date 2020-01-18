// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto as pt;
use crate::backend_proto::backend_input::Value;
use crate::backend_proto::RenderedTemplateReplacement;
use crate::err::{AnkiError, Result};
use crate::sched::{local_minutes_west_for_stamp, sched_timing_today};
use crate::template::{
    render_card, without_legacy_template_directives, FieldMap, FieldRequirements, ParsedTemplate,
    RenderedNode,
};
use prost::Message;
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

pub struct Backend {
    #[allow(dead_code)]
    path: PathBuf,
}

/// Convert an Anki error to a protobuf error.
impl std::convert::From<AnkiError> for pt::BackendError {
    fn from(err: AnkiError) -> Self {
        use pt::backend_error::Value as V;
        let value = match err {
            AnkiError::InvalidInput { info } => V::InvalidInput(pt::InvalidInputError { info }),
            AnkiError::TemplateError { info } => V::TemplateParse(pt::TemplateParseError { info }),
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

impl Backend {
    pub fn new<P: Into<PathBuf>>(path: P) -> Backend {
        Backend { path: path.into() }
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

    fn run_command(&self, input: pt::BackendInput) -> pt::BackendOutput {
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
        &self,
        ival: pt::backend_input::Value,
    ) -> Result<pt::backend_output::Value> {
        use pt::backend_output::Value as OValue;
        Ok(match ival {
            Value::TemplateRequirements(input) => {
                OValue::TemplateRequirements(self.template_requirements(input)?)
            }
            Value::PlusOne(input) => OValue::PlusOne(self.plus_one(input)?),
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
        })
    }

    fn plus_one(&self, input: pt::PlusOneIn) -> Result<pt::PlusOneOut> {
        let num = input.num + 1;
        Ok(pt::PlusOneOut { num })
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
