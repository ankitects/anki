// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use itertools::Itertools;
use serde::Deserialize;
use serde::Serialize;
use serde_tuple::Serialize_tuple;
use tracing::debug;

use crate::card::Card;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::notes::Note;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::serde::deserialize_int_from_number;
use crate::storage::card::data::card_data_string;
use crate::storage::card::data::CardData;
use crate::sync::collection::normal::ClientSyncState;
use crate::sync::collection::normal::NormalSyncer;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::collection::start::ServerSyncState;
use crate::sync::request::IntoSyncRequest;
use crate::tags::join_tags;
use crate::tags::split_tags;

/// Cards collected while applying downloaded chunks, for FSRS reconciliation
/// once the whole download has been merged. Chunks interleave revlog and card
/// rows, so the reschedule decision must wait until the download is complete.
#[derive(Debug, Default)]
pub(crate) struct FsrsReconcileCandidates {
    /// Locally modified cards whose incoming row differs in FSRS-relevant
    /// fields.
    pub(crate) cards: HashSet<CardId>,
    /// Cards that received at least one new rating review in this download.
    pub(crate) cards_with_new_reviews: HashSet<CardId>,
}

impl FsrsReconcileCandidates {
    /// Flagged cards that received a new review: their schedule may change.
    /// Deck moves, (un)suspend, flags or remote resets never move a schedule.
    /// Reconciliation also reschedules a flagged card whose winning row has
    /// an older `last_review_time` than its newest review (see
    /// `should_reschedule_reconciled_card`).
    pub(crate) fn cards_to_reschedule(&self) -> HashSet<CardId> {
        self.cards
            .intersection(&self.cards_with_new_reviews)
            .copied()
            .collect()
    }
}

pub(in crate::sync) struct ChunkableIds {
    revlog: Vec<RevlogId>,
    cards: Vec<CardId>,
    notes: Vec<NoteId>,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct Chunk {
    #[serde(default)]
    pub done: bool,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub revlog: Vec<RevlogEntry>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub cards: Vec<CardEntry>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub notes: Vec<NoteEntry>,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct NoteEntry {
    pub id: NoteId,
    pub guid: String,
    #[serde(rename = "mid")]
    pub ntid: NotetypeId,
    #[serde(rename = "mod")]
    pub mtime: TimestampSecs,
    pub usn: Usn,
    pub tags: String,
    pub fields: String,
    pub sfld: String, // always empty
    pub csum: String, // always empty
    pub flags: u32,
    pub data: String,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct CardEntry {
    pub id: CardId,
    pub nid: NoteId,
    pub did: DeckId,
    pub ord: u16,
    #[serde(deserialize_with = "deserialize_int_from_number")]
    pub mtime: TimestampSecs,
    pub usn: Usn,
    pub ctype: CardType,
    pub queue: CardQueue,
    #[serde(deserialize_with = "deserialize_int_from_number")]
    pub due: i32,
    #[serde(deserialize_with = "deserialize_int_from_number")]
    pub ivl: u32,
    pub factor: u16,
    pub reps: u32,
    pub lapses: u32,
    pub left: u32,
    #[serde(deserialize_with = "deserialize_int_from_number")]
    pub odue: i32,
    pub odid: DeckId,
    pub flags: u8,
    pub data: String,
}

impl NormalSyncer<'_> {
    pub(in crate::sync) async fn process_chunks_from_server(
        &mut self,
        state: &ClientSyncState,
        fsrs_reconcile_candidates: &mut FsrsReconcileCandidates,
    ) -> Result<()> {
        loop {
            let chunk = self.server.chunk(EmptyInput::request()).await?.json()?;

            debug!(
                done = chunk.done,
                cards = chunk.cards.len(),
                notes = chunk.notes.len(),
                revlog = chunk.revlog.len(),
                "received"
            );

            self.progress.update(false, |p| {
                p.remote_update += chunk.cards.len() + chunk.notes.len() + chunk.revlog.len()
            })?;

            let done = chunk.done;
            self.col
                .apply_chunk(chunk, state.pending_usn, fsrs_reconcile_candidates)?;

            self.progress.check_cancelled()?;

            if done {
                return Ok(());
            }
        }
    }

    pub(in crate::sync) async fn send_chunks_to_server(
        &mut self,
        state: &ClientSyncState,
    ) -> Result<()> {
        let mut ids = self.col.get_chunkable_ids(state.pending_usn)?;

        loop {
            let chunk: Chunk = self.col.get_chunk(&mut ids, Some(state.server_usn))?;
            let done = chunk.done;

            debug!(
                done = chunk.done,
                cards = chunk.cards.len(),
                notes = chunk.notes.len(),
                revlog = chunk.revlog.len(),
                "sending"
            );

            self.progress.update(false, |p| {
                p.local_update += chunk.cards.len() + chunk.notes.len() + chunk.revlog.len()
            })?;

            self.server
                .apply_chunk(ApplyChunkRequest { chunk }.try_into_sync_request()?)
                .await?;

            self.progress.check_cancelled()?;

            if done {
                return Ok(());
            }
        }
    }
}

impl Collection {
    // Remote->local chunks
    //----------------------------------------------------------------

    /// pending_usn is used to decide whether the local objects are newer.
    /// If the provided objects are not modified locally, the USN inside
    /// the individual objects is used.
    pub(in crate::sync) fn apply_chunk(
        &mut self,
        chunk: Chunk,
        pending_usn: Usn,
        fsrs_reconcile_candidates: &mut FsrsReconcileCandidates,
    ) -> Result<()> {
        self.merge_revlog(chunk.revlog, fsrs_reconcile_candidates)?;
        self.merge_cards(chunk.cards, pending_usn, fsrs_reconcile_candidates)?;
        self.merge_notes(chunk.notes, pending_usn)
    }

    fn merge_revlog(
        &self,
        entries: Vec<RevlogEntry>,
        fsrs_reconcile_candidates: &mut FsrsReconcileCandidates,
    ) -> Result<()> {
        for entry in entries {
            let added = self.storage.add_revlog_entry(&entry, false)?.is_some();
            if added && entry.has_rating_and_affects_scheduling() {
                fsrs_reconcile_candidates
                    .cards_with_new_reviews
                    .insert(entry.cid);
            }
        }
        Ok(())
    }

    fn merge_cards(
        &self,
        entries: Vec<CardEntry>,
        pending_usn: Usn,
        fsrs_reconcile_candidates: &mut FsrsReconcileCandidates,
    ) -> Result<()> {
        for entry in entries {
            self.add_or_update_card_if_newer(entry, pending_usn, fsrs_reconcile_candidates)?;
        }
        Ok(())
    }

    fn add_or_update_card_if_newer(
        &self,
        entry: CardEntry,
        pending_usn: Usn,
        fsrs_reconcile_candidates: &mut FsrsReconcileCandidates,
    ) -> Result<()> {
        let proceed = if let Some(existing_card) = self.storage.get_card(entry.id)? {
            if existing_card.usn.is_pending_sync(pending_usn)
                && card_needs_fsrs_reconcile(&existing_card, &entry)
            {
                fsrs_reconcile_candidates.cards.insert(entry.id);
            }
            !existing_card.usn.is_pending_sync(pending_usn) || existing_card.mtime < entry.mtime
        } else {
            true
        };
        if proceed {
            let card = entry.into();
            self.storage.add_or_update_card(&card)?;
        }
        Ok(())
    }

    fn merge_notes(&mut self, entries: Vec<NoteEntry>, pending_usn: Usn) -> Result<()> {
        for entry in entries {
            self.add_or_update_note_if_newer(entry, pending_usn)?;
        }
        Ok(())
    }

    fn add_or_update_note_if_newer(&mut self, entry: NoteEntry, pending_usn: Usn) -> Result<()> {
        let proceed = if let Some(existing_note) = self.storage.get_note(entry.id)? {
            !existing_note.usn.is_pending_sync(pending_usn) || existing_note.mtime < entry.mtime
        } else {
            true
        };
        if proceed {
            let mut note: Note = entry.into();
            let nt = self
                .get_notetype(note.notetype_id)?
                .or_invalid("note missing notetype")?;
            note.prepare_for_update(&nt, false)?;
            self.storage.add_or_update_note(&note)?;
        }
        Ok(())
    }

    // Local->remote chunks
    //----------------------------------------------------------------

    pub(in crate::sync) fn get_chunkable_ids(&self, pending_usn: Usn) -> Result<ChunkableIds> {
        Ok(ChunkableIds {
            revlog: self.storage.objects_pending_sync("revlog", pending_usn)?,
            cards: self.storage.objects_pending_sync("cards", pending_usn)?,
            notes: self.storage.objects_pending_sync("notes", pending_usn)?,
        })
    }

    /// Fetch a chunk of ids from `ids`, returning the referenced objects.
    pub(in crate::sync) fn get_chunk(
        &self,
        ids: &mut ChunkableIds,
        server_usn_if_client: Option<Usn>,
    ) -> Result<Chunk> {
        // get a bunch of IDs
        let mut limit = CHUNK_SIZE as i32;
        let mut revlog_ids = vec![];
        let mut card_ids = vec![];
        let mut note_ids = vec![];
        let mut chunk = Chunk::default();
        while limit > 0 {
            let last_limit = limit;
            if let Some(id) = ids.revlog.pop() {
                revlog_ids.push(id);
                limit -= 1;
            }
            if let Some(id) = ids.notes.pop() {
                note_ids.push(id);
                limit -= 1;
            }
            if let Some(id) = ids.cards.pop() {
                card_ids.push(id);
                limit -= 1;
            }
            if limit == last_limit {
                // all empty
                break;
            }
        }
        if limit > 0 {
            chunk.done = true;
        }

        // remove pending status
        if !self.server {
            self.storage
                .maybe_update_object_usns("revlog", &revlog_ids, server_usn_if_client)?;
            self.storage
                .maybe_update_object_usns("cards", &card_ids, server_usn_if_client)?;
            self.storage
                .maybe_update_object_usns("notes", &note_ids, server_usn_if_client)?;
        }

        // the fetch associated objects, and return
        chunk.revlog = revlog_ids
            .into_iter()
            .map(|id| {
                self.storage.get_revlog_entry(id).map(|e| {
                    let mut e = e.unwrap();
                    e.usn = server_usn_if_client.unwrap_or(e.usn);
                    e
                })
            })
            .collect::<Result<_>>()?;
        chunk.cards = card_ids
            .into_iter()
            .map(|id| {
                self.storage.get_card(id).map(|e| {
                    let mut e: CardEntry = e.unwrap().into();
                    e.usn = server_usn_if_client.unwrap_or(e.usn);
                    e
                })
            })
            .collect::<Result<_>>()?;
        chunk.notes = note_ids
            .into_iter()
            .map(|id| {
                self.storage.get_note(id).map(|e| {
                    let mut e: NoteEntry = e.unwrap().into();
                    e.usn = server_usn_if_client.unwrap_or(e.usn);
                    e
                })
            })
            .collect::<Result<_>>()?;

        Ok(chunk)
    }
}

fn card_needs_fsrs_reconcile(existing: &Card, incoming: &CardEntry) -> bool {
    let incoming_data = CardData::from_str(&incoming.data);
    let incoming_has_fsrs = incoming_data.memory_state().is_some()
        || incoming_data.fsrs_desired_retention.is_some()
        || incoming_data.decay.is_some()
        || existing.memory_state.is_some()
        || existing.desired_retention.is_some()
        || existing.decay.is_some();

    incoming_has_fsrs
        && (existing.memory_state != incoming_data.memory_state()
            || existing.desired_retention != incoming_data.fsrs_desired_retention
            || existing.decay != incoming_data.decay
            || existing.last_review_time != incoming_data.last_review_time
            || card_schedule_differs(existing, incoming))
}

fn card_schedule_differs(existing: &Card, incoming: &CardEntry) -> bool {
    existing.deck_id != incoming.did
        || existing.ctype != incoming.ctype
        || existing.queue != incoming.queue
        || existing.due != incoming.due
        || existing.interval != incoming.ivl
        || existing.original_due != incoming.odue
        || existing.original_deck_id != incoming.odid
}

impl From<CardEntry> for Card {
    fn from(e: CardEntry) -> Self {
        let data = CardData::from_str(&e.data);
        Card {
            id: e.id,
            note_id: e.nid,
            deck_id: e.did,
            template_idx: e.ord,
            mtime: e.mtime,
            usn: e.usn,
            ctype: e.ctype,
            queue: e.queue,
            due: e.due,
            interval: e.ivl,
            ease_factor: e.factor,
            reps: e.reps,
            lapses: e.lapses,
            remaining_steps: e.left,
            original_due: e.odue,
            original_deck_id: e.odid,
            flags: e.flags,
            original_position: data.original_position,
            memory_state: data.memory_state(),
            desired_retention: data.fsrs_desired_retention,
            decay: data.decay,
            last_review_time: data.last_review_time,
            custom_data: data.custom_data,
        }
    }
}

impl From<Card> for CardEntry {
    fn from(e: Card) -> Self {
        CardEntry {
            id: e.id,
            nid: e.note_id,
            did: e.deck_id,
            ord: e.template_idx,
            mtime: e.mtime,
            usn: e.usn,
            ctype: e.ctype,
            queue: e.queue,
            due: e.due,
            ivl: e.interval,
            factor: e.ease_factor,
            reps: e.reps,
            lapses: e.lapses,
            left: e.remaining_steps,
            odue: e.original_due,
            odid: e.original_deck_id,
            flags: e.flags,
            data: card_data_string(&e),
        }
    }
}

impl From<NoteEntry> for Note {
    fn from(e: NoteEntry) -> Self {
        let fields = e.fields.split('\x1f').map(ToString::to_string).collect();
        Note::new_from_storage(
            e.id,
            e.guid,
            e.ntid,
            e.mtime,
            e.usn,
            split_tags(&e.tags).map(ToString::to_string).collect(),
            fields,
            None,
            None,
        )
    }
}

impl From<Note> for NoteEntry {
    fn from(e: Note) -> Self {
        NoteEntry {
            id: e.id,
            fields: e.fields().iter().join("\x1f"),
            guid: e.guid,
            ntid: e.notetype_id,
            mtime: e.mtime,
            usn: e.usn,
            tags: join_tags(&e.tags),
            sfld: String::new(),
            csum: String::new(),
            flags: 0,
            data: String::new(),
        }
    }
}

pub fn server_chunk(col: &mut Collection, state: &mut ServerSyncState) -> Result<Chunk> {
    if state.server_chunk_ids.is_none() {
        state.server_chunk_ids = Some(col.get_chunkable_ids(state.client_usn)?);
    }
    col.get_chunk(state.server_chunk_ids.as_mut().unwrap(), None)
}

pub fn server_apply_chunk(
    req: ApplyChunkRequest,
    col: &mut Collection,
    state: &mut ServerSyncState,
) -> Result<()> {
    // The server never reconciles FSRS state.
    col.apply_chunk(
        req.chunk,
        state.client_usn,
        &mut FsrsReconcileCandidates::default(),
    )
}

impl Usn {
    pub(crate) fn is_pending_sync(self, pending_usn: Usn) -> bool {
        if pending_usn.0 == -1 {
            self.0 == -1
        } else {
            self.0 >= pending_usn.0
        }
    }
}

pub const CHUNK_SIZE: usize = 250;

#[derive(Serialize, Deserialize, Debug)]
pub struct ApplyChunkRequest {
    pub chunk: Chunk,
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::card::FsrsMemoryState;
    use crate::revlog::RevlogId;
    use crate::revlog::RevlogReviewKind;
    use crate::search::SortMode;

    const PENDING: Usn = Usn(-1);

    fn memory_state(stability: f32) -> Option<FsrsMemoryState> {
        Some(FsrsMemoryState {
            stability,
            difficulty: 5.0,
        })
    }

    /// A collection holding one review card with FSRS data. The stored card is
    /// modified locally (usn -1) unless the caller changes that.
    fn col_with_fsrs_card() -> (Collection, Card) {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic").unwrap().unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "front").unwrap();
        col.add_note(&mut note, DeckId(1)).unwrap();
        let card_id = col.search_cards(note.id, SortMode::NoOrder).unwrap()[0];
        let mut card = col.storage.get_card(card_id).unwrap().unwrap();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.due = 100;
        card.interval = 10;
        card.memory_state = memory_state(10.0);
        card.desired_retention = Some(0.9);
        card.decay = Some(0.2);
        card.last_review_time = Some(TimestampSecs(1_000_000));
        card.usn = PENDING;
        col.storage.update_card(&card).unwrap();
        (col, card)
    }

    /// Applies one incoming card row and returns the cards flagged for FSRS
    /// reconciliation.
    fn apply(col: &Collection, entry: CardEntry, pending_usn: Usn) -> HashSet<CardId> {
        let mut candidates = FsrsReconcileCandidates::default();
        col.add_or_update_card_if_newer(entry, pending_usn, &mut candidates)
            .unwrap();
        candidates.cards
    }

    fn revlog_entry(
        review_kind: RevlogReviewKind,
        button_chosen: u8,
        ease_factor: u32,
    ) -> RevlogEntry {
        RevlogEntry {
            id: RevlogId(1_000_000_000),
            cid: CardId(1),
            usn: Usn(5),
            button_chosen,
            interval: 10,
            last_interval: 5,
            ease_factor,
            taken_millis: 1000,
            review_kind,
        }
    }

    /// Merges downloaded revlog entries and returns the cards recorded as
    /// having received new reviews.
    fn merge(col: &Collection, entries: Vec<RevlogEntry>) -> HashSet<CardId> {
        let mut candidates = FsrsReconcileCandidates::default();
        col.merge_revlog(entries, &mut candidates).unwrap();
        candidates.cards_with_new_reviews
    }

    #[test]
    fn card_not_in_collection_is_not_flagged_for_fsrs_reconcile() {
        let (col, card) = col_with_fsrs_card();
        let mut entry = CardEntry::from(card);
        entry.id = CardId(entry.id.0 + 1);

        assert!(apply(&col, entry, PENDING).is_empty());
    }

    #[test]
    fn locally_unmodified_card_is_not_flagged_for_fsrs_reconcile() {
        let (col, mut card) = col_with_fsrs_card();
        card.usn = Usn(5);
        col.storage.update_card(&card).unwrap();
        let mut incoming = card.clone();
        incoming.memory_state = memory_state(20.0);

        assert!(apply(&col, incoming.into(), PENDING).is_empty());
    }

    #[test]
    fn card_without_fsrs_data_on_either_side_is_not_flagged_for_fsrs_reconcile() {
        let (col, mut card) = col_with_fsrs_card();
        card.memory_state = None;
        card.desired_retention = None;
        card.decay = None;
        col.storage.update_card(&card).unwrap();
        let mut incoming = card.clone();
        incoming.due += 5;
        incoming.interval += 5;

        assert!(apply(&col, incoming.into(), PENDING).is_empty());
    }

    #[test]
    fn identical_incoming_card_is_not_flagged_for_fsrs_reconcile() {
        let (col, card) = col_with_fsrs_card();

        assert!(apply(&col, card.into(), PENDING).is_empty());
    }

    #[test]
    fn memory_state_difference_is_flagged_for_fsrs_reconcile() {
        let (col, card) = col_with_fsrs_card();
        let mut incoming = card.clone();
        incoming.memory_state = memory_state(card.memory_state.unwrap().stability + 1.0);

        assert_eq!(
            apply(&col, incoming.into(), PENDING),
            HashSet::from([card.id])
        );
    }

    #[test]
    fn last_review_time_difference_is_flagged_for_fsrs_reconcile() {
        let (col, card) = col_with_fsrs_card();
        let mut incoming = card.clone();
        incoming.last_review_time = Some(TimestampSecs(1_000_100));

        assert_eq!(
            apply(&col, incoming.into(), PENDING),
            HashSet::from([card.id])
        );
    }

    #[test]
    fn due_and_interval_difference_is_flagged_for_fsrs_reconcile() {
        let (col, card) = col_with_fsrs_card();
        let mut incoming = card.clone();
        incoming.due += 20;
        incoming.interval += 20;

        assert_eq!(
            apply(&col, incoming.into(), PENDING),
            HashSet::from([card.id])
        );
    }

    #[test]
    fn new_rating_review_is_recorded_as_new_review() {
        let col = Collection::new();
        let review = revlog_entry(RevlogReviewKind::Review, 3, 2500);

        assert_eq!(merge(&col, vec![review]), HashSet::from([CardId(1)]));
    }

    #[test]
    fn review_already_present_locally_is_not_recorded_as_new_review() {
        let col = Collection::new();
        let review = revlog_entry(RevlogReviewKind::Review, 3, 2500);
        col.storage.add_revlog_entry(&review, false).unwrap();

        assert!(merge(&col, vec![review]).is_empty());
    }

    #[test]
    fn new_reset_entry_is_not_recorded_as_new_review() {
        let col = Collection::new();
        let reset = revlog_entry(RevlogReviewKind::Manual, 0, 0);

        assert!(merge(&col, vec![reset]).is_empty());
    }

    #[test]
    fn new_cramming_entry_is_not_recorded_as_new_review() {
        let col = Collection::new();
        let cramming = revlog_entry(RevlogReviewKind::Filtered, 3, 0);

        assert!(merge(&col, vec![cramming]).is_empty());
    }

    #[test]
    fn only_flagged_cards_with_new_reviews_are_rescheduled() {
        let candidates = FsrsReconcileCandidates {
            cards: HashSet::from([CardId(1), CardId(2)]),
            cards_with_new_reviews: HashSet::from([CardId(2), CardId(3)]),
        };

        assert_eq!(candidates.cards_to_reschedule(), HashSet::from([CardId(2)]));
    }
}
