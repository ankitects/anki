// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use itertools::Itertools;
use strum::{Display, EnumIter, EnumString, IntoEnumIterator};

use crate::{
    backend_proto as pb,
    card::{CardQueue, CardType},
    notetype::{CardTemplate, NotetypeKind},
    prelude::*,
    scheduler::{timespan::time_span, timing::SchedTimingToday},
    template::RenderedNode,
    text::{extract_av_tags, html_to_text_line},
};

#[derive(Debug, PartialEq, Clone, Copy, Display, EnumIter, EnumString)]
#[strum(serialize_all = "camelCase")]
pub enum Column {
    #[strum(serialize = "")]
    Custom,
    Answer,
    CardMod,
    #[strum(serialize = "template")]
    Cards,
    Deck,
    #[strum(serialize = "cardDue")]
    Due,
    #[strum(serialize = "cardEase")]
    Ease,
    #[strum(serialize = "cardLapses")]
    Lapses,
    #[strum(serialize = "cardIvl")]
    Interval,
    #[strum(serialize = "noteCrt")]
    NoteCreation,
    NoteMod,
    #[strum(serialize = "note")]
    Notetype,
    Question,
    #[strum(serialize = "cardReps")]
    Reps,
    #[strum(serialize = "noteFld")]
    SortField,
    #[strum(serialize = "noteTags")]
    Tags,
}

impl Default for Column {
    fn default() -> Self {
        Column::Custom
    }
}

struct RowContext {
    notes_mode: bool,
    cards: Vec<Card>,
    note: Note,
    notetype: Arc<Notetype>,
    deck: Arc<Deck>,
    original_deck: Option<Arc<Deck>>,
    tr: I18n,
    timing: SchedTimingToday,
    render_context: Option<RenderContext>,
}

/// The answer string needs the question string but not the other way around, so only build the
/// answer string when needed.
struct RenderContext {
    question: String,
    answer_nodes: Vec<RenderedNode>,
}

fn card_render_required(columns: &[Column]) -> bool {
    columns
        .iter()
        .any(|c| matches!(c, Column::Question | Column::Answer))
}

impl Card {
    fn is_new_type_or_queue(&self) -> bool {
        self.queue == CardQueue::New || self.ctype == CardType::New
    }

    fn is_filtered_deck(&self) -> bool {
        self.original_deck_id != DeckId(0)
    }

    /// Returns true if the card can not be due as it's buried or suspended.
    fn is_undue_queue(&self) -> bool {
        (self.queue as i8) < 0
    }

    /// Returns true if the card has a due date in terms of days.
    fn is_due_in_days(&self) -> bool {
        matches!(self.queue, CardQueue::DayLearn | CardQueue::Review)
            || (self.ctype == CardType::Review && self.is_undue_queue())
    }

    /// Returns the card's due date as a timestamp if it has one.
    fn due_time(&self, timing: &SchedTimingToday) -> Option<TimestampSecs> {
        if self.queue == CardQueue::Learn {
            Some(TimestampSecs(self.due as i64))
        } else if self.is_due_in_days() {
            Some(TimestampSecs::now().adding_secs(
                ((self.due - timing.days_elapsed as i32).saturating_mul(86400)) as i64,
            ))
        } else {
            None
        }
    }
}

impl Note {
    fn is_marked(&self) -> bool {
        self.tags
            .iter()
            .any(|tag| tag.eq_ignore_ascii_case("marked"))
    }
}

impl Column {
    pub fn cards_mode_label(self, i18n: &I18n) -> String {
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

    pub fn notes_mode_label(self, i18n: &I18n) -> String {
        match self {
            Self::CardMod => i18n.search_card_modified(),
            Self::Cards => i18n.editing_cards(),
            Self::Ease => i18n.browsing_average_ease(),
            Self::Interval => i18n.browsing_average_interval(),
            Self::Reps => i18n.scheduling_reviews(),
            _ => return self.cards_mode_label(i18n),
        }
        .into()
    }

    pub fn sorting(self) -> pb::browser_columns::Sorting {
        use pb::browser_columns::Sorting;
        match self {
            Self::Question | Self::Answer | Self::Custom => Sorting::None,
            Self::SortField => Sorting::Reversed,
            _ => Sorting::Normal,
        }
    }

    pub fn uses_cell_font(self) -> bool {
        matches!(self, Self::Question | Self::Answer | Self::SortField)
    }

    pub fn alignment(self) -> pb::browser_columns::Alignment {
        use pb::browser_columns::Alignment;
        match self {
            Self::Question
            | Self::Answer
            | Self::Cards
            | Self::Deck
            | Self::SortField
            | Self::Notetype
            | Self::Tags => Alignment::Start,
            _ => Alignment::Center,
        }
    }
}

impl Collection {
    pub fn all_browser_columns(&self) -> pb::BrowserColumns {
        let mut columns: Vec<pb::browser_columns::Column> = Column::iter()
            .filter(|&c| c != Column::Custom)
            .map(|c| c.to_pb_column(&self.tr))
            .collect();
        columns.sort_by(|c1, c2| c1.cards_mode_label.cmp(&c2.cards_mode_label));
        pb::BrowserColumns { columns }
    }

    pub fn browser_row_for_id(&mut self, id: i64) -> Result<pb::BrowserRow> {
        let notes_mode = self.get_config_bool(BoolKey::BrowserTableShowNotesMode);
        let columns = Arc::clone(
            self.state
                .active_browser_columns
                .as_ref()
                .ok_or_else(|| AnkiError::invalid_input("Active browser columns not set."))?,
        );
        RowContext::new(self, id, notes_mode, card_render_required(&columns))?.browser_row(&columns)
    }

    fn get_note_maybe_with_fields(&self, id: NoteId, _with_fields: bool) -> Result<Note> {
        // todo: After note.sort_field has been modified so it can be displayed in the browser,
        // we can update note_field_str() and only load the note with fields if a card render is
        // necessary (see #1082).
        if true {
            self.storage.get_note(id)?
        } else {
            self.storage.get_note_without_fields(id)?
        }
        .ok_or(AnkiError::NotFound)
    }
}

impl RenderContext {
    fn new(col: &mut Collection, card: &Card, note: &Note, notetype: &Notetype) -> Result<Self> {
        let render = col.render_card(
            note,
            card,
            notetype,
            notetype.get_template(card.template_idx)?,
            true,
        )?;
        let qnodes_text = render
            .qnodes
            .iter()
            .map(|node| match node {
                RenderedNode::Text { text } => text,
                RenderedNode::Replacement {
                    field_name: _,
                    current_text,
                    filters: _,
                } => current_text,
            })
            .join("");
        let question = extract_av_tags(&qnodes_text, true).0.to_string();

        Ok(RenderContext {
            question,
            answer_nodes: render.anodes,
        })
    }
}

impl RowContext {
    fn new(
        col: &mut Collection,
        id: i64,
        notes_mode: bool,
        with_card_render: bool,
    ) -> Result<Self> {
        let cards;
        let note;
        if notes_mode {
            note = col.get_note_maybe_with_fields(NoteId(id), with_card_render)?;
            cards = col.storage.all_cards_of_note(note.id)?;
            if cards.is_empty() {
                return Err(AnkiError::DatabaseCheckRequired);
            }
        } else {
            cards = vec![col
                .storage
                .get_card(CardId(id))?
                .ok_or(AnkiError::NotFound)?];
            note = col.get_note_maybe_with_fields(cards[0].note_id, with_card_render)?;
        }
        let notetype = col
            .get_notetype(note.notetype_id)?
            .ok_or(AnkiError::NotFound)?;
        let deck = col.get_deck(cards[0].deck_id)?.ok_or(AnkiError::NotFound)?;
        let original_deck = if cards[0].original_deck_id.0 != 0 {
            Some(
                col.get_deck(cards[0].original_deck_id)?
                    .ok_or(AnkiError::NotFound)?,
            )
        } else {
            None
        };
        let timing = col.timing_today()?;
        let render_context = if with_card_render {
            Some(RenderContext::new(col, &cards[0], &note, &notetype)?)
        } else {
            None
        };

        Ok(RowContext {
            notes_mode,
            cards,
            note,
            notetype,
            deck,
            original_deck,
            tr: col.tr.clone(),
            timing,
            render_context,
        })
    }

    fn browser_row(&self, columns: &[Column]) -> Result<pb::BrowserRow> {
        Ok(pb::BrowserRow {
            cells: columns
                .iter()
                .map(|&column| self.get_cell(column))
                .collect::<Result<_>>()?,
            color: self.get_row_color() as i32,
            font_name: self.get_row_font_name()?,
            font_size: self.get_row_font_size()?,
        })
    }

    fn get_cell(&self, column: Column) -> Result<pb::browser_row::Cell> {
        Ok(pb::browser_row::Cell {
            text: self.get_cell_text(column)?,
            is_rtl: self.get_is_rtl(column),
        })
    }

    fn get_cell_text(&self, column: Column) -> Result<String> {
        Ok(match column {
            Column::Question => self.question_str(),
            Column::Answer => self.answer_str(),
            Column::Deck => self.deck_str(),
            Column::Due => self.due_str(),
            Column::Ease => self.ease_str(),
            Column::Interval => self.interval_str(),
            Column::Lapses => self.cards.iter().map(|c| c.lapses).sum::<u32>().to_string(),
            Column::CardMod => self.card_mod_str(),
            Column::Reps => self.cards.iter().map(|c| c.reps).sum::<u32>().to_string(),
            Column::Cards => self.cards_str()?,
            Column::NoteCreation => self.note_creation_str(),
            Column::SortField => self.note_field_str(),
            Column::NoteMod => self.note.mtime.date_string(),
            Column::Tags => self.note.tags.join(" "),
            Column::Notetype => self.notetype.name.to_owned(),
            Column::Custom => "".to_string(),
        })
    }

    fn note_creation_str(&self) -> String {
        TimestampMillis(self.note.id.into()).as_secs().date_string()
    }

    fn note_field_str(&self) -> String {
        let index = self.notetype.config.sort_field_idx as usize;
        html_to_text_line(&self.note.fields()[index]).into()
    }

    fn get_is_rtl(&self, column: Column) -> bool {
        match column {
            Column::SortField => {
                let index = self.notetype.config.sort_field_idx as usize;
                self.notetype.fields[index].config.rtl
            }
            _ => false,
        }
    }

    fn template(&self) -> Result<&CardTemplate> {
        self.notetype.get_template(self.cards[0].template_idx)
    }

    fn answer_str(&self) -> String {
        let render_context = self.render_context.as_ref().unwrap();
        let answer = render_context
            .answer_nodes
            .iter()
            .map(|node| match node {
                RenderedNode::Text { text } => text,
                RenderedNode::Replacement {
                    field_name: _,
                    current_text,
                    filters: _,
                } => current_text,
            })
            .join("");
        let answer = extract_av_tags(&answer, false).0;
        html_to_text_line(
            if let Some(stripped) = answer.strip_prefix(&render_context.question) {
                stripped
            } else {
                &answer
            },
        )
        .to_string()
    }

    fn due_str(&self) -> String {
        if self.notes_mode {
            self.note_due_str()
        } else {
            self.card_due_str()
        }
    }

    fn card_due_str(&self) -> String {
        let due = if self.cards[0].is_filtered_deck() {
            self.tr.browsing_filtered()
        } else if self.cards[0].is_new_type_or_queue() {
            self.tr.statistics_due_for_new_card(self.cards[0].due)
        } else if let Some(time) = self.cards[0].due_time(&self.timing) {
            time.date_string().into()
        } else {
            return "".into();
        };
        if self.cards[0].is_undue_queue() {
            format!("({})", due)
        } else {
            due.into()
        }
    }

    /// Returns the due date of the next due card that is not in a filtered deck, new, suspended or
    /// buried or the empty string if there is no such card.
    fn note_due_str(&self) -> String {
        self.cards
            .iter()
            .filter(|c| !(c.is_filtered_deck() || c.is_new_type_or_queue() || c.is_undue_queue()))
            .filter_map(|c| c.due_time(&self.timing))
            .min()
            .map(|time| time.date_string())
            .unwrap_or_else(|| "".into())
    }

    /// Returns the average ease of the non-new cards or a hint if there aren't any.
    fn ease_str(&self) -> String {
        let eases: Vec<u16> = self
            .cards
            .iter()
            .filter(|c| c.ctype != CardType::New)
            .map(|c| c.ease_factor)
            .collect();
        if eases.is_empty() {
            self.tr.browsing_new().into()
        } else {
            format!("{}%", eases.iter().sum::<u16>() / eases.len() as u16 / 10)
        }
    }

    /// Returns the average interval of the review and relearn cards if there are any.
    fn interval_str(&self) -> String {
        if !self.notes_mode {
            match self.cards[0].ctype {
                CardType::New => return self.tr.browsing_new().into(),
                CardType::Learn => return self.tr.browsing_learning().into(),
                CardType::Review | CardType::Relearn => (),
            }
        }
        let intervals: Vec<u32> = self
            .cards
            .iter()
            .filter(|c| matches!(c.ctype, CardType::Review | CardType::Relearn))
            .map(|c| c.interval)
            .collect();
        if intervals.is_empty() {
            "".into()
        } else {
            time_span(
                (intervals.iter().sum::<u32>() * 86400 / (intervals.len() as u32)) as f32,
                &self.tr,
                false,
            )
        }
    }

    fn card_mod_str(&self) -> String {
        self.cards
            .iter()
            .map(|c| c.mtime)
            .max()
            .unwrap()
            .date_string()
    }

    fn deck_str(&self) -> String {
        if self.notes_mode {
            let decks = self.cards.iter().map(|c| c.deck_id).unique().count();
            if decks > 1 {
                return format!("({})", decks);
            }
        }
        let deck_name = self.deck.human_name();
        if let Some(original_deck) = &self.original_deck {
            format!("{} ({})", &deck_name, &original_deck.human_name())
        } else {
            deck_name
        }
    }

    fn cards_str(&self) -> Result<String> {
        Ok(if self.notes_mode {
            self.cards.len().to_string()
        } else {
            let name = &self.template()?.name;
            match self.notetype.config.kind() {
                NotetypeKind::Normal => name.to_owned(),
                NotetypeKind::Cloze => format!("{} {}", name, self.cards[0].template_idx + 1),
            }
        })
    }

    fn question_str(&self) -> String {
        html_to_text_line(&self.render_context.as_ref().unwrap().question).to_string()
    }

    fn get_row_font_name(&self) -> Result<String> {
        Ok(self.template()?.config.browser_font_name.to_owned())
    }

    fn get_row_font_size(&self) -> Result<u32> {
        Ok(self.template()?.config.browser_font_size)
    }

    fn get_row_color(&self) -> pb::browser_row::Color {
        use pb::browser_row::Color;
        if self.notes_mode {
            if self.note.is_marked() {
                Color::Marked
            } else {
                Color::Default
            }
        } else {
            match self.cards[0].flags {
                1 => Color::FlagRed,
                2 => Color::FlagOrange,
                3 => Color::FlagGreen,
                4 => Color::FlagBlue,
                5 => Color::FlagPink,
                6 => Color::FlagTurquoise,
                7 => Color::FlagPurple,
                _ => {
                    if self.note.is_marked() {
                        Color::Marked
                    } else if self.cards[0].queue == CardQueue::Suspended {
                        Color::Suspended
                    } else {
                        Color::Default
                    }
                }
            }
        }
    }
}
