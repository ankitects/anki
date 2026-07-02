// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

impl super::SqliteStorage {
    /// True if any ids used as timestamps are larger than `cutoff`.
    pub(crate) fn invalid_ids(&self, cutoff: i64) -> Result<usize> {
        Ok(self
            .db
            .query_row_and_then(include_str!("invalid_ids_count.sql"), [cutoff], |r| {
                r.get(0)
            })?)
    }

    /// Ensures all ids used as timestamps are `max_valid_id` or lower.
    /// If not, new ids will be assigned starting at whichever is larger,
    /// `new_id` or the next free valid id.
    /// `new_id` must be a valid id, i.e. lower or equal to `max_valid_id`.
    pub(crate) fn fix_invalid_ids(&self, max_valid_id: i64, new_id: i64) -> Result<()> {
        require!(new_id <= max_valid_id, "new_id is invalid");
        for (source_table, foreign_table) in [
            ("notes", Some(("cards", "nid"))),
            ("cards", Some(("revlog", "cid"))),
            ("revlog", None),
        ] {
            self.setup_invalid_ids_table(source_table, max_valid_id, new_id)?;
            self.update_invalid_ids_from_table(source_table, "id")?;
            if let Some((target_table, id_column)) = foreign_table {
                self.update_invalid_ids_from_table(target_table, id_column)?;
            }
        }
        self.db.execute(include_str!("invalid_ids_drop.sql"), [])?;
        Ok(())
    }

    fn setup_invalid_ids_table(
        &self,
        source_table: &str,
        max_valid_id: i64,
        new_id: i64,
    ) -> Result<()> {
        self.db.execute_batch(&format!(
            include_str!("invalid_ids_create.sql"),
            source_table = source_table,
            max_valid_id = max_valid_id,
            new_id = new_id,
        ))?;
        Ok(())
    }

    /// Fix the invalid ids in `id_column` of `target_table` using the map from
    /// the invalid ids temporary table.
    fn update_invalid_ids_from_table(&self, target_table: &str, id_column: &str) -> Result<()> {
        self.db.execute_batch(&format!(
            include_str!("invalid_ids_update.sql"),
            target_table = target_table,
            id_column = id_column,
        ))?;
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use crate::prelude::*;

    #[test]
    fn any_invalid_ids() {
        let mut col = Collection::new();
        assert_eq!(col.storage.invalid_ids(0).unwrap(), 0);
        NoteAdder::basic(&mut col).add(&mut col);
        // 1 card and 1 note
        assert_eq!(col.storage.invalid_ids(0).unwrap(), 2);
        assert_eq!(
            col.storage.invalid_ids(TimestampMillis::now().0).unwrap(),
            0
        );
    }

    #[test]
    fn fix_invalid_note_ids_only_and_update_cards() {
        let mut col = Collection::new();
        let valid = NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col).add(&mut col);
        col.storage.fix_invalid_ids(valid.id.0, 42).unwrap();
        assert_eq!(col.storage.all_cards_of_note(valid.id).unwrap().len(), 1);
        assert_eq!(col.storage.all_cards_of_note(NoteId(42)).unwrap().len(), 1);
    }

    #[test]
    fn fix_invalid_card_ids_only() {
        let mut col = Collection::new();
        let mut cards = CardAdder::new().siblings(3).add(&mut col);
        col.storage.fix_invalid_ids(cards[0].id.0, 42).unwrap();
        cards.sort_by(|c1, c2| c1.id.cmp(&c2.id));
        cards[1].id.0 = 42;
        cards[2].id.0 = 43;
        let old_first_card = cards.remove(0);
        cards.push(old_first_card);
        let mut new_cards = col.storage.get_all_cards();
        new_cards.sort_by(|c1, c2| c1.id.cmp(&c2.id));
        assert_eq!(new_cards, cards);
    }

    #[test]
    fn update_revlog_when_fixing_card_ids() {
        let mut col = Collection::new();
        CardAdder::new().due_dates(["7"]).add(&mut col);
        col.storage.fix_invalid_ids(42, 42).unwrap();
        // revlog id was also reset to 42
        let revlog_entry = col.storage.get_revlog_entry(RevlogId(42)).unwrap().unwrap();
        assert_eq!(revlog_entry.cid.0, 42);
    }
}
