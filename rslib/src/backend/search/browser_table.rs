// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::str::FromStr;

use crate::browser_table;
use crate::i18n::I18n;
use crate::pb;

impl browser_table::Column {
    pub fn to_pb_column(self, i18n: &I18n) -> pb::search::browser_columns::Column {
        pb::search::browser_columns::Column {
            key: self.to_string(),
            cards_mode_label: self.cards_mode_label(i18n),
            notes_mode_label: self.notes_mode_label(i18n),
            sorting: self.default_order() as i32,
            uses_cell_font: self.uses_cell_font(),
            alignment: self.alignment() as i32,
            cards_mode_tooltip: self.cards_mode_tooltip(i18n),
            notes_mode_tooltip: self.notes_mode_tooltip(i18n),
        }
    }
}

impl From<pb::generic::StringList> for Vec<browser_table::Column> {
    fn from(input: pb::generic::StringList) -> Self {
        input
            .vals
            .iter()
            .map(|c| browser_table::Column::from_str(c).unwrap_or_default())
            .collect()
    }
}
