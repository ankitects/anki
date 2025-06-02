// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;

use super::CardTemplate;
use super::Notetype;
use super::NotetypeKind;
use crate::prelude::*;
use crate::template::field_is_empty;
use crate::template::render_card;
use crate::template::ParsedTemplate;
use crate::template::RenderCardRequest;
use crate::template::RenderedNode;

#[derive(Debug)]
pub struct RenderCardOutput {
    pub qnodes: Vec<RenderedNode>,
    pub anodes: Vec<RenderedNode>,
    pub css: String,
    pub latex_svg: bool,
    pub is_empty: bool,
}

impl RenderCardOutput {
    /// The question text. This is only valid to call when partial_render=false.
    pub fn question(&self) -> Cow<str> {
        match self.qnodes.as_slice() {
            [RenderedNode::Text { text }] => text.into(),
            _ => "not fully rendered".into(),
        }
    }

    /// The answer text. This is only valid to call when partial_render=false.
    pub fn answer(&self) -> Cow<str> {
        match self.anodes.as_slice() {
            [RenderedNode::Text { text }] => text.into(),
            _ => "not fully rendered".into(),
        }
    }
}

impl Collection {
    /// Render an existing card saved in the database.
    pub fn render_existing_card(
        &mut self,
        cid: CardId,
        browser: bool,
        partial_render: bool,
    ) -> Result<RenderCardOutput> {
        let card = self.storage.get_card(cid)?.or_invalid("no such card")?;
        let note = self
            .storage
            .get_note(card.note_id)?
            .or_invalid("no such note")?;
        let nt = self
            .get_notetype(note.notetype_id)?
            .or_invalid("no such notetype")?;
        let template = match nt.config.kind() {
            NotetypeKind::Normal => nt.templates.get(card.template_idx as usize),
            NotetypeKind::Cloze => nt.templates.first(),
        }
        .or_invalid("missing template")?;

        self.render_card(&note, &card, &nt, template, browser, partial_render)
    }

    /// Render a card that may not yet have been added.
    /// The provided ordinal will be used if the template has not yet been
    /// saved. If fill_empty is set, note will be mutated.
    pub fn render_uncommitted_card(
        &mut self,
        note: &mut Note,
        template: &CardTemplate,
        card_ord: u16,
        fill_empty: bool,
        partial_render: bool,
    ) -> Result<RenderCardOutput> {
        let card = self.existing_or_synthesized_card(note.id, template.ord, card_ord)?;
        let nt = self
            .get_notetype(note.notetype_id)?
            .or_invalid("no such notetype")?;

        if fill_empty {
            fill_empty_fields(note, &template.config.q_format, &nt, &self.tr);
        }

        self.render_card(note, &card, &nt, template, false, partial_render)
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
        partial_render: bool,
    ) -> Result<RenderCardOutput> {
        let mut field_map = note.fields_map(&nt.fields);

        self.add_special_fields(&mut field_map, note, card, nt, template)?;
        // due to lifetime restrictions we need to add card number here
        let card_num = format!("c{}", card.template_idx + 1);
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

        let response = render_card(RenderCardRequest {
            qfmt,
            afmt,
            field_map: &field_map,
            card_ord: card.template_idx,
            is_cloze: nt.is_cloze(),
            browser,
            tr: &self.tr,
            partial_render,
        })?;
        Ok(RenderCardOutput {
            qnodes: response.qnodes,
            anodes: response.anodes,
            css: nt.config.css.clone(),
            latex_svg: nt.config.latex_svg,
            is_empty: response.is_empty,
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
        map.entry("CardID")
            .or_insert_with(|| card.id.to_string().into());

        Ok(())
    }
}

fn flag_name(n: u8) -> String {
    format!("flag{n}")
}

fn fill_empty_fields(note: &mut Note, qfmt: &str, nt: &Notetype, tr: &I18n) {
    if let Ok(tmpl) = ParsedTemplate::from_text(qfmt) {
        let cloze_fields = tmpl.all_referenced_cloze_field_names();

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

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::CollectionBuilder;

    #[test]
    fn can_render_fully() -> Result<()> {
        let mut col = CollectionBuilder::default().build()?;
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = Note::new(&nt);
        note.set_field(0, "front")?;
        note.set_field(1, "back")?;
        let out: RenderCardOutput =
            col.render_uncommitted_card(&mut note, &nt.templates[0], 0, false, false)?;
        assert_eq!(&out.question(), "front");
        assert_eq!(&out.answer(), "front\n\n<hr id=answer>\n\nback");

        // should work even if unknown filters are encountered
        let mut tmpl = nt.templates[0].clone();
        tmpl.config.q_format = "{{some_filter:Front}}{{another_filter:}}".into();
        let out = col.render_uncommitted_card(&mut note, &nt.templates[0], 0, false, false)?;
        assert_eq!(&out.question(), "front");

        Ok(())
    }
}
