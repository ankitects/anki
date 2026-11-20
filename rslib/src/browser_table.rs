// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use itertools::Itertools;
use strum::Display;
use strum::EnumIter;
use strum::EnumString;
use strum::IntoEnumIterator;

use crate::card::CardQueue;
use crate::card::CardType;
use crate::card_rendering::prettify_av_tags;
use crate::notetype::CardTemplate;
use crate::notetype::NotetypeKind;
use crate::prelude::*;
use crate::scheduler::timespan::time_span;
use crate::scheduler::timing::SchedTimingToday;
use crate::template::RenderedNode;
use crate::text::html_to_text_line;

#[derive(Debug, PartialEq, Eq, Clone, Copy, Display, EnumIter, EnumString)]
#[strum(serialize_all = "camelCase")]
#[derive(Default)]
pub enum Column {
    #[strum(serialize = "")]
    #[default]
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
    OriginalPosition,
    Question,
    #[strum(serialize = "cardReps")]
    Reps,
    #[strum(serialize = "noteFld")]
    SortField,
    #[strum(serialize = "noteTags")]
    Tags,
    Stability,
    Difficulty,
    Retrievability,
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
    render_context: RenderContext,
    fsrs_retrievability: Option<f32>,
    fsrs_stability: Option<f32>,
}

enum RenderContext {
    // The answer string needs the question string, but not the other way around,
    // so only build the answer string when needed.
    Ok {
        question: String,
        answer_nodes: Vec<RenderedNode>,
    },
    Err(String),
    Unset,
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
        self.ctype != CardType::New && self.original_or_current_due() <= 365_000 // keep consistent with SQL
            || matches!(self.queue, CardQueue::DayLearn | CardQueue::Review)
            || (self.ctype == CardType::Review && self.is_undue_queue())
    }

    /// Returns the card's due date as a timestamp if it has one.
    fn due_time(&self, timing: &SchedTimingToday) -> Option<TimestampSecs> {
        if self.queue == CardQueue::Learn {
            Some(TimestampSecs(self.original_or_current_due() as i64))
        } else if self.is_due_in_days() {
            Some(
                TimestampSecs::now().adding_secs(
                    (self.original_or_current_due() as i64 - timing.days_elapsed as i64)
                        .saturating_mul(86400),
                ),
            )
        } else {
            None
        }
    }

    /// If last_review_date isn't stored in the card, this uses card.due and
    /// card.ivl to infer the elapsed time, which won't be accurate if
    /// 'set due date' or an add-on has changed the due date.
    pub(crate) fn seconds_since_last_review(&self, timing: &SchedTimingToday) -> Option<u32> {
        if let Some(last_review_time) = self.last_review_time {
            Some(timing.now.elapsed_secs_since_clamped(last_review_time))
        } else if self.is_due_in_days() {
            self.due_time(timing).map(|due| {
                let last_review_time =
                    due.adding_secs(-86_400_i64.saturating_mul(self.interval as i64));
                timing.now.elapsed_secs_since_clamped(last_review_time)
            })
        } else {
            let last_review_time = TimestampSecs(self.original_or_current_due() as i64);
            Some(timing.now.elapsed_secs_since_clamped(last_review_time))
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
    pub fn cards_mode_label(self, tr: &I18n) -> String {
        match self {
            Self::Answer => tr.browsing_answer(),
            Self::CardMod => tr.search_card_modified(),
            Self::Cards => tr.card_stats_card_template(),
            Self::Deck => tr.decks_deck(),
            Self::Due => tr.statistics_due_date(),
            Self::Custom => tr.browsing_addon(),
            Self::Ease => tr.browsing_ease(),
            Self::Interval => tr.browsing_interval(),
            Self::Lapses => tr.scheduling_lapses(),
            Self::NoteCreation => tr.browsing_created(),
            Self::NoteMod => tr.search_note_modified(),
            Self::Notetype => tr.card_stats_note_type(),
            Self::OriginalPosition => tr.card_stats_new_card_position(),
            Self::Question => tr.browsing_question(),
            Self::Reps => tr.scheduling_reviews(),
            Self::SortField => tr.browsing_sort_field(),
            Self::Tags => tr.editing_tags(),
            Self::Stability => tr.card_stats_fsrs_stability(),
            Self::Difficulty => tr.card_stats_fsrs_difficulty(),
            Self::Retrievability => tr.card_stats_fsrs_retrievability(),
        }
        .into()
    }

    pub fn notes_mode_label(self, tr: &I18n) -> String {
        match self {
            Self::Cards => tr.editing_cards(),
            Self::Ease => tr.browsing_average_ease(),
            Self::Interval => tr.browsing_average_interval(),
            _ => return self.cards_mode_label(tr),
        }
        .into()
    }

    pub fn cards_mode_tooltip(self, tr: &I18n) -> String {
        match self {
            Self::Answer => tr.browsing_tooltip_answer(),
            Self::CardMod => tr.browsing_tooltip_card_modified(),
            Self::Cards => tr.browsing_tooltip_card(),
            Self::NoteMod => tr.browsing_tooltip_note_modified(),
            Self::Notetype => tr.browsing_tooltip_notetype(),
            Self::Question => tr.browsing_tooltip_question(),
            _ => "".into(),
        }
        .into()
    }

    pub fn notes_mode_tooltip(self, tr: &I18n) -> String {
        match self {
            Self::Cards => tr.browsing_tooltip_cards(),
            _ => return self.cards_mode_label(tr),
        }
        .into()
    }

    pub fn default_cards_order(self) -> anki_proto::search::browser_columns::Sorting {
        self.default_order(false)
    }

    pub fn default_notes_order(self) -> anki_proto::search::browser_columns::Sorting {
        self.default_order(true)
    }

    fn default_order(self, notes: bool) -> anki_proto::search::browser_columns::Sorting {
        use anki_proto::search::browser_columns::Sorting;
        match self {
            Column::Question | Column::Answer | Column::Custom => Sorting::None,
            Column::SortField | Column::Tags | Column::Notetype | Column::Deck => {
                Sorting::Ascending
            }
            Column::CardMod
            | Column::Cards
            | Column::Due
            | Column::Ease
            | Column::Lapses
            | Column::Interval
            | Column::NoteCreation
            | Column::NoteMod
            | Column::OriginalPosition
            | Column::Reps => Sorting::Descending,
            Column::Stability | Column::Difficulty | Column::Retrievability => {
                if notes {
                    Sorting::None
                } else {
                    Sorting::Descending
                }
            }
        }
    }

    pub fn uses_cell_font(self) -> bool {
        matches!(self, Self::Question | Self::Answer | Self::SortField)
    }

    pub fn alignment(self) -> anki_proto::search::browser_columns::Alignment {
        use anki_proto::search::browser_columns::Alignment;
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
    pub fn all_browser_columns(&self) -> anki_proto::search::BrowserColumns {
        let mut columns: Vec<anki_proto::search::browser_columns::Column> = Column::iter()
            .filter(|&c| c != Column::Custom)
            .map(|c| c.to_pb_column(&self.tr))
            .collect();
        columns.sort_by(|c1, c2| c1.cards_mode_label.cmp(&c2.cards_mode_label));
        anki_proto::search::BrowserColumns { columns }
    }

    pub fn browser_row_for_id(&mut self, id: i64) -> Result<anki_proto::search::BrowserRow> {
        let notes_mode = self.get_config_bool(BoolKey::BrowserTableShowNotesMode);
        let columns = Arc::clone(
            self.state
                .active_browser_columns
                .as_ref()
                .or_invalid("Active browser columns not set.")?,
        );
        RowContext::new(self, id, notes_mode, card_render_required(&columns))?.browser_row(&columns)
    }

    fn get_note_maybe_with_fields(&self, id: NoteId, _with_fields: bool) -> Result<Note> {
        // todo: After note.sort_field has been modified so it can be displayed in the
        // browser, we can update note_field_str() and only load the note with
        // fields if a card render is necessary (see #1082).
        if true {
            self.storage.get_note(id)?
        } else {
            self.storage.get_note_without_fields(id)?
        }
        .or_not_found(id)
    }
}

impl RenderContext {
    fn new(col: &mut Collection, card: &Card, note: &Note, notetype: &Notetype) -> Self {
        match notetype
            .get_template(card.template_idx)
            .and_then(|template| col.render_card(note, card, notetype, template, true, true))
        {
            Ok(render) => RenderContext::Ok {
                question: rendered_nodes_to_str(&render.qnodes),
                answer_nodes: render.anodes,
            },
            Err(err) => RenderContext::Err(err.message(&col.tr)),
        }
    }

    fn side_str(&self, is_answer: bool) -> String {
        let back;
        let html = match self {
            Self::Ok {
                question,
                answer_nodes,
            } => {
                if is_answer {
                    back = rendered_nodes_to_str(answer_nodes);
                    back.strip_prefix(question).unwrap_or(&back)
                } else {
                    question
                }
            }
            Self::Err(err) => err,
            Self::Unset => "Invalid input: RenderContext unset",
        };
        html_to_text_line(html, true).into()
    }
}

fn rendered_nodes_to_str(nodes: &[RenderedNode]) -> String {
    let txt = nodes
        .iter()
        .map(|node| match node {
            RenderedNode::Text { text } => text,
            RenderedNode::Replacement { current_text, .. } => current_text,
        })
        .join("");
    prettify_av_tags(txt)
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
            note = col
                .get_note_maybe_with_fields(NoteId(id), with_card_render)
                .map_err(|e| match e {
                    AnkiError::NotFound { .. } => AnkiError::Deleted,
                    _ => e,
                })?;
            cards = col.storage.all_cards_of_note(note.id)?;
            if cards.is_empty() {
                return Err(AnkiError::DatabaseCheckRequired);
            }
        } else {
            cards = vec![col
                .storage
                .get_card(CardId(id))?
                .ok_or(AnkiError::Deleted)?];
            note = col.get_note_maybe_with_fields(cards[0].note_id, with_card_render)?;
        }
        let notetype = col
            .get_notetype(note.notetype_id)?
            .or_not_found(note.notetype_id)?;
        let deck = col
            .get_deck(cards[0].deck_id)?
            .or_not_found(cards[0].deck_id)?;
        let original_deck = if cards[0].original_deck_id.0 != 0 {
            Some(
                col.get_deck(cards[0].original_deck_id)?
                    .or_not_found(cards[0].original_deck_id)?,
            )
        } else {
            None
        };
        let timing = col.timing_today()?;
        let fsrs_retrievability = cards[0]
            .memory_state
            .zip(cards[0].seconds_since_last_review(&timing))
            .map(|(state, seconds)| {
                col.fsrs_current_retrievability_for_card_state(
                    cards[0].id,
                    state,
                    seconds as f32 / 86_400.0,
                )
            })
            .transpose()?;
        let fsrs_stability = cards[0].memory_state.map(|state| state.stability);
        let render_context = if with_card_render {
            RenderContext::new(col, &cards[0], &note, &notetype)
        } else {
            RenderContext::Unset
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
            fsrs_retrievability,
            fsrs_stability,
        })
    }

    fn browser_row(&self, columns: &[Column]) -> Result<anki_proto::search::BrowserRow> {
        Ok(anki_proto::search::BrowserRow {
            cells: columns
                .iter()
                .map(|&column| self.get_cell(column))
                .collect::<Result<_>>()?,
            color: self.get_row_color() as i32,
            font_name: self.get_row_font_name()?,
            font_size: self.get_row_font_size()?,
        })
    }

    fn get_cell(&self, column: Column) -> Result<anki_proto::search::browser_row::Cell> {
        Ok(anki_proto::search::browser_row::Cell {
            text: self.get_cell_text(column)?,
            is_rtl: self.get_is_rtl(column),
            elide_mode: self.get_elide_mode(column) as i32,
        })
    }

    fn get_cell_text(&self, column: Column) -> Result<String> {
        Ok(match column {
            Column::Question => self.render_context.side_str(false),
            Column::Answer => self.render_context.side_str(true),
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
            Column::NoteMod => self.note.mtime.date_and_time_string(),
            Column::OriginalPosition => self.card_original_position(),
            Column::Tags => self.note.tags.join(" "),
            Column::Notetype => self.notetype.name.to_owned(),
            Column::Stability => self.fsrs_stability_str(),
            Column::Difficulty => self.fsrs_difficulty_str(),
            Column::Retrievability => self.fsrs_retrievability_str(),
            Column::Custom => "".to_string(),
        })
    }

    fn card_original_position(&self) -> String {
        let card = &self.cards[0];
        if let Some(pos) = &card.original_position {
            pos.to_string()
        } else if card.ctype == CardType::New {
            card.due.to_string()
        } else {
            String::new()
        }
    }

    fn note_creation_str(&self) -> String {
        TimestampMillis(self.note.id.into())
            .as_secs()
            .date_and_time_string()
    }

    fn note_field_str(&self) -> String {
        let index = self.notetype.config.sort_field_idx as usize;
        html_to_text_line(&self.note.fields()[index], true).into()
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

    fn get_elide_mode(
        &self,
        column: Column,
    ) -> anki_proto::search::browser_row::cell::TextElideMode {
        use anki_proto::search::browser_row::cell::TextElideMode;
        match column {
            Column::Deck => TextElideMode::ElideMiddle,
            _ => TextElideMode::ElideRight,
        }
    }

    fn template(&self) -> Result<&CardTemplate> {
        self.notetype.get_template(self.cards[0].template_idx)
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
            format!("({due})")
        } else {
            due.into()
        }
    }

    fn fsrs_stability_str(&self) -> String {
        self.fsrs_stability
            .map(|stability| time_span(stability * 86400.0, &self.tr, false))
            .unwrap_or_default()
    }

    fn fsrs_difficulty_str(&self) -> String {
        self.cards[0]
            .memory_state
            .as_ref()
            .map(|s| format!("{:.0}%", s.difficulty() * 100.0))
            .unwrap_or_default()
    }

    fn fsrs_retrievability_str(&self) -> String {
        self.fsrs_retrievability
            .map(|r| format!("{:.0}%", r * 100.))
            .unwrap_or_default()
    }

    /// Returns the due date of the next due card that is not in a filtered
    /// deck, new, suspended or buried or the empty string if there is no
    /// such card.
    fn note_due_str(&self) -> String {
        self.cards
            .iter()
            .filter(|c| !(c.is_filtered_deck() || c.is_new_type_or_queue() || c.is_undue_queue()))
            .filter_map(|c| c.due_time(&self.timing))
            .min()
            .map(|time| time.date_string())
            .unwrap_or_else(|| "".into())
    }

    /// Returns the average ease of the non-new cards or a hint if there aren't
    /// any.
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

    /// Returns the average interval of the review and relearn cards if there
    /// are any.
    fn interval_str(&self) -> String {
        if !self.notes_mode {
            match self.cards[0].ctype {
                CardType::New => return self.tr.browsing_new().into(),
                CardType::Learn => return self.tr.browsing_learning().into(),
                CardType::Review | CardType::Relearn => (),
            }
        }
        let intervals: Vec<u64> = self
            .cards
            .iter()
            .filter(|c| matches!(c.ctype, CardType::Review | CardType::Relearn))
            .map(|c| c.interval as u64)
            .collect();
        if intervals.is_empty() {
            "".into()
        } else {
            // u64: the seconds overflow u32 past 49,710 days (spec
            // ui.browser-interval-average)
            time_span(
                (intervals.iter().sum::<u64>() * 86400 / (intervals.len() as u64)) as f32,
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
            .expect("cards missing from RowContext")
            .date_and_time_string()
    }

    fn deck_str(&self) -> String {
        if self.notes_mode {
            let decks = self.cards.iter().map(|c| c.deck_id).unique().count();
            if decks > 1 {
                return format!("({decks})");
            }
        }
        let deck_name = self.deck.human_name();
        if let Some(original_deck) = &self.original_deck {
            format!("{} ({})", deck_name, original_deck.human_name())
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

    fn get_row_font_name(&self) -> Result<String> {
        Ok(self.template()?.config.browser_font_name.to_owned())
    }

    fn get_row_font_size(&self) -> Result<u32> {
        Ok(self.template()?.config.browser_font_size)
    }

    fn get_row_color(&self) -> anki_proto::search::browser_row::Color {
        use anki_proto::search::browser_row::Color;
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
                    } else {
                        match self.cards[0].queue {
                            CardQueue::Suspended => Color::Suspended,
                            CardQueue::UserBuried | CardQueue::SchedBuried => Color::Buried,
                            _ => Color::Default,
                        }
                    }
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use anki_proto::deck_config::deck_configs_for_update::current_deck::Limits;
    use anki_proto::deck_config::UpdateDeckConfigsMode;

    use super::*;
    use crate::card::FsrsMemoryState;
    use crate::deckconfig::FsrsVersion;
    use crate::deckconfig::UpdateDeckConfigsRequest;
    use crate::scheduler::fsrs::memory_state::fsrs_current_retrievability_for_state;
    use crate::search::SortMode;

    fn fsrs7_params_for_retrievability_test() -> Vec<f32> {
        vec![
            0.4843, 3.0562, 10.9946, 32.7202, 5.6296, 0.5900, 3.1230, 2.4679, 0.2733, 1.4895,
            0.4868, 0.0010, 0.8082, 0.1723, 0.6389, 1.5767, 0.8918, 0.3341, 3.5942, 0.3455, 0.0022,
            0.2834, 2.6418, 0.5604, 1.3042, 2.5054, 0.9376, 0.0611, 0.0830, 0.6339, 0.9846, 0.2485,
            0.6014, 0.0545,
        ]
    }

    fn set_selected_fsrs7_params(col: &mut Collection, params: Vec<f32>) -> Result<()> {
        let output = col.get_deck_configs_for_update(DeckId(1))?;
        let mut input = UpdateDeckConfigsRequest {
            target_deck_id: DeckId(1),
            configs: output
                .all_config
                .into_iter()
                .map(|c| c.config.unwrap().into())
                .collect(),
            removed_config_ids: vec![],
            mode: UpdateDeckConfigsMode::Normal,
            card_state_customizer: String::new(),
            limits: Limits::default(),
            new_cards_ignore_review_limit: false,
            apply_all_parent_limits: false,
            fsrs: true,
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
        };
        input.configs[0].inner.fsrs_version = FsrsVersion::Seven as i32;
        input.configs[0].inner.fsrs_params_7 = params;
        col.update_deck_configs(input)?;
        Ok(())
    }

    #[test]
    fn retrievability_cell_uses_selected_model_curve() -> Result<()> {
        let mut col = Collection::new();
        let params = fsrs7_params_for_retrievability_test();
        set_selected_fsrs7_params(&mut col, params.clone())?;

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let cid = col.search_cards("", SortMode::NoOrder)?[0];

        let mut card = col.storage.get_card(cid)?.unwrap();
        let stability = 42.0;
        let elapsed_days = 120.0;
        let timing = col.timing_today()?;
        let state = FsrsMemoryState {
            stability,
            stability_internal: stability,
            stability_fast: Some(17.0),
            difficulty: 8.0,
        };
        card.memory_state = Some(state);
        card.last_review_time = Some(timing.now.adding_secs(-(elapsed_days as i64) * 86_400));
        card.decay = Some(params[23]);
        col.storage.update_card(&card)?;

        let ctx = RowContext::new(&mut col, cid.0, false, false)?;
        let actual = ctx.get_cell_text(Column::Retrievability)?;
        let expected = fsrs_current_retrievability_for_state(&params, state, elapsed_days)?;
        assert_eq!(actual, format!("{:.0}%", expected * 100.0));
        Ok(())
    }

    // Pins spec/ui.md#ui.browser-interval-average: the average does not
    // overflow when the intervals add up to more than 49,710 days.
    #[test]
    fn interval_cell_averages_long_intervals_without_overflow() -> Result<()> {
        let mut col = Collection::new();
        let nt = col
            .get_notetype_by_name("Basic (and reversed card)")?
            .unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "front")?;
        note.set_field(1, "back")?;
        col.add_note(&mut note, DeckId(1))?;
        for (cid, interval) in col
            .search_cards("", SortMode::NoOrder)?
            .into_iter()
            .zip([30_000, 32_000])
        {
            let mut card = col.storage.get_card(cid)?.unwrap();
            card.ctype = CardType::Review;
            card.queue = CardQueue::Review;
            card.interval = interval;
            col.storage.update_card(&card)?;
        }

        let ctx = RowContext::new(&mut col, note.id.0, true, false)?;
        let expected = time_span(31_000.0 * 86_400.0, &ctx.tr, false);
        assert_eq!(ctx.get_cell_text(Column::Interval)?, expected);
        Ok(())
    }

    #[test]
    fn stability_cell_uses_s90_for_fsrs7() -> Result<()> {
        let mut col = Collection::new();
        let params = fsrs7_params_for_retrievability_test();
        set_selected_fsrs7_params(&mut col, params)?;

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let cid = col.search_cards("", SortMode::NoOrder)?[0];

        let mut card = col.storage.get_card(cid)?.unwrap();
        let stability = 42.0;
        let s90 = col.fsrs_interval_at_retrievability_for_card(cid, stability, 0.9)?;
        card.memory_state = Some(FsrsMemoryState {
            stability: s90,
            stability_internal: stability,
            stability_fast: None,
            difficulty: 5.0,
        });
        col.storage.update_card(&card)?;

        let ctx = RowContext::new(&mut col, cid.0, false, false)?;
        let actual = ctx.get_cell_text(Column::Stability)?;
        assert!(
            (s90 - stability).abs() > 0.001,
            "test requires fsrs7 s90 to differ from raw stability"
        );
        let expected = time_span(s90 * 86400.0, &ctx.tr, false);
        assert_eq!(actual, expected);
        Ok(())
    }
}
