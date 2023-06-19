// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;

use anki_i18n::I18n;
use anki_proto::generic;
use anki_proto::generic::Json;
use anki_proto::i18n::format_timespan_request::Context;
use anki_proto::i18n::FormatTimespanRequest;
use anki_proto::i18n::I18nResourcesRequest;
use anki_proto::i18n::TranslateStringRequest;
use fluent_bundle::FluentArgs;
use fluent_bundle::FluentValue;

use crate::collection::Collection;
use crate::error;
use crate::scheduler::timespan::answer_button_time;
use crate::scheduler::timespan::time_span;

impl crate::services::I18nService for Collection {
    fn translate_string(
        &mut self,
        input: TranslateStringRequest,
    ) -> error::Result<generic::String> {
        translate_string(&self.tr, input)
    }

    fn format_timespan(&mut self, input: FormatTimespanRequest) -> error::Result<generic::String> {
        format_timespan(&self.tr, input)
    }

    fn i18n_resources(&mut self, input: I18nResourcesRequest) -> error::Result<Json> {
        i18n_resources(&self.tr, input)
    }
}

pub(crate) fn translate_string(
    tr: &I18n,
    input: TranslateStringRequest,
) -> error::Result<generic::String> {
    let args = build_fluent_args(input.args);
    Ok(tr
        .translate_via_index(
            input.module_index as usize,
            input.message_index as usize,
            args,
        )
        .into())
}

pub(crate) fn format_timespan(
    tr: &I18n,
    input: FormatTimespanRequest,
) -> error::Result<generic::String> {
    Ok(match input.context() {
        Context::Precise => time_span(input.seconds, tr, true),
        Context::Intervals => time_span(input.seconds, tr, false),
        Context::AnswerButtons => answer_button_time(input.seconds, tr),
    }
    .into())
}

pub(crate) fn i18n_resources(
    tr: &I18n,
    input: I18nResourcesRequest,
) -> error::Result<generic::Json> {
    serde_json::to_vec(&tr.resources_for_js(&input.modules))
        .map(Into::into)
        .map_err(Into::into)
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
