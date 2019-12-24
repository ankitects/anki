use crate::err::{AnkiError, Result};
use crate::proto as pt;
use crate::proto::bridge_input::Value;
use crate::template::{FieldMap, FieldRequirements, ParsedTemplate};
use prost::Message;
use std::collections::HashSet;

pub struct Bridge {}

impl Default for Bridge {
    fn default() -> Self {
        Bridge {}
    }
}

/// Convert an Anki error to a protobuf error.
impl std::convert::From<AnkiError> for pt::BridgeError {
    fn from(err: AnkiError) -> Self {
        use pt::bridge_error::Value as V;
        let value = match err {
            AnkiError::InvalidInput { info } => V::InvalidInput(pt::InvalidInputError { info }),
            AnkiError::TemplateParseError { info } => {
                V::TemplateParse(pt::TemplateParseError { info })
            }
        };

        pt::BridgeError { value: Some(value) }
    }
}

// Convert an Anki error to a protobuf output.
impl std::convert::From<AnkiError> for pt::bridge_output::Value {
    fn from(err: AnkiError) -> Self {
        pt::bridge_output::Value::Error(err.into())
    }
}

impl Bridge {
    pub fn new() -> Bridge {
        Bridge::default()
    }

    /// Decode a request, process it, and return the encoded result.
    pub fn run_command_bytes(&mut self, req: &[u8]) -> Vec<u8> {
        let mut buf = vec![];

        let req = match pt::BridgeInput::decode(req) {
            Ok(req) => req,
            Err(_e) => {
                // unable to decode
                let err = AnkiError::invalid_input("couldn't decode bridge request");
                let output = pt::BridgeOutput {
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

    fn run_command(&self, input: pt::BridgeInput) -> pt::BridgeOutput {
        let oval = if let Some(ival) = input.value {
            match self.run_command_inner(ival) {
                Ok(output) => output,
                Err(err) => err.into(),
            }
        } else {
            AnkiError::invalid_input("unrecognized bridge input value").into()
        };

        pt::BridgeOutput { value: Some(oval) }
    }

    fn run_command_inner(&self, ival: pt::bridge_input::Value) -> Result<pt::bridge_output::Value> {
        use pt::bridge_output::Value as OValue;
        Ok(match ival {
            Value::TemplateRequirements(input) => {
                OValue::TemplateRequirements(self.template_requirements(input)?)
            }
            Value::PlusOne(input) => OValue::PlusOne(self.plus_one(input)?),
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
        use crate::proto::template_requirement::Value;
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
}

fn ords_hash_to_set(ords: HashSet<u16>) -> Vec<u32> {
    ords.iter().map(|ord| *ord as u32).collect()
}
