// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::generic;
pub(super) use anki_proto::i18n::i18n_service::Service as I18nService;
use fluent::FluentArgs;
use fluent::FluentValue;

use super::Backend;
use crate::prelude::*;
use crate::scheduler::timespan::answer_button_time;
use crate::scheduler::timespan::time_span;

impl I18nService for Backend {
    type Error = AnkiError;

    fn translate_string(
        &self,
        input: anki_proto::i18n::TranslateStringRequest,
    ) -> Result<generic::String> {
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

    fn format_timespan(
        &self,
        input: anki_proto::i18n::FormatTimespanRequest,
    ) -> Result<generic::String> {
        use anki_proto::i18n::format_timespan_request::Context;
        Ok(match input.context() {
            Context::Precise => time_span(input.seconds, &self.tr, true),
            Context::Intervals => time_span(input.seconds, &self.tr, false),
            Context::AnswerButtons => answer_button_time(input.seconds, &self.tr),
        }
        .into())
    }

    fn i18n_resources(
        &self,
        input: anki_proto::i18n::I18nResourcesRequest,
    ) -> Result<generic::Json> {
        serde_json::to_vec(&self.tr.resources_for_js(&input.modules))
            .map(Into::into)
            .map_err(Into::into)
    }
}

fn build_fluent_args(
    input: HashMap<String, anki_proto::i18n::TranslateArgValue>,
) -> FluentArgs<'static> {
    let mut args = FluentArgs::new();
    for (key, val) in input {
        args.set(key, translate_arg_to_fluent_val(&val));
    }
    args
}

fn translate_arg_to_fluent_val(arg: &anki_proto::i18n::TranslateArgValue) -> FluentValue<'static> {
    use anki_proto::i18n::translate_arg_value::Value as V;
    match &arg.value {
        Some(val) => match val {
            V::Str(s) => FluentValue::String(s.to_owned().into()),
            V::Number(f) => FluentValue::Number(f.into()),
        },
        None => FluentValue::String("".into()),
    }
}
