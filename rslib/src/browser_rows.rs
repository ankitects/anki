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

const CARD_RENDER_COLUMNS: [&str; 2] = ["question", "answer"];

#[derive(Debug, PartialEq)]
pub struct Row {
    pub cells: Vec<Cell>,
    pub color: Color,
    pub font: Font,
}

#[derive(Debug, PartialEq)]
pub struct Cell {
    pub text: String,
    pub is_rtl: bool,
}

#[derive(Debug, PartialEq)]
pub enum Color {
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

fn card_render_required(columns: &[String]) -> bool {
    columns
        .iter()
        .any(|c| CARD_RENDER_COLUMNS.contains(&c.as_str()))
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
        let mut context = RowContext::new(self, id, card_render_required(&columns))?;

        Ok(Row {
            cells: columns
                .iter()
                .map(|column| context.get_cell(column))
                .collect::<Result<_>>()?,
            color: context.get_row_color(),
            font: context.get_row_font()?,
        })
    }
}

impl<'a> RowContext<'a> {
    fn new(col: &'a mut Collection, id: CardID, with_card_render: bool) -> Result<Self> {
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
            let render = col.render_card(
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

    fn get_cell(&mut self, column: &str) -> Result<Cell> {
        Ok(Cell {
            text: self.get_cell_text(column)?,
            is_rtl: self.get_is_rtl(column),
        })
    }

    fn get_cell_text(&mut self, column: &str) -> Result<String> {
        Ok(match column {
            "answer" => self.answer_str()?,
            "cardDue" => self.card_due_str()?,
            "cardEase" => self.card_ease_str(),
            "cardIvl" => self.card_interval_str(),
            "cardLapses" => self.card.lapses.to_string(),
            "cardMod" => self.card.mtime.date_string(self.offset),
            "cardReps" => self.card.reps.to_string(),
            "deck" => self.deck_str()?,
            "note" => self.notetype.name.to_owned(),
            "noteCrt" => self.note_creation_str(),
            "noteFld" => self.note_field_str(),
            "noteMod" => self.note.mtime.date_string(self.offset),
            "noteTags" => self.note.tags.join(" "),
            "question" => self.question_str()?,
            "template" => self.template_str()?,
            _ => "".to_string(),
        })
    }

    fn answer_str(&self) -> Result<String> {
        let text = self
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

    fn card_due_str(&mut self) -> Result<String> {
        Ok(if self.original_deck()?.is_some() {
            self.i18n.tr(TR::BrowsingFiltered).into()
        } else if self.card.queue == CardQueue::New || self.card.ctype == CardType::New {
            self.i18n.trn(
                TR::StatisticsDueForNewCard,
                tr_args!["number"=>self.card.due],
            )
        } else {
            let date = match self.card.queue {
                CardQueue::Learn => TimestampSecs(self.card.due as i64),
                CardQueue::DayLearn | CardQueue::Review => TimestampSecs::now().adding_secs(
                    ((self.card.due - self.timing.days_elapsed as i32) * 86400) as i64,
                ),
                _ => return Ok("".into()),
            };
            date.date_string(self.offset)
        })
    }

    fn card_ease_str(&self) -> String {
        match self.card.ctype {
            CardType::New => self.i18n.tr(TR::BrowsingNew).into(),
            _ => format!("{}%", self.card.ease_factor / 10),
        }
    }

    fn card_interval_str(&self) -> String {
        match self.card.ctype {
            CardType::New => self.i18n.tr(TR::BrowsingNew).into(),
            CardType::Learn => self.i18n.tr(TR::BrowsingLearning).into(),
            _ => time_span((self.card.interval * 86400) as f32, self.i18n, false),
        }
    }

    fn deck_str(&mut self) -> Result<String> {
        let deck_name = self.deck()?.human_name();
        Ok(if let Some(original_deck) = self.original_deck()? {
            format!("{} ({})", &deck_name, &original_deck.human_name())
        } else {
            deck_name
        })
    }

    fn note_creation_str(&self) -> String {
        TimestampMillis(self.note.id.into())
            .as_secs()
            .date_string(self.offset)
    }

    fn note_field_str(&self) -> String {
        if let Some(field) = &self.note.sort_field {
            field.to_owned()
        } else {
            "".to_string()
        }
    }

    fn template_str(&self) -> Result<String> {
        let name = &self.template()?.name;
        Ok(match self.notetype.config.kind() {
            NoteTypeKind::Normal => name.to_owned(),
            NoteTypeKind::Cloze => format!("{} {}", name, self.card.template_idx + 1),
        })
    }

    fn question_str(&self) -> Result<String> {
        let text = self
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

    fn get_is_rtl(&self, column: &str) -> bool {
        match column {
            "noteFld" => {
                let index = self.notetype.config.sort_field_idx as usize;
                self.notetype.fields[index].config.rtl
            }
            _ => false,
        }
    }

    fn get_row_color(&self) -> Color {
        match self.card.flags {
            1 => Color::FlagRed,
            2 => Color::FlagOrange,
            3 => Color::FlagGreen,
            4 => Color::FlagBlue,
            _ => {
                if self
                    .note
                    .tags
                    .iter()
                    .any(|tag| tag.eq_ignore_ascii_case("marked"))
                {
                    Color::Marked
                } else if self.card.queue == CardQueue::Suspended {
                    Color::Suspended
                } else {
                    Color::Default
                }
            }
        }
    }

    fn get_row_font(&self) -> Result<Font> {
        Ok(Font {
            name: self.template()?.config.browser_font_name.to_owned(),
            size: self.template()?.config.browser_font_size,
        })
    }
}
