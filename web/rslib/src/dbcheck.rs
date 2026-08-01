// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;
use std::sync::Arc;

use anki_i18n::I18n;
use anki_proto::notetypes::stock_notetype::OriginalStockKind;
use anki_proto::notetypes::ImageOcclusionField;
use itertools::Itertools;
use tracing::debug;

use crate::collection::Collection;
use crate::config::SchedulerVersion;
use crate::error::AnkiError;
use crate::error::DbError;
use crate::error::DbErrorKind;
use crate::error::Result;
use crate::notetype::all_stock_notetypes;
use crate::notetype::AlreadyGeneratedCardInfo;
use crate::notetype::CardGenContext;
use crate::notetype::Notetype;
use crate::notetype::NotetypeId;
use crate::notetype::NotetypeKind;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::storage::card::CardFixStats;
use crate::timestamp::TimestampMillis;
use crate::timestamp::TimestampSecs;

#[derive(Debug, Default, PartialEq, Eq)]
pub struct CheckDatabaseOutput {
    card_properties_invalid: usize,
    card_position_too_high: usize,
    cards_missing_note: usize,
    decks_missing: usize,
    revlog_properties_invalid: usize,
    templates_missing: usize,
    card_ords_duplicated: usize,
    field_count_mismatch: usize,
    notetypes_recovered: usize,
    invalid_utf8: usize,
    invalid_ids: usize,
    card_last_review_time_empty: usize,
}

#[derive(Debug, Clone, Copy, Default)]
pub enum DatabaseCheckProgress {
    #[default]
    Integrity,
    Optimize,
    Cards,
    Notes {
        current: usize,
        total: usize,
    },
    History,
}

impl CheckDatabaseOutput {
    pub fn to_i18n_strings(&self, tr: &I18n) -> Vec<String> {
        let mut probs = Vec::new();

        if self.notetypes_recovered > 0 {
            probs.push(tr.database_check_notetypes_recovered());
        }

        if self.card_position_too_high > 0 {
            probs.push(tr.database_check_new_card_high_due(self.card_position_too_high));
        }
        if self.card_properties_invalid > 0 {
            probs.push(tr.database_check_card_properties(self.card_properties_invalid));
        }
        if self.card_last_review_time_empty > 0 {
            probs.push(
                tr.database_check_card_last_review_time_empty(self.card_last_review_time_empty),
            );
        }
        if self.cards_missing_note > 0 {
            probs.push(tr.database_check_card_missing_note(self.cards_missing_note));
        }
        if self.decks_missing > 0 {
            probs.push(tr.database_check_missing_decks(self.decks_missing));
        }
        if self.field_count_mismatch > 0 {
            probs.push(tr.database_check_field_count(self.field_count_mismatch));
        }
        if self.card_ords_duplicated > 0 {
            probs.push(tr.database_check_duplicate_card_ords(self.card_ords_duplicated));
        }
        if self.templates_missing > 0 {
            probs.push(tr.database_check_missing_templates(self.templates_missing));
        }
        if self.revlog_properties_invalid > 0 {
            probs.push(tr.database_check_revlog_properties(self.revlog_properties_invalid));
        }
        if self.invalid_utf8 > 0 {
            probs.push(tr.database_check_notes_with_invalid_utf8(self.invalid_utf8));
        }
        if self.invalid_ids > 0 {
            probs.push(tr.database_check_fixed_invalid_ids(self.invalid_ids));
        }

        probs.into_iter().map(Into::into).collect()
    }
}

impl Collection {
    /// Check the database, returning a list of problems that were fixed.
    pub(crate) fn check_database(&mut self) -> Result<CheckDatabaseOutput> {
        let mut progress = self.new_progress_handler();
        progress.set(DatabaseCheckProgress::Integrity)?;
        debug!("quick check");
        if self.storage.quick_check_corrupt() {
            debug!("quick check failed");
            return Err(AnkiError::db_error(
                self.tr.database_check_corrupt(),
                DbErrorKind::Corrupt,
            ));
        }

        progress.set(DatabaseCheckProgress::Optimize)?;
        debug!("optimize");
        self.storage.optimize()?;

        self.transact_no_undo(|col| col.check_database_inner(progress))
    }

    fn check_database_inner(
        &mut self,
        mut progress: ThrottlingProgressHandler<DatabaseCheckProgress>,
    ) -> Result<CheckDatabaseOutput> {
        let mut out = CheckDatabaseOutput::default();

        // cards first, as we need to be able to read them to process notes
        progress.set(DatabaseCheckProgress::Cards)?;
        debug!("check cards");
        self.check_card_properties(&mut out)?;
        self.check_orphaned_cards(&mut out)?;

        debug!("check decks");
        self.check_missing_deck_ids(&mut out)?;
        self.check_filtered_cards(&mut out)?;

        debug!("check notetypes");
        self.check_notetypes(&mut out, &mut progress)?;

        progress.set(DatabaseCheckProgress::History)?;

        debug!("check review log");
        self.check_revlog(&mut out)?;

        debug!("missing decks");
        self.check_missing_deck_names(&mut out)?;

        self.update_next_new_position()?;

        debug!("invalid ids");
        out.invalid_ids = self.maybe_fix_invalid_ids()?;

        debug!("db check finished: {:#?}", out);

        Ok(out)
    }

    fn check_card_properties(&mut self, out: &mut CheckDatabaseOutput) -> Result<()> {
        let timing = self.timing_today()?;
        let CardFixStats {
            new_cards_fixed,
            other_cards_fixed,
            last_review_time_fixed,
        } = self.storage.fix_card_properties(
            timing.days_elapsed,
            TimestampSecs::now(),
            self.usn()?,
            self.scheduler_version() == SchedulerVersion::V1,
        )?;
        out.card_position_too_high = new_cards_fixed;
        out.card_properties_invalid += other_cards_fixed;
        out.card_last_review_time_empty = last_review_time_fixed;

        // Trigger one-way sync if last_review_time was updated to avoid conflicts
        if last_review_time_fixed > 0 {
            self.set_schema_modified()?;
        }

        Ok(())
    }

    fn check_orphaned_cards(&mut self, out: &mut CheckDatabaseOutput) -> Result<()> {
        let cnt = self.storage.delete_orphaned_cards()?;
        if cnt > 0 {
            self.set_schema_modified()?;
            out.cards_missing_note = cnt;
        }
        Ok(())
    }

    fn check_missing_deck_ids(&mut self, out: &mut CheckDatabaseOutput) -> Result<()> {
        let usn = self.usn()?;
        for did in self.storage.missing_decks()? {
            self.recover_missing_deck(did, usn)?;
            out.decks_missing += 1;
        }
        Ok(())
    }

    fn check_filtered_cards(&mut self, out: &mut CheckDatabaseOutput) -> Result<()> {
        let decks = self.storage.get_decks_map()?;

        let mut wrong = 0;
        for (cid, did) in self.storage.all_filtered_cards_by_deck()? {
            // we expect calling code to ensure all decks already exist
            if let Some(deck) = decks.get(&did) {
                if !deck.is_filtered() {
                    let mut card = self.storage.get_card(cid)?.unwrap();
                    card.original_deck_id.0 = 0;
                    card.original_due = 0;
                    self.storage.update_card(&card)?;
                    wrong += 1;
                }
            }
        }

        if wrong > 0 {
            self.set_schema_modified()?;
            out.card_properties_invalid += wrong;
        }

        Ok(())
    }

    fn check_notetypes(
        &mut self,
        out: &mut CheckDatabaseOutput,
        progress: &mut ThrottlingProgressHandler<DatabaseCheckProgress>,
    ) -> Result<()> {
        let nids_by_notetype = self.storage.all_note_ids_by_notetype()?;
        let norm = self.get_config_bool(BoolKey::NormalizeNoteText);
        let usn = self.usn()?;
        let stamp_millis = TimestampMillis::now();
        let stamp_secs = TimestampSecs::now();

        let expanded_tags = self.storage.expanded_tags()?;
        self.storage.clear_all_tags()?;

        let total_notes = self.storage.total_notes()?;

        progress.set(DatabaseCheckProgress::Notes {
            current: 0,
            total: total_notes as usize,
        })?;
        for (ntid, group) in &nids_by_notetype.into_iter().chunk_by(|tup| tup.0) {
            debug!("check notetype: {}", ntid);
            let mut group = group.peekable();
            let mut nt = match self.get_notetype(ntid)? {
                None => {
                    let first_note = self.storage.get_note(group.peek().unwrap().1)?.unwrap();
                    out.notetypes_recovered += 1;
                    self.recover_notetype(stamp_millis, first_note.fields().len(), ntid)?
                }
                Some(nt) => nt,
            };

            self.add_missing_field_tags(Arc::make_mut(&mut nt))?;

            let mut genctx = None;
            for (_, nid) in group {
                progress.increment(|p| {
                    let DatabaseCheckProgress::Notes { current, .. } = p else {
                        unreachable!()
                    };
                    current
                })?;

                let mut note = self.get_note_fixing_invalid_utf8(nid, out)?;
                let original = note.clone();

                let cards = self.storage.existing_cards_for_note(nid)?;

                out.card_ords_duplicated += self.remove_duplicate_card_ordinals(&cards)?;
                out.templates_missing += self.remove_cards_without_template(&nt, &cards)?;

                // fix fields
                if note.fields().len() != nt.fields.len() {
                    note.fix_field_count(&nt);
                    note.tags.push("db-check".into());
                    out.field_count_mismatch += 1;
                }

                if note.mtime > stamp_secs {
                    note.mtime = stamp_secs;
                }

                // note type ID may have changed if we created a recovery notetype
                note.notetype_id = nt.id;

                // write note, updating tags and generating missing cards
                let ctx = genctx.get_or_insert_with(|| {
                    CardGenContext::new(
                        nt.as_ref(),
                        self.get_last_deck_added_to_for_notetype(nt.id),
                        usn,
                    )
                });
                self.update_note_inner_generating_cards(
                    ctx, &mut note, &original, false, norm, true,
                )?;
            }
        }

        // the note rebuilding process took care of adding tags back, so we just need
        // to ensure to restore the collapse state
        self.storage.restore_expanded_tags(&expanded_tags)?;

        // if the collection is empty and the user has deleted all note types, ensure at
        // least one note type exists
        if self.storage.get_all_notetype_names()?.is_empty() {
            let mut nt = all_stock_notetypes(&self.tr).remove(0);
            self.add_notetype_inner(&mut nt, usn, true)?;
        }

        if out.card_ords_duplicated > 0
            || out.field_count_mismatch > 0
            || out.templates_missing > 0
            || out.notetypes_recovered > 0
        {
            self.set_schema_modified()?;
        }

        Ok(())
    }

    fn get_note_fixing_invalid_utf8(
        &self,
        nid: NoteId,
        out: &mut CheckDatabaseOutput,
    ) -> Result<Note> {
        match self.storage.get_note(nid) {
            Ok(note) => Ok(note.unwrap()),
            Err(err) => match err {
                AnkiError::DbError {
                    source:
                        DbError {
                            kind: DbErrorKind::Utf8,
                            ..
                        },
                } => {
                    // fix note then fetch again
                    self.storage.fix_invalid_utf8_in_note(nid)?;
                    out.invalid_utf8 += 1;
                    Ok(self.storage.get_note(nid)?.unwrap())
                }
                // other errors are unhandled
                _ => Err(err),
            },
        }
    }

    fn remove_duplicate_card_ordinals(
        &mut self,
        cards: &[AlreadyGeneratedCardInfo],
    ) -> Result<usize> {
        let mut ords = HashSet::new();
        let mut removed = 0;
        for card in cards {
            if !ords.insert(card.ord) {
                self.storage.remove_card(card.id)?;
                removed += 1;
            }
        }

        Ok(removed)
    }

    fn remove_cards_without_template(
        &mut self,
        nt: &Notetype,
        cards: &[AlreadyGeneratedCardInfo],
    ) -> Result<usize> {
        if nt.config.kind() == NotetypeKind::Cloze {
            return Ok(0);
        }
        let mut removed = 0;
        for card in cards {
            if card.ord as usize >= nt.templates.len() {
                self.storage.remove_card(card.id)?;
                removed += 1;
            }
        }

        Ok(removed)
    }

    fn recover_notetype(
        &mut self,
        stamp: TimestampMillis,
        field_count: usize,
        previous_id: NotetypeId,
    ) -> Result<Arc<Notetype>> {
        debug!("create recovery notetype");
        let extra_cards_required = self
            .storage
            .highest_card_ordinal_for_notetype(previous_id)?;
        let mut basic = all_stock_notetypes(&self.tr).remove(0);
        let mut field = 3;
        while basic.fields.len() < field_count {
            basic.add_field(format!("{field}"));
            field += 1;
        }
        basic.name = format!("db-check-{stamp}-{field_count}");
        let qfmt = basic.templates[0].config.q_format.clone();
        let afmt = basic.templates[0].config.a_format.clone();
        for n in 0..extra_cards_required {
            basic.add_template(format!("Card {}", n + 2), &qfmt, &afmt);
        }
        self.add_notetype(&mut basic, true)?;
        Ok(Arc::new(basic))
    }

    fn check_revlog(&mut self, out: &mut CheckDatabaseOutput) -> Result<()> {
        let cnt = self.storage.fix_revlog_properties()?;
        if cnt > 0 {
            self.set_schema_modified()?;
            out.revlog_properties_invalid = cnt;
        }

        Ok(())
    }

    fn check_missing_deck_names(&mut self, out: &mut CheckDatabaseOutput) -> Result<()> {
        let names = self.storage.get_all_deck_names()?;
        out.decks_missing += self.add_missing_deck_names(&names)?;
        Ok(())
    }

    fn update_next_new_position(&mut self) -> Result<()> {
        let pos = self.storage.max_new_card_position().unwrap_or(0);
        self.set_next_card_position(pos)
    }

    pub(crate) fn maybe_fix_invalid_ids(&mut self) -> Result<usize> {
        let now = TimestampMillis::now();
        let tomorrow = now.adding_secs(24 * 60 * 60).0;
        let num_invalid_ids = self.storage.invalid_ids(tomorrow)?;
        if num_invalid_ids > 0 {
            self.storage.fix_invalid_ids(tomorrow, now.0)?;
            self.set_schema_modified()?;
        }
        Ok(num_invalid_ids)
    }
    fn add_missing_field_tags(&mut self, nt: &mut Notetype) -> Result<()> {
        // we only try to fix I/O, as the other notetypes have been in circulation too
        // long, and there's too much of a risk that the user has reordered the fields
        // already. We could try to match on field name in the future though.
        let usn = self.usn()?;
        if let OriginalStockKind::ImageOcclusion = nt.config.original_stock_kind() {
            let mut changed = false;
            if nt.fields.len() >= 5 {
                for i in 0..5 {
                    let conf = &mut nt.fields[i].config;
                    if !conf.prevent_deletion {
                        changed = true;
                        conf.prevent_deletion = i != ImageOcclusionField::Comments as usize;
                        conf.tag = Some(i as u32);
                    }
                }
            }
            if changed {
                nt.set_modified(usn);
                self.add_or_update_notetype_with_existing_id_inner(nt, None, usn, true)?;
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::decks::DeckId;
    use crate::search::SortMode;

    #[test]
    fn cards() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        // card properties
        col.storage
            .db
            .execute_batch("update cards set ivl=1.5,due=2000000,odue=1.5")?;

        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                card_properties_invalid: 2,
                card_position_too_high: 1,
                ..Default::default()
            }
        );
        // should be idempotent
        assert_eq!(col.check_database()?, Default::default());

        // missing deck
        col.storage.db.execute_batch("update cards set did=123")?;

        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                decks_missing: 1,
                ..Default::default()
            }
        );
        assert_eq!(
            col.storage
                .get_deck(DeckId(123))?
                .unwrap()
                .name
                .as_native_str(),
            "recovered123"
        );

        // missing note
        col.storage.remove_note(note.id)?;
        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                cards_missing_note: 1,
                ..Default::default()
            }
        );
        assert_eq!(
            col.storage.db_scalar::<u32>("select count(*) from cards")?,
            0
        );

        Ok(())
    }

    #[test]
    fn revlog() -> Result<()> {
        let mut col = Collection::new();

        col.storage.db.execute_batch(
            "
        insert into revlog (id,cid,usn,ease,ivl,lastIvl,factor,time,type)
        values (0,0,0,0,1.5,1.5,0,0,0)",
        )?;

        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                revlog_properties_invalid: 1,
                ..Default::default()
            }
        );
        assert!(col
            .storage
            .db_scalar::<bool>("select ivl = lastIvl = 1 from revlog")?);

        Ok(())
    }

    #[test]
    fn note_card_link() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        // duplicate ordinals
        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let mut card = col.storage.get_card(cid)?.unwrap();
        card.id.0 += 1;
        col.storage.add_card(&mut card)?;

        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                card_ords_duplicated: 1,
                ..Default::default()
            }
        );
        assert_eq!(
            col.storage.db_scalar::<u32>("select count(*) from cards")?,
            1
        );

        // missing templates
        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let mut card = col.storage.get_card(cid)?.unwrap();
        card.id.0 += 1;
        card.template_idx = 10;
        col.storage.add_card(&mut card)?;

        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                templates_missing: 1,
                ..Default::default()
            }
        );
        assert_eq!(
            col.storage.db_scalar::<u32>("select count(*) from cards")?,
            1
        );

        Ok(())
    }

    #[test]
    fn note_fields() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        // excess fields get joined into the last one
        col.storage
            .db
            .execute_batch("update notes set flds = 'a\x1fb\x1fc\x1fd'")?;
        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                field_count_mismatch: 1,
                ..Default::default()
            }
        );
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.fields()[..], &["a", "b; c; d"]);

        // missing fields get filled with blanks
        col.storage
            .db
            .execute_batch("update notes set flds = 'a'")?;
        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                field_count_mismatch: 1,
                ..Default::default()
            }
        );
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.fields()[..], &["a", ""]);

        Ok(())
    }

    #[test]
    fn deck_names() -> Result<()> {
        let mut col = Collection::new();

        let deck = col.get_or_create_normal_deck("foo::bar::baz")?;
        // includes default
        assert_eq!(col.storage.get_all_deck_names()?.len(), 4);

        col.storage
            .db
            .prepare("delete from decks where id != ? and id != 1")?
            .execute([deck.id])?;
        assert_eq!(col.storage.get_all_deck_names()?.len(), 2);

        let out = col.check_database()?;
        assert_eq!(
            out,
            CheckDatabaseOutput {
                decks_missing: 1, // only counts the immediate parent that was missing
                ..Default::default()
            }
        );
        assert_eq!(
            &col.storage
                .get_all_deck_names()?
                .iter()
                .map(|(_, name)| name)
                .collect::<Vec<_>>(),
            &["Default", "foo", "foo::bar", "foo::bar::baz"]
        );

        Ok(())
    }

    #[test]
    fn tags() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags.push("one".into());
        note.tags.push("two".into());
        col.add_note(&mut note, DeckId(1))?;

        col.set_tag_collapsed("one", false)?;

        col.check_database()?;

        assert!(col.storage.get_tag("one")?.unwrap().expanded);
        assert!(!col.storage.get_tag("two")?.unwrap().expanded);

        Ok(())
    }
}
