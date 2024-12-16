// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use anki_proto::links::help_page_link_request::HelpPage;

use crate::collection::Collection;
use crate::error;

static HELP_SITE: &str = "https://docs.ankiweb.net/";

pub fn help_page_to_link(page: HelpPage) -> String {
    format!("{}{}", HELP_SITE, help_page_link_suffix(page))
}

pub fn help_page_link_suffix(page: HelpPage) -> &'static str {
    match page {
        HelpPage::NoteType => "getting-started.html#note-types",
        HelpPage::Browsing => "browsing.html",
        HelpPage::BrowsingFindAndReplace => "browsing.html#find-and-replace",
        HelpPage::BrowsingNotesMenu => "browsing.html#notes",
        HelpPage::KeyboardShortcuts => "studying.html#keyboard-shortcuts",
        HelpPage::Editing => "editing.html",
        HelpPage::AddingCardAndNote => "editing.html#adding-cards-and-notes",
        HelpPage::AddingANoteType => "editing.html#adding-a-note-type",
        HelpPage::Latex => "math.html#latex",
        HelpPage::Preferences => "preferences.html",
        HelpPage::Index => "",
        HelpPage::Templates => "templates/intro.html",
        HelpPage::FilteredDeck => "filtered-decks.html",
        HelpPage::Importing => "importing/intro.html",
        HelpPage::CustomizingFields => "editing.html#customizing-fields",
        HelpPage::DeckOptions => "deck-options.html",
        HelpPage::EditingFeatures => "editing.html#editing-features",
        HelpPage::FullScreenIssue => "platform/windows/display-issues.html#full-screen",
        HelpPage::CardTypeTemplateError => "templates/errors.html#template-syntax-error",
        HelpPage::CardTypeDuplicate => "templates/errors.html#identical-front-sides",
        HelpPage::CardTypeNoFrontField => {
            "templates/errors.html#no-field-replacement-on-front-side"
        }
        HelpPage::CardTypeMissingCloze => "templates/errors.html#no-cloze-filter-on-cloze-notetype",
        HelpPage::Troubleshooting => "troubleshooting.html",
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
