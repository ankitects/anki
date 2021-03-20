// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use chrono::prelude::*;
use itertools::Itertools;

use crate::err::{AnkiError, Result};
use crate::i18n::{tr_args, I18n, TR};
use crate::{
    card::{Card, CardID, CardQueue, CardType},
    collection::Collection,
    decks::Deck,
    notes::Note,
    notetype::{CardTemplate, NoteType, NoteTypeKind},
    scheduler::{timespan::time_span, timing::SchedTimingToday},
    template::RenderedNode,
    text::{extract_av_tags, html_to_text_line},
    timestamp::{TimestampMillis, TimestampSecs},
};

#[derive(Debug, PartialEq)]
pub struct Row {
    pub cells: Vec<Cell>,
    pub color: RowColor,
    pub font: Font,
}

#[derive(Debug, PartialEq)]
pub struct Cell {
    pub text: String,
    pub is_rtl: bool,
}

#[derive(Debug, PartialEq)]
pub enum RowColor {
    Default,
    Marked,
    Suspended,
    FlagRed,
    FlagOrange,
    FlagGreen,
    FlagBlue,
}

#[derive(Debug, PartialEq)]
pub struct Font {
    pub name: String,
    pub size: u32,
}

struct RowContext<'a> {
    col: &'a Collection,
    card: Card,
    note: Note,
    notetype: Arc<NoteType>,
    deck: Option<Deck>,
    original_deck: Option<Option<Deck>>,
    i18n: &'a I18n,
    offset: FixedOffset,
    timing: SchedTimingToday,
    question_nodes: Option<Vec<RenderedNode>>,
    answer_nodes: Option<Vec<RenderedNode>>,
}

impl RowContext<'_> {
    fn template(&self) -> Result<&CardTemplate> {
        self.notetype.get_template(self.card.template_idx)
    }

    fn deck(&mut self) -> Result<&Deck> {
        if self.deck.is_none() {
            self.deck = Some(
                self.col
                    .storage
                    .get_deck(self.card.deck_id)?
                    .ok_or(AnkiError::NotFound)?,
            );
        }
        Ok(self.deck.as_ref().unwrap())
    }

    fn original_deck(&mut self) -> Result<&Option<Deck>> {
        if self.original_deck.is_none() {
            self.original_deck = Some(self.col.storage.get_deck(self.card.original_deck_id)?);
        }
        Ok(self.original_deck.as_ref().unwrap())
    }
}

fn row_context_from_cid<'a>(
    col: &'a mut Collection,
    id: CardID,
    with_card_render: bool,
) -> Result<RowContext<'a>> {
    let card = col.storage.get_card(id)?.ok_or(AnkiError::NotFound)?;
    let note = if with_card_render {
        col.storage.get_note(card.note_id)?
    } else {
        col.storage.get_note_without_fields(card.note_id)?
    }
    .ok_or(AnkiError::NotFound)?;
    let notetype = col
        .get_notetype(note.notetype_id)?
        .ok_or(AnkiError::NotFound)?;
    let offset = col.local_utc_offset_for_user()?;
    let timing = col.timing_today()?;
    let (question_nodes, answer_nodes) = if with_card_render {
        let render = col.render_card_inner(
            &note,
            &card,
            &notetype,
            notetype.get_template(card.template_idx)?,
            true,
        )?;
        (Some(render.qnodes), Some(render.anodes))
    } else {
        (None, None)
    };

    Ok(RowContext {
        col,
        card,
        note,
        notetype,
        deck: None,
        original_deck: None,
        i18n: &col.i18n,
        offset,
        timing,
        question_nodes,
        answer_nodes,
    })
}

impl Collection {
    pub fn browser_row_for_card(&mut self, id: CardID) -> Result<Row> {
        let columns: Vec<String> = self.get_config_optional("activeCols").unwrap_or_else(|| {
            vec![
                "noteFld".to_string(),
                "template".to_string(),
                "cardDue".to_string(),
                "deck".to_string(),
            ]
        });
        let mut context = row_context_from_cid(self, id, note_fields_required(&columns))?;
        Ok(Row {
            cells: columns
                .iter()
                .map(|column| get_cell(column, &mut context))
                .collect::<Result<_>>()?,
            color: get_row_color(&context),
            font: get_row_font(&context)?,
        })
    }
}

fn get_cell(column: &str, context: &mut RowContext) -> Result<Cell> {
    Ok(Cell {
        text: get_cell_text(column, context)?,
        is_rtl: get_is_rtl(column, context),
    })
}

fn get_cell_text(column: &str, context: &mut RowContext) -> Result<String> {
    Ok(match column {
        "answer" => answer_str(context)?,
        "cardDue" => card_due_str(context)?,
        "cardEase" => card_ease_str(context),
        "cardIvl" => card_interval_str(context),
        "cardLapses" => context.card.lapses.to_string(),
        "cardMod" => context.card.mtime.date_string(context.offset),
        "cardReps" => context.card.reps.to_string(),
        "deck" => deck_str(context)?,
        "note" => context.notetype.name.to_owned(),
        "noteCrt" => note_creation_str(context),
        "noteFld" => note_field_str(context),
        "noteMod" => context.note.mtime.date_string(context.offset),
        "noteTags" => context.note.tags.join(" "),
        "question" => question_str(context)?,
        "template" => template_str(context)?,
        _ => "".to_string(),
    })
}

fn answer_str(context: &RowContext) -> Result<String> {
    let text = context
        .answer_nodes
        .as_ref()
        .unwrap()
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
    Ok(html_to_text_line(&extract_av_tags(&text, false).0).to_string())
}

fn card_due_str(context: &mut RowContext) -> Result<String> {
    Ok(if context.original_deck()?.is_some() {
        context.i18n.tr(TR::BrowsingFiltered).into()
    } else if context.card.queue == CardQueue::New || context.card.ctype == CardType::New {
        context.i18n.trn(
            TR::StatisticsDueForNewCard,
            tr_args!["number"=>context.card.due],
        )
    } else {
        let date = match context.card.queue {
            CardQueue::Learn => TimestampSecs(context.card.due as i64),
            CardQueue::DayLearn | CardQueue::Review => TimestampSecs::now().adding_secs(
                ((context.card.due - context.timing.days_elapsed as i32) * 86400) as i64,
            ),
            _ => return Ok("".into()),
        };
        date.date_string(context.offset)
    })
}

fn card_ease_str(context: &RowContext) -> String {
    match context.card.ctype {
        CardType::New => context.i18n.tr(TR::BrowsingNew).into(),
        _ => format!("{}%", context.card.ease_factor / 10),
    }
}

fn card_interval_str(context: &RowContext) -> String {
    match context.card.ctype {
        CardType::New => context.i18n.tr(TR::BrowsingNew).into(),
        CardType::Learn => context.i18n.tr(TR::BrowsingLearning).into(),
        _ => time_span((context.card.interval * 86400) as f32, context.i18n, false),
    }
}

fn deck_str(context: &mut RowContext) -> Result<String> {
    let deck_name = context.deck()?.human_name();
    Ok(if let Some(original_deck) = context.original_deck()? {
        format!("{} ({})", &deck_name, &original_deck.human_name())
    } else {
        deck_name
    })
}

fn note_creation_str(context: &RowContext) -> String {
    TimestampMillis(context.note.id.into())
        .as_secs()
        .date_string(context.offset)
}

fn note_field_str(context: &RowContext) -> String {
    if let Some(field) = &context.note.sort_field {
        field.to_owned()
    } else {
        "".to_string()
    }
}

fn template_str(context: &RowContext) -> Result<String> {
    let name = &context.template()?.name;
    Ok(match context.notetype.config.kind() {
        NoteTypeKind::Normal => name.to_owned(),
        NoteTypeKind::Cloze => format!("{} {}", name, context.card.template_idx + 1),
    })
}

fn question_str(context: &RowContext) -> Result<String> {
    let text = context
        .question_nodes
        .as_ref()
        .unwrap()
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
    Ok(html_to_text_line(&extract_av_tags(&text, true).0).to_string())
}

fn get_is_rtl(column: &str, context: &RowContext) -> bool {
    match column {
        "noteFld" => {
            let index = context.notetype.config.sort_field_idx as usize;
            context.notetype.fields[index].config.rtl
        }
        _ => false,
    }
}

fn get_row_color(context: &RowContext) -> RowColor {
    match context.card.flags {
        1 => RowColor::FlagRed,
        2 => RowColor::FlagOrange,
        3 => RowColor::FlagGreen,
        4 => RowColor::FlagBlue,
        _ => {
            if context
                .note
                .tags
                .iter()
                .any(|tag| tag.eq_ignore_ascii_case("marked"))
            {
                RowColor::Marked
            } else if context.card.queue == CardQueue::Suspended {
                RowColor::Suspended
            } else {
                RowColor::Default
            }
        }
    }
}

fn get_row_font(context: &RowContext) -> Result<Font> {
    Ok(Font {
        name: context.template()?.config.browser_font_name.to_owned(),
        size: context.template()?.config.browser_font_size,
    })
}

const FIELDS_REQUIRING_COLUMNS: [&str; 2] = ["question", "answer"];

fn note_fields_required(columns: &[String]) -> bool {
    columns
        .iter()
        .any(|c| FIELDS_REQUIRING_COLUMNS.contains(&c.as_str()))
}
