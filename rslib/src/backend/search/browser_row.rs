// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, browser_rows};

impl From<pb::StringList> for Vec<browser_rows::Column> {
    fn from(input: pb::StringList) -> Self {
        input.vals.into_iter().map(Into::into).collect()
    }
}

impl From<String> for browser_rows::Column {
    fn from(text: String) -> Self {
        match text.as_str() {
            "question" => browser_rows::Column::Question,
            "answer" => browser_rows::Column::Answer,
            "deck" => browser_rows::Column::CardDeck,
            "cardDue" => browser_rows::Column::CardDue,
            "cardEase" => browser_rows::Column::CardEase,
            "cardLapses" => browser_rows::Column::CardLapses,
            "cardIvl" => browser_rows::Column::CardInterval,
            "cardMod" => browser_rows::Column::CardMod,
            "cardReps" => browser_rows::Column::CardReps,
            "template" => browser_rows::Column::CardTemplate,
            "noteCards" => browser_rows::Column::NoteCards,
            "noteCrt" => browser_rows::Column::NoteCreation,
            "noteEase" => browser_rows::Column::NoteEase,
            "noteFld" => browser_rows::Column::NoteField,
            "noteLapses" => browser_rows::Column::NoteLapses,
            "noteMod" => browser_rows::Column::NoteMod,
            "noteReps" => browser_rows::Column::NoteReps,
            "noteTags" => browser_rows::Column::NoteTags,
            "note" => browser_rows::Column::Notetype,
            _ => browser_rows::Column::Custom,
        }
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
