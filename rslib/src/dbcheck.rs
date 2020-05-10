// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection,
    err::{AnkiError, DBErrorKind, Result},
    i18n::{tr_args, TR},
    notetype::{
        all_stock_notetypes, AlreadyGeneratedCardInfo, CardGenContext, NoteType, NoteTypeKind,
    },
    timestamp::{TimestampMillis, TimestampSecs},
};
use itertools::Itertools;
use slog::debug;
use std::{
    collections::{HashMap, HashSet},
    sync::Arc,
};

impl Collection {
    /// Check the database, returning a list of problems that were fixed.
    pub(crate) fn check_database(&mut self) -> Result<Vec<String>> {
        debug!(self.log, "quick check");
        if self.storage.quick_check_corrupt() {
            debug!(self.log, "quick check failed");
            return Err(AnkiError::DBError {
                info: self.i18n.tr(TR::DatabaseCheckCorrupt).into(),
                kind: DBErrorKind::Corrupt,
            });
        }

        debug!(self.log, "optimize");
        self.storage.optimize()?;

        self.transact(None, |col| col.check_database_inner())
    }

    fn check_database_inner(&mut self) -> Result<Vec<String>> {
        let mut probs = vec![];

        // cards first, as we need to be able to read them to process notes
        debug!(self.log, "check cards");
        self.check_card_properties(&mut probs)?;
        self.check_orphaned_cards(&mut probs)?;

        debug!(self.log, "check decks");
        self.check_missing_deck_ids(&mut probs)?;
        self.check_filtered_cards(&mut probs)?;

        debug!(self.log, "check notetypes");
        self.check_notetypes(&mut probs)?;

        debug!(self.log, "check review log");
        self.check_revlog(&mut probs)?;

        debug!(self.log, "missing decks");
        let names = self.storage.get_all_deck_names()?;
        self.add_missing_deck_names(&names)?;

        self.update_next_new_position()?;

        debug!(self.log, "db check finished with problems: {:#?}", probs);

        Ok(probs)
    }

    fn check_card_properties(&mut self, probs: &mut Vec<String>) -> Result<()> {
        let timing = self.timing_today()?;
        let (new_cnt, other_cnt) = self.storage.fix_card_properties(
            timing.days_elapsed,
            TimestampSecs::now(),
            self.usn()?,
        )?;
        if new_cnt > 0 {
            probs.push(
                self.i18n
                    .trn(TR::DatabaseCheckNewCardHighDue, tr_args!["count"=>new_cnt]),
            );
        }
        if other_cnt > 0 {
            probs.push(self.i18n.trn(
                TR::DatabaseCheckCardProperties,
                tr_args!["count"=>other_cnt],
            ));
        }
        Ok(())
    }

    fn check_orphaned_cards(&mut self, probs: &mut Vec<String>) -> Result<()> {
        let orphaned = self.storage.delete_orphaned_cards()?;
        if orphaned > 0 {
            self.storage.set_schema_modified()?;
            probs.push(self.i18n.trn(
                TR::DatabaseCheckCardMissingNote,
                tr_args!["count"=>orphaned],
            ));
        }
        Ok(())
    }

    fn check_missing_deck_ids(&mut self, probs: &mut Vec<String>) -> Result<()> {
        let mut cnt = 0;
        for did in self.storage.missing_decks()? {
            self.recover_missing_deck(did)?;
            cnt += 1;
        }

        if cnt > 0 {
            probs.push(
                self.i18n
                    .trn(TR::DatabaseCheckMissingDecks, tr_args!["count"=>cnt]),
            );
        }

        Ok(())
    }

    fn check_filtered_cards(&mut self, probs: &mut Vec<String>) -> Result<()> {
        let decks: HashMap<_, _> = self
            .storage
            .get_all_decks()?
            .into_iter()
            .map(|d| (d.id, d))
            .collect();

        let mut wrong = 0;

        for (cid, did) in self.storage.all_filtered_cards_by_deck()? {
            // we expect calling code to ensure all decks already exist
            if let Some(deck) = decks.get(&did) {
                if !deck.is_filtered() {
                    let mut card = self.storage.get_card(cid)?.unwrap();
                    card.odid.0 = 0;
                    card.odue = 0;
                    self.storage.update_card(&card)?;
                    wrong += 1;
                }
            }
        }

        if wrong > 0 {
            self.storage.set_schema_modified()?;
            probs.push(
                self.i18n
                    .trn(TR::DatabaseCheckCardProperties, tr_args!["count"=>wrong]),
            );
        }

        Ok(())
    }

    fn check_notetypes(&mut self, probs: &mut Vec<String>) -> Result<()> {
        let nids_by_notetype = self.storage.all_note_ids_by_notetype()?;
        let norm = self.normalize_note_text();
        let usn = self.usn()?;
        let stamp = TimestampMillis::now();

        // will rebuild tag list below
        self.storage.clear_tags()?;

        let mut note_fields = 0;
        let mut dupe_ords = 0;
        let mut missing_template_ords = 0;

        for (ntid, group) in &nids_by_notetype.into_iter().group_by(|tup| tup.0) {
            debug!(self.log, "check notetype: {}", ntid);
            let mut group = group.peekable();
            let nt = match self.get_notetype(ntid)? {
                None => {
                    let first_note = self.storage.get_note(group.peek().unwrap().1)?.unwrap();
                    self.recover_notetype(stamp, first_note.fields.len())?
                }
                Some(nt) => nt,
            };

            let mut genctx = None;
            for (_, nid) in group {
                let mut note = self.storage.get_note(nid)?.unwrap();

                let cards = self.storage.existing_cards_for_note(nid)?;
                dupe_ords += self.remove_duplicate_card_ordinals(&cards)?;
                missing_template_ords += self.remove_cards_without_template(&nt, &cards)?;

                // fix fields
                if note.fields.len() != nt.fields.len() {
                    note.fix_field_count(&nt);
                    note_fields += 1;
                    note.tags.push("db-check".into());
                }

                // write note, updating tags and generating missing cards
                let ctx = genctx.get_or_insert_with(|| CardGenContext::new(&nt, usn));
                self.update_note_inner_generating_cards(&ctx, &mut note, false, norm)?;
            }
        }

        // if the collection is empty and the user has deleted all note types, ensure at least
        // one note type exists
        if self.storage.get_all_notetype_names()?.is_empty() {
            let mut nt = all_stock_notetypes(&self.i18n).remove(0);
            self.add_notetype_inner(&mut nt)?;
        }

        if note_fields > 0 {
            self.storage.set_schema_modified()?;
            probs.push(
                self.i18n
                    .trn(TR::DatabaseCheckFieldCount, tr_args!["count"=>note_fields]),
            );
        }
        if dupe_ords > 0 {
            self.storage.set_schema_modified()?;
            probs.push(self.i18n.trn(
                TR::DatabaseCheckDuplicateCardOrds,
                tr_args!["count"=>dupe_ords],
            ));
        }
        if missing_template_ords > 0 {
            self.storage.set_schema_modified()?;
            probs.push(self.i18n.trn(
                TR::DatabaseCheckMissingTemplates,
                tr_args!["count"=>missing_template_ords],
            ));
        }

        Ok(())
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
        nt: &NoteType,
        cards: &[AlreadyGeneratedCardInfo],
    ) -> Result<usize> {
        if nt.config.kind() == NoteTypeKind::Cloze {
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
    ) -> Result<Arc<NoteType>> {
        debug!(self.log, "create recovery notetype");
        let mut basic = all_stock_notetypes(&self.i18n).remove(0);
        let mut field = 3;
        while basic.fields.len() < field_count {
            basic.add_field(format!("{}", field));
            field += 1;
        }
        basic.name = format!("db-check-{}-{}", stamp, field_count);
        self.add_notetype(&mut basic)?;
        Ok(Arc::new(basic))
    }

    fn check_revlog(&self, probs: &mut Vec<String>) -> Result<()> {
        let cnt = self.storage.fix_revlog_properties()?;

        if cnt > 0 {
            self.storage.set_schema_modified()?;
            probs.push(
                self.i18n
                    .trn(TR::DatabaseCheckRevlogProperties, tr_args!["count"=>cnt]),
            );
        }

        Ok(())
    }

    fn update_next_new_position(&self) -> Result<()> {
        let pos = self.storage.max_new_card_position().unwrap_or(0);
        self.set_next_card_position(pos)
    }
}
