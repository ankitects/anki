// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use anki_proto::links::help_page_link_request::HelpPage;

use crate::collection::Collection;
use crate::error;

static HELP_SITE: &str = "https://docs.ankiweb.net/manual/";

pub fn help_page_to_link(page: HelpPage) -> String {
    format!("{}{}", HELP_SITE, help_page_link_suffix(page))
}

pub fn help_page_link_suffix(page: HelpPage) -> &'static str {
    match page {
        HelpPage::NoteType => "getting-started#note-types",
        HelpPage::Browsing => "browsing",
        HelpPage::BrowsingFindAndReplace => "browsing#find-and-replace",
        HelpPage::BrowsingNotesMenu => "browsing#notes",
        HelpPage::KeyboardShortcuts => "studying#keyboard-shortcuts",
        HelpPage::Editing => "editing",
        HelpPage::AddingCardAndNote => "editing#adding-cards-and-notes",
        HelpPage::AddingANoteType => "editing#adding-a-note-type",
        HelpPage::Latex => "math#latex",
        HelpPage::Preferences => "preferences",
        HelpPage::Index => "intro",
        HelpPage::Templates => "templates/intro",
        HelpPage::FilteredDeck => "filtered-decks",
        HelpPage::Importing => "importing/intro",
        HelpPage::CustomizingFields => "editing#customizing-fields",
        HelpPage::DeckOptions => "deck-options",
        HelpPage::EditingFeatures => "editing#editing-features",
        HelpPage::FullScreenIssue => "platform/windows/display-issues#full-screen",
        HelpPage::CardTypeTemplateError => "templates/errors#template-syntax-error",
        HelpPage::CardTypeDuplicate => "templates/errors#identical-front-sides",
        HelpPage::CardTypeNoFrontField => "templates/errors#no-field-replacement-on-front-side",
        HelpPage::CardTypeMissingCloze => "templates/errors#no-cloze-filter-on-cloze-notetype",
        HelpPage::Troubleshooting => "troubleshooting",
    }
}

impl crate::services::LinksService for Collection {
    fn help_page_link(
        &mut self,
        input: anki_proto::links::HelpPageLinkRequest,
    ) -> error::Result<anki_proto::generic::String> {
        Ok(help_page_to_link(HelpPage::try_from(input.page).unwrap_or(HelpPage::Index)).into())
    }
}
