// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, collections::HashMap};

use super::{CardTemplate, Notetype, NotetypeKind};
use crate::{
    card::{Card, CardId},
    collection::Collection,
    error::{AnkiError, Result},
    i18n::I18n,
    notes::{Note, NoteId},
    template::{field_is_empty, render_card, ParsedTemplate, RenderedNode},
};

pub struct RenderCardOutput {
    pub qnodes: Vec<RenderedNode>,
    pub anodes: Vec<RenderedNode>,
    pub css: String,
    pub latex_svg: bool,
}

impl Collection {
    /// Render an existing card saved in the database.
    pub fn render_existing_card(&mut self, cid: CardId, browser: bool) -> Result<RenderCardOutput> {
        let card = self
            .storage
            .get_card(cid)?
            .ok_or_else(|| AnkiError::invalid_input("no such card"))?;
        let note = self
            .storage
            .get_note(card.note_id)?
            .ok_or_else(|| AnkiError::invalid_input("no such note"))?;
        let nt = self
            .get_notetype(note.notetype_id)?
            .ok_or_else(|| AnkiError::invalid_input("no such notetype"))?;
        let template = match nt.config.kind() {
            NotetypeKind::Normal => nt.templates.get(card.template_idx as usize),
            NotetypeKind::Cloze => nt.templates.get(0),
        }
        .ok_or_else(|| AnkiError::invalid_input("missing template"))?;

        self.render_card(&note, &card, &nt, template, browser)
    }

    /// Render a card that may not yet have been added.
    /// The provided ordinal will be used if the template has not yet been saved.
    /// If fill_empty is set, note will be mutated.
    pub fn render_uncommitted_card(
        &mut self,
        note: &mut Note,
        template: &CardTemplate,
        card_ord: u16,
        fill_empty: bool,
    ) -> Result<RenderCardOutput> {
        let card = self.existing_or_synthesized_card(note.id, template.ord, card_ord)?;
        let nt = self
            .get_notetype(note.notetype_id)?
            .ok_or_else(|| AnkiError::invalid_input("no such notetype"))?;

        if fill_empty {
            fill_empty_fields(note, &template.config.q_format, &nt, &self.tr);
        }

        self.render_card(note, &card, &nt, template, false)
    }

    fn existing_or_synthesized_card(
        &self,
        nid: NoteId,
        template_ord: Option<u32>,
        card_ord: u16,
    ) -> Result<Card> {
        // fetch existing card
        if let Some(ord) = template_ord {
            if let Some(card) = self.storage.get_card_by_ordinal(nid, ord as u16)? {
                return Ok(card);
            }
        }

        // no existing card; synthesize one
        Ok(Card {
            template_idx: card_ord,
            ..Default::default()
        })
    }

    pub fn render_card(
        &mut self,
        note: &Note,
        card: &Card,
        nt: &Notetype,
        template: &CardTemplate,
        browser: bool,
    ) -> Result<RenderCardOutput> {
        let mut field_map = note.fields_map(&nt.fields);

        let card_num;
        self.add_special_fields(&mut field_map, note, card, nt, template)?;
        // due to lifetime restrictions we need to add card number here
        card_num = format!("c{}", card.template_idx + 1);
        field_map.entry(&card_num).or_insert_with(|| "1".into());

        let (qfmt, afmt) = if browser {
            (
                template.question_format_for_browser(),
                template.answer_format_for_browser(),
            )
        } else {
            (
                template.config.q_format.as_str(),
                template.config.a_format.as_str(),
            )
        };

        let (qnodes, anodes) = render_card(
            qfmt,
            afmt,
            &field_map,
            card.template_idx,
            nt.is_cloze(),
            &self.tr,
        )?;
        Ok(RenderCardOutput {
            qnodes,
            anodes,
            css: nt.config.css.clone(),
            latex_svg: nt.config.latex_svg,
        })
    }

    /// Add special fields if they don't clobber note fields.
    /// The fields supported here must coincide with SPECIAL_FIELDS in
    /// notetype/mod.rs, apart from FrontSide which is handled by Python.
    fn add_special_fields(
        &mut self,
        map: &mut HashMap<&str, Cow<str>>,
        note: &Note,
        card: &Card,
        nt: &Notetype,
        template: &CardTemplate,
    ) -> Result<()> {
        let tags = note.tags.join(" ");
        map.entry("Tags").or_insert_with(|| tags.into());
        map.entry("Type").or_insert_with(|| nt.name.clone().into());
        let deck_name: Cow<str> = self
            .get_deck(card.original_deck_id.or(card.deck_id))?
            .map(|d| d.human_name().into())
            .unwrap_or_else(|| "(Deck)".into());
        let subdeck_name = deck_name.rsplit("::").next().unwrap();
        map.entry("Subdeck")
            .or_insert_with(|| subdeck_name.to_string().into());
        map.entry("Deck")
            .or_insert_with(|| deck_name.to_string().into());
        map.entry("CardFlag")
            .or_insert_with(|| flag_name(card.flags).into());
        map.entry("Card")
            .or_insert_with(|| template.name.clone().into());

        Ok(())
    }
}

fn flag_name(n: u8) -> &'static str {
    match n {
        1 => "flag1",
        2 => "flag2",
        3 => "flag3",
        4 => "flag4",
        _ => "",
    }
}

fn fill_empty_fields(note: &mut Note, qfmt: &str, nt: &Notetype, tr: &I18n) {
    if let Ok(tmpl) = ParsedTemplate::from_text(qfmt) {
        let cloze_fields = tmpl.cloze_fields();

        for (val, field) in note.fields_mut().iter_mut().zip(nt.fields.iter()) {
            if field_is_empty(val) {
                if cloze_fields.contains(&field.name.as_str()) {
                    *val = tr.card_templates_sample_cloze().into();
                } else {
                    *val = format!("({})", field.name);
                }
            }
        }
    }
}
