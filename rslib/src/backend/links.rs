// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
pub(super) use crate::backend_proto::links_service::Service as LinksService;
use crate::{
    backend_proto as pb, backend_proto::links::help_page_link_request::HelpPage, prelude::*,
};

impl LinksService for Backend {
    fn help_page_link(&self, input: pb::HelpPageLinkRequest) -> Result<pb::String> {
        Ok(HelpPage::from_i32(input.page)
            .unwrap_or(HelpPage::Index)
            .to_link()
            .into())
    }
}
