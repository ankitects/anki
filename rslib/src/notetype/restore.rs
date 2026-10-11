// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::notetypes::stock_notetype::Kind;
use anki_proto::notetypes::stock_notetype::OriginalStockKind;

use crate::notetype::notetypechange::default_field_map;
use crate::notetype::notetypechange::default_template_map;
use crate::notetype::stock::get_original_stock_notetype;
use crate::notetype::stock::StockKind;
use crate::prelude::*;

impl Collection {
    /// If force_kind is not Unknown, it will be used in preference to the kind
    /// stored in the notetype. If Unknown, and the kind stored in the
    /// notetype is also Unknown, an error will be returned.
    pub(crate) fn restore_notetype_to_stock(
        &mut self,
        notetype_id: NotetypeId,
        force_kind: Option<StockKind>,
    ) -> Result<OpOutput<()>> {
        let mut nt = self
            .storage
            .get_notetype(notetype_id)?
            .or_not_found(notetype_id)?;
        let stock_kind = match (nt.config.original_stock_kind(), force_kind) {
            (_, Some(force_kind)) => match force_kind {
                Kind::Basic => OriginalStockKind::Basic,
                Kind::BasicAndReversed => OriginalStockKind::BasicAndReversed,
                Kind::BasicOptionalReversed => OriginalStockKind::BasicOptionalReversed,
                Kind::BasicTyping => OriginalStockKind::BasicTyping,
                Kind::Cloze => OriginalStockKind::Cloze,
                Kind::ImageOcclusion => OriginalStockKind::ImageOcclusion,
            },
            (stock, _) => stock,
        };
        if stock_kind == OriginalStockKind::Unknown {
            invalid_input!("unknown original notetype kind");
        }

        let mut stock_nt = get_original_stock_notetype(stock_kind, &self.tr)?;
        // Map stock fields and templates to the existing ones by name, so reordering
        // them doesn't move content into the wrong place. Unmatched ones take the
        // remaining existing ones in order. Cloze templates are mapped by position.
        let template_map = default_template_map(&nt, &stock_nt)
            .unwrap_or_else(|| (0..stock_nt.templates.len()).map(Some).collect());
        let field_map = default_field_map(&nt, &stock_nt);
        for (item, ord) in stock_nt.templates.iter_mut().zip(template_map) {
            item.ord = ord.map(|ord| ord as u32);
        }
        nt.templates = stock_nt.templates;
        for (item, ord) in stock_nt.fields.iter_mut().zip(field_map) {
            item.ord = ord.map(|ord| ord as u32);
        }
        nt.fields = stock_nt.fields;
        nt.config.css = stock_nt.config.css;
        if force_kind.is_some() {
            nt.config.original_stock_kind = stock_kind as i32;
            nt.config.kind = stock_nt.config.kind;
        }
        self.update_notetype(&mut nt, false)
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn adding_and_removing_fields_and_templates() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let note = NoteAdder::basic(&mut col)
            .fields(&["front", "back"])
            .add(&mut col);

        col.restore_notetype_to_stock(nt.id, Some(StockKind::BasicOptionalReversed))?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.fields(), &["front", "back", ""]);
        assert_eq!(
            col.storage.db_scalar::<u32>("select count(*) from cards")?,
            1
        );

        col.restore_notetype_to_stock(nt.id, Some(StockKind::BasicAndReversed))?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.fields(), &["front", "back"]);
        assert_eq!(
            col.storage.db_scalar::<u32>("select count(*) from cards")?,
            2
        );

        col.restore_notetype_to_stock(nt.id, Some(StockKind::Cloze))?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.fields(), &["front", "back"]);
        assert_eq!(
            col.storage.db_scalar::<u32>("select count(*) from cards")?,
            1
        );

        Ok(())
    }

    #[test]
    fn reordered_fields_keep_their_content() -> Result<()> {
        let mut col = Collection::new();
        for (mut nt, fields, q_format) in [
            (col.basic_notetype(), ["front", "back"], "{{Front}}"),
            (
                col.cloze_notetype(),
                ["{{c1::text}}", "extra"],
                "{{cloze:Text}}",
            ),
        ] {
            let note = NoteAdder::new(&nt).fields(&fields).add(&mut col);
            nt.fields.swap(0, 1);
            col.update_notetype(&mut nt, false)?;

            col.restore_notetype_to_stock(nt.id, None)?;
            let note = col.storage.get_note(note.id)?.unwrap();
            assert_eq!(note.fields(), &fields);
            let restored = col.get_notetype(nt.id)?.unwrap();
            assert_eq!(restored.templates[0].config.q_format, q_format);
        }

        Ok(())
    }

    #[test]
    fn reordered_templates_keep_their_cards() -> Result<()> {
        let mut col = Collection::new();
        let mut nt = col.basic_rev_notetype();
        let note = NoteAdder::new(&nt).fields(&["front", "back"]).add(&mut col);
        let card_ids_by_template = |col: &Collection| -> Result<Vec<CardId>> {
            let mut cards = col.storage.all_cards_of_note(note.id)?;
            cards.sort_by_key(|card| card.template_idx);
            Ok(cards.into_iter().map(|card| card.id).collect())
        };
        let original_cards = card_ids_by_template(&col)?;
        nt.templates.swap(0, 1);
        col.update_notetype(&mut nt, false)?;

        col.restore_notetype_to_stock(nt.id, None)?;
        assert_eq!(card_ids_by_template(&col)?, original_cards);

        Ok(())
    }
}
