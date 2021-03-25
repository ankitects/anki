// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::{
    backend_proto as pb,
    prelude::*,
    scheduler::timespan::{answer_button_time, time_span},
};
use fluent::FluentValue;
pub(super) use pb::i18n_service::Service as I18nService;

impl I18nService for Backend {
    fn translate_string(&self, input: pb::TranslateStringIn) -> Result<pb::String> {
        let key = input.key;
        let map = input
            .args
            .iter()
            .map(|(k, v)| (k.as_str(), translate_arg_to_fluent_val(&v)))
            .collect();

        Ok(self.i18n.trn2(key as usize, map).into())
    }

    fn format_timespan(&self, input: pb::FormatTimespanIn) -> Result<pb::String> {
        use pb::format_timespan_in::Context;
        Ok(match input.context() {
            Context::Precise => time_span(input.seconds, &self.i18n, true),
            Context::Intervals => time_span(input.seconds, &self.i18n, false),
            Context::AnswerButtons => answer_button_time(input.seconds, &self.i18n),
        }
        .into())
    }

    fn i18n_resources(&self, _input: pb::Empty) -> Result<pb::Json> {
        serde_json::to_vec(&self.i18n.resources_for_js())
            .map(Into::into)
            .map_err(Into::into)
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
