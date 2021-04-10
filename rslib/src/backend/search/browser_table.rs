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

impl From<browser_table::Row> for pb::BrowserRow {
    fn from(row: browser_table::Row) -> Self {
        pb::BrowserRow {
            cells: row.cells.into_iter().map(Into::into).collect(),
            color: row.color.into(),
            font_name: row.font.name,
            font_size: row.font.size,
        }
    }
}

impl From<browser_table::Cell> for pb::browser_row::Cell {
    fn from(cell: browser_table::Cell) -> Self {
        pb::browser_row::Cell {
            text: cell.text,
            is_rtl: cell.is_rtl,
        }
    }
}

impl From<browser_table::Color> for i32 {
    fn from(color: browser_table::Color) -> Self {
        match color {
            browser_table::Color::Default => pb::browser_row::Color::Default as i32,
            browser_table::Color::Marked => pb::browser_row::Color::Marked as i32,
            browser_table::Color::Suspended => pb::browser_row::Color::Suspended as i32,
            browser_table::Color::FlagRed => pb::browser_row::Color::FlagRed as i32,
            browser_table::Color::FlagOrange => pb::browser_row::Color::FlagOrange as i32,
            browser_table::Color::FlagGreen => pb::browser_row::Color::FlagGreen as i32,
            browser_table::Color::FlagBlue => pb::browser_row::Color::FlagBlue as i32,
        }
    }
}
