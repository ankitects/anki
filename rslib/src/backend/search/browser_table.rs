// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::str::FromStr;

use crate::{backend_proto as pb, browser_table, i18n::I18n};

impl browser_table::Column {
    pub fn to_pb_column(self, i18n: &I18n) -> pb::browser_columns::Column {
        pb::browser_columns::Column {
            key: self.to_string(),
            cards_mode_label: self.cards_mode_label(i18n),
            notes_mode_label: self.notes_mode_label(i18n),
            sorting: self.sorting() as i32,
            uses_cell_font: self.uses_cell_font(),
            alignment: self.alignment() as i32,
        }
    }
}

impl From<pb::StringList> for Vec<browser_table::Column> {
    fn from(input: pb::StringList) -> Self {
        input
            .vals
            .iter()
            .map(|c| browser_table::Column::from_str(c).unwrap_or_default())
            .collect()
    }
}
