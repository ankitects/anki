// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use fluent::{FluentArgs, FluentValue};

use super::Backend;
pub(super) use crate::backend_proto::i18n_service::Service as I18nService;
use crate::{
    backend_proto as pb,
    prelude::*,
    scheduler::timespan::{answer_button_time, time_span},
};

impl I18nService for Backend {
    fn translate_string(&self, input: pb::TranslateStringRequest) -> Result<pb::String> {
        let args = build_fluent_args(input.args);

        Ok(self
            .tr
            .translate_via_index(
                input.module_index as usize,
                input.message_index as usize,
                args,
            )
            .into())
    }

    fn format_timespan(&self, input: pb::FormatTimespanRequest) -> Result<pb::String> {
        use pb::format_timespan_request::Context;
        Ok(match input.context() {
            Context::Precise => time_span(input.seconds, &self.tr, true),
            Context::Intervals => time_span(input.seconds, &self.tr, false),
            Context::AnswerButtons => answer_button_time(input.seconds, &self.tr),
        }
        .into())
    }

    fn i18n_resources(&self, input: pb::I18nResourcesRequest) -> Result<pb::Json> {
        serde_json::to_vec(&self.tr.resources_for_js(&input.modules))
            .map(Into::into)
            .map_err(Into::into)
    }
}

fn build_fluent_args(input: HashMap<String, pb::TranslateArgValue>) -> FluentArgs<'static> {
    let mut args = FluentArgs::new();
    for (key, val) in input {
        args.set(key, translate_arg_to_fluent_val(&val));
    }
    args
}

fn translate_arg_to_fluent_val(arg: &pb::TranslateArgValue) -> FluentValue<'static> {
    use pb::translate_arg_value::Value as V;
    match &arg.value {
        Some(val) => match val {
            V::Str(s) => FluentValue::String(s.to_owned().into()),
            V::Number(f) => FluentValue::Number(f.into()),
        },
        None => FluentValue::String("".into()),
    }
}
