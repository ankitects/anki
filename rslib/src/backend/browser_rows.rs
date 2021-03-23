// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::{backend_proto as pb, browser_rows, prelude::*};
pub(super) use pb::browserrows_service::Service as BrowserRowsService;

impl BrowserRowsService for Backend {
    fn browser_row_for_card(&self, input: pb::CardId) -> Result<pb::BrowserRow> {
        self.with_col(|col| col.browser_row_for_card(input.cid.into()).map(Into::into))
    }
}

impl From<browser_rows::Row> for pb::BrowserRow {
    fn from(row: browser_rows::Row) -> Self {
        pb::BrowserRow {
            cells: row.cells.into_iter().map(Into::into).collect(),
            color: row.color.into(),
            font_name: row.font.name,
            font_size: row.font.size,
        }
    }
}

impl From<browser_rows::Cell> for pb::browser_row::Cell {
    fn from(cell: browser_rows::Cell) -> Self {
        pb::browser_row::Cell {
            text: cell.text,
            is_rtl: cell.is_rtl,
        }
    }
}

impl From<browser_rows::Color> for i32 {
    fn from(color: browser_rows::Color) -> Self {
        match color {
            browser_rows::Color::Default => pb::browser_row::Color::Default as i32,
            browser_rows::Color::Marked => pb::browser_row::Color::Marked as i32,
            browser_rows::Color::Suspended => pb::browser_row::Color::Suspended as i32,
            browser_rows::Color::FlagRed => pb::browser_row::Color::FlagRed as i32,
            browser_rows::Color::FlagOrange => pb::browser_row::Color::FlagOrange as i32,
            browser_rows::Color::FlagGreen => pb::browser_row::Color::FlagGreen as i32,
            browser_rows::Color::FlagBlue => pb::browser_row::Color::FlagBlue as i32,
        }
    }
}
