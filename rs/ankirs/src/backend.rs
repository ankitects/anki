use crate::backend_proto as pt;
use crate::backend_proto::backend_input::Value;
use crate::err::{AnkiError, Result};
use crate::sched::sched_timing_today;
use crate::template::{FieldMap, FieldRequirements, ParsedTemplate};
use prost::Message;
use std::collections::HashSet;

pub struct Backend {}

impl Default for Backend {
    fn default() -> Self {
        Backend {}
    }
}

/// Convert an Anki error to a protobuf error.
impl std::convert::From<AnkiError> for pt::BackendError {
    fn from(err: AnkiError) -> Self {
        use pt::backend_error::Value as V;
        let value = match err {
            AnkiError::InvalidInput { info } => V::InvalidInput(pt::InvalidInputError { info }),
            AnkiError::TemplateParseError { info } => {
                V::TemplateParse(pt::TemplateParseError { info })
            }
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
    pub fn new() -> Backend {
        Backend::default()
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
                if let Ok(tmpl) = ParsedTemplate::from_text(&template) {
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
            input.created as i64,
            input.now as i64,
            input.minutes_west,
            input.rollover_hour as i8,
        );
        pt::SchedTimingTodayOut {
            days_elapsed: today.days_elapsed,
            next_day_at: today.next_day_at,
        }
    }
}

fn ords_hash_to_set(ords: HashSet<u16>) -> Vec<u32> {
    ords.iter().map(|ord| *ord as u32).collect()
}
