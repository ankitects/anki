// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, browser_table};

impl From<pb::StringList> for Vec<browser_table::Column> {
    fn from(input: pb::StringList) -> Self {
        input.vals.into_iter().map(Into::into).collect()
    }
}

impl From<String> for browser_table::Column {
    fn from(text: String) -> Self {
        match text.as_str() {
            "question" => browser_table::Column::Question,
            "answer" => browser_table::Column::Answer,
            "deck" => browser_table::Column::CardDeck,
            "cardDue" => browser_table::Column::CardDue,
            "cardEase" => browser_table::Column::CardEase,
            "cardLapses" => browser_table::Column::CardLapses,
            "cardIvl" => browser_table::Column::CardInterval,
            "cardMod" => browser_table::Column::CardMod,
            "cardReps" => browser_table::Column::CardReps,
            "template" => browser_table::Column::CardTemplate,
            "noteCards" => browser_table::Column::NoteCards,
            "noteCrt" => browser_table::Column::NoteCreation,
            "noteDue" => browser_table::Column::NoteDue,
            "noteEase" => browser_table::Column::NoteEase,
            "noteFld" => browser_table::Column::NoteField,
            "noteLapses" => browser_table::Column::NoteLapses,
            "noteMod" => browser_table::Column::NoteMod,
            "noteReps" => browser_table::Column::NoteReps,
            "noteTags" => browser_table::Column::NoteTags,
            "note" => browser_table::Column::Notetype,
            _ => browser_table::Column::Custom,
        }
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
