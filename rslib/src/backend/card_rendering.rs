// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::card_rendering::StripHtmlRequest;

use crate::backend::Backend;
use crate::card_rendering::service::strip_html_proto;
use crate::services::BackendCardRenderingService;

impl BackendCardRenderingService for Backend {
    fn strip_html(
        &self,
        input: StripHtmlRequest,
    ) -> crate::error::Result<anki_proto::generic::String> {
        strip_html_proto(input)
    }
}
