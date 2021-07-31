// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use crate::backend_proto::links::help_page_link_request::HelpPage;

static HELP_SITE: &str = "https://docs.ankiweb.net/";

impl HelpPage {
    pub fn to_link(self) -> String {
        format!("{}{}", HELP_SITE, self.to_link_suffix())
    }

    pub fn to_link_suffix(self) -> &'static str {
        match self {
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
            HelpPage::Importing => "importing.html",
            HelpPage::CustomizingFields => "editing.html#customizing-fields",
            HelpPage::DeckOptions => "deck-options.html",
            HelpPage::EditingFeatures => "editing.html#features",
        }
    }
}
