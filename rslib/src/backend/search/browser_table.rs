// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::str::FromStr;

use strum::IntoEnumIterator;

use crate::{backend_proto as pb, browser_table, collection::Collection, i18n::I18n};

impl Collection {
    pub(crate) fn all_browser_columns(&self) -> pb::BrowserColumns {
        let mut columns: Vec<pb::browser_columns::Column> = browser_table::Column::iter()
            .filter(|&c| c != browser_table::Column::Custom)
            .map(|c| c.to_pb_column(&self.tr))
            .collect();
        columns.sort_by(|c1, c2| c1.label.cmp(&c2.label));
        pb::BrowserColumns { columns }
    }
}

impl browser_table::Column {
    fn to_pb_column(self, i18n: &I18n) -> pb::browser_columns::Column {
        pb::browser_columns::Column {
            key: self.to_string(),
            label: self.localized_label(i18n),
            notes_label: self.localized_notes_label(i18n),
            sorting: self.sorting() as i32,
            uses_cell_font: self.uses_cell_font(),
            alignment: self.alignment() as i32,
        }
    }

    fn sorting(self) -> pb::browser_columns::Sorting {
        match self {
            Self::Question | Self::Answer | Self::Custom => pb::browser_columns::Sorting::None,
            Self::SortField => pb::browser_columns::Sorting::Reversed,
            _ => pb::browser_columns::Sorting::Normal,
        }
    }

    fn uses_cell_font(self) -> bool {
        matches!(self, Self::Question | Self::Answer | Self::SortField)
    }

    fn alignment(self) -> pb::browser_columns::Alignment {
        match self {
            Self::Question
            | Self::Answer
            | Self::Cards
            | Self::Deck
            | Self::SortField
            | Self::Notetype
            | Self::Tags => pb::browser_columns::Alignment::Start,
            _ => pb::browser_columns::Alignment::Center,
        }
    }

    fn localized_label(self, i18n: &I18n) -> String {
        match self {
            Self::Answer => i18n.browsing_answer(),
            Self::CardMod => i18n.search_card_modified(),
            Self::Cards => i18n.browsing_card(),
            Self::Deck => i18n.decks_deck(),
            Self::Due => i18n.statistics_due_date(),
            Self::Custom => i18n.browsing_addon(),
            Self::Ease => i18n.browsing_ease(),
            Self::Interval => i18n.browsing_interval(),
            Self::Lapses => i18n.scheduling_lapses(),
            Self::NoteCreation => i18n.browsing_created(),
            Self::NoteMod => i18n.search_note_modified(),
            Self::Notetype => i18n.browsing_note(),
            Self::Question => i18n.browsing_question(),
            Self::Reps => i18n.scheduling_reviews(),
            Self::SortField => i18n.browsing_sort_field(),
            Self::Tags => i18n.editing_tags(),
        }
        .into()
    }

    fn localized_notes_label(self, i18n: &I18n) -> String {
        match self {
            Self::CardMod => i18n.search_card_modified(),
            Self::Cards => i18n.editing_cards(),
            Self::Ease => i18n.browsing_average_ease(),
            Self::Interval => i18n.browsing_average_interval(),
            Self::Reps => i18n.scheduling_reviews(),
            _ => return self.localized_label(i18n),
        }
        .into()
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
