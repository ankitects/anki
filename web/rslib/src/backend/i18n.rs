// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::generic;
use anki_proto::i18n::FormatTimespanRequest;
use anki_proto::i18n::I18nResourcesRequest;
use anki_proto::i18n::TranslateStringRequest;

use super::Backend;
use crate::i18n::service;
use crate::prelude::*;

// We avoid delegating to collection for these, as tr doesn't require a
// collection lock.
impl crate::services::BackendI18nService for Backend {
    fn translate_string(&self, input: TranslateStringRequest) -> Result<generic::String> {
        service::translate_string(&self.tr, input)
    }

    fn format_timespan(&self, input: FormatTimespanRequest) -> Result<generic::String> {
        service::format_timespan(&self.tr, input)
    }

    fn i18n_resources(&self, input: I18nResourcesRequest) -> Result<generic::Json> {
        service::i18n_resources(&self.tr, input)
    }
}
