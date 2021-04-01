// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_i18n::I18n;

use super::AnkiError;

#[derive(Debug, PartialEq)]
pub enum FilteredDeckError {
    MustBeLeafNode,
    CanNotMoveCardsInto,
    SearchReturnedNoCards,
    FilteredDeckRequired,
}

impl FilteredDeckError {
    pub fn localized_description(&self, tr: &I18n) -> String {
        match self {
            FilteredDeckError::MustBeLeafNode => tr.errors_filtered_parent_deck(),
            FilteredDeckError::CanNotMoveCardsInto => {
                tr.browsing_cards_cant_be_manually_moved_into()
            }
            FilteredDeckError::SearchReturnedNoCards => tr.decks_filtered_deck_search_empty(),
            FilteredDeckError::FilteredDeckRequired => tr.errors_filtered_deck_required(),
        }
        .into()
    }
}

impl From<FilteredDeckError> for AnkiError {
    fn from(e: FilteredDeckError) -> Self {
        AnkiError::FilteredDeckError(e)
    }
}
