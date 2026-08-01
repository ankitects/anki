// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::notetypes::stock_notetype::Kind;
use anki_proto::notetypes::stock_notetype::OriginalStockKind;

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
        for (idx, item) in stock_nt.templates.iter_mut().enumerate() {
            item.ord = Some(idx as u32);
        }
        nt.templates = stock_nt.templates;
        for (idx, item) in stock_nt.fields.iter_mut().enumerate() {
            item.ord = Some(idx as u32);
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
}
