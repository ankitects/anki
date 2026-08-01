// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::str::FromStr;

use anki_i18n::I18n;

use crate::browser_table;

impl browser_table::Column {
    pub fn to_pb_column(self, i18n: &I18n) -> anki_proto::search::browser_columns::Column {
        anki_proto::search::browser_columns::Column {
            key: self.to_string(),
            cards_mode_label: self.cards_mode_label(i18n),
            notes_mode_label: self.notes_mode_label(i18n),
            sorting_cards: self.default_cards_order() as i32,
            sorting_notes: self.default_notes_order() as i32,
            uses_cell_font: self.uses_cell_font(),
            alignment: self.alignment() as i32,
            cards_mode_tooltip: self.cards_mode_tooltip(i18n),
            notes_mode_tooltip: self.notes_mode_tooltip(i18n),
        }
    }
}

pub(crate) fn string_list_to_browser_columns(
    list: anki_proto::generic::StringList,
) -> Vec<browser_table::Column> {
    list.vals
        .into_iter()
        .map(|c| browser_table::Column::from_str(&c).unwrap_or_default())
        .collect()
}
