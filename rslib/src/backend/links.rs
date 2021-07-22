// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
pub(super) use crate::backend_proto::links_service::Service as LinksService;
use crate::{
    backend_proto as pb,
    backend_proto::links::help_page_link_request::{HelpPage as ProtoHelpPage, Page},
    links::{help_page_link, help_page_link_from_str, HelpPage},
    prelude::*,
};

impl LinksService for Backend {
    fn help_page_link(&self, input: pb::HelpPageLinkRequest) -> Result<pb::String> {
        Ok(match input.page {
            Some(Page::Variant(var)) => help_page_link(var.into()),
            Some(Page::Literal(s)) => help_page_link_from_str(&s),
            None => help_page_link_from_str(""),
        }
        .into())
    }
}

impl From<i32> for HelpPage {
    fn from(page: i32) -> Self {
        match ProtoHelpPage::from_i32(page).unwrap_or(ProtoHelpPage::Index) {
            ProtoHelpPage::NoteType => HelpPage::Notetype,
            ProtoHelpPage::Browsing => HelpPage::Browsing,
            ProtoHelpPage::BrowsingFindAndReplace => HelpPage::BrowsingFindAndReplace,
            ProtoHelpPage::BrowsingNotesMenu => HelpPage::BrowsingNotesMenu,
            ProtoHelpPage::KeyboardShortcuts => HelpPage::KeyboardShortcuts,
            ProtoHelpPage::Editing => HelpPage::Editing,
            ProtoHelpPage::AddingCardAndNote => HelpPage::AddingCardAndNote,
            ProtoHelpPage::AddingANoteType => HelpPage::AddingNotetype,
            ProtoHelpPage::Latex => HelpPage::Latex,
            ProtoHelpPage::Preferences => HelpPage::Preferences,
            ProtoHelpPage::Index => HelpPage::Index,
            ProtoHelpPage::Templates => HelpPage::Templates,
            ProtoHelpPage::FilteredDeck => HelpPage::FilteredDeck,
            ProtoHelpPage::Importing => HelpPage::Importing,
            ProtoHelpPage::CustomizingFields => HelpPage::CustomizingFields,
            ProtoHelpPage::DeckOptions => HelpPage::DeckOptions,
            ProtoHelpPage::EditingFeatures => HelpPage::EditingFeatures,
        }
    }
}
