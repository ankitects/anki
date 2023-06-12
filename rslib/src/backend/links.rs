// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::links::help_page_link_request::HelpPage;
pub(super) use anki_proto::links::links_service::Service as LinksService;

use super::Backend;
use crate::links::help_page_to_link;
use crate::prelude::*;

impl LinksService for Backend {
    type Error = AnkiError;

    fn help_page_link(
        &self,
        input: anki_proto::links::HelpPageLinkRequest,
    ) -> Result<anki_proto::generic::String> {
        Ok(help_page_to_link(HelpPage::from_i32(input.page).unwrap_or(HelpPage::Index)).into())
    }
}
