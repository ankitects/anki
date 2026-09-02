// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod service;
pub(crate) mod undo;

use std::collections::hash_map::Entry;
use std::collections::HashMap;
use std::collections::HashSet;

use fsrs::MemoryState;
use num_enum::TryFromPrimitive;
use serde_repr::Deserialize_repr;
use serde_repr::Serialize_repr;

use crate::collection::Collection;
use crate::config::BoolKey;
use crate::config::SchedulerVersion;
use crate::deckconfig::DeckConfig;
use crate::decks::DeckId;
use crate::define_newtype;
use crate::error::AnkiError;
use crate::error::FilteredDeckError;
use crate::error::Result;
use crate::notes::NoteId;
use crate::ops::StateChanges;
use crate::prelude::*;
use crate::scheduler::fsrs::memory_state::UpdateMemoryStateEntry;
use crate::scheduler::fsrs::memory_state::UpdateMemoryStateRequest;
use crate::scheduler::fsrs::params::ignore_revlogs_before_ms_from_config;
use crate::search::Negated;
use crate::timestamp::TimestampSecs;
use crate::types::Usn;

define_newtype!(CardId, i64);

impl CardId {
    pub fn as_secs(self) -> TimestampSecs {
        TimestampSecs(self.0 / 1000)
    }
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Eq, TryFromPrimitive, Clone, Copy)]
#[repr(u8)]
pub enum CardType {
    New = 0,
    Learn = 1,
    Review = 2,
    Relearn = 3,
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Eq, TryFromPrimitive, Clone, Copy)]
#[repr(i8)]
pub enum CardQueue {
    /// due is the order cards are shown in
    New = 0,
    /// due is a unix timestamp
    Learn = 1,
    /// due is days since creation date
    Review = 2,
    DayLearn = 3,
    /// due is a unix timestamp.
    /// preview cards only placed here when failed.
    PreviewRepeat = 4,
    /// cards are not due in these states
    Suspended = -1,
    SchedBuried = -2,
    UserBuried = -3,
}

/// Which of the blue/red/green numbers this card maps to.
pub enum CardQueueNumber {
    New,
    Learning,
    Review,
    /// Suspended/buried cards should not be included.
    Invalid,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Card {
    pub(crate) id: CardId,
    pub(crate) note_id: NoteId,
    pub(crate) deck_id: DeckId,
    pub(crate) template_idx: u16,
    pub(crate) mtime: TimestampSecs,
    pub(crate) usn: Usn,
    pub(crate) ctype: CardType,
    pub(crate) queue: CardQueue,
    pub(crate) due: i32,
    pub(crate) interval: u32,
    pub(crate) ease_factor: u16,
    pub(crate) reps: u32,
    pub(crate) lapses: u32,
    pub(crate) remaining_steps: u32,
    pub(crate) original_due: i32,
    pub(crate) original_deck_id: DeckId,
    pub(crate) flags: u8,
    /// The position in the new queue before leaving it.
    pub(crate) original_position: Option<u32>,
    pub(crate) memory_state: Option<FsrsMemoryState>,
    pub(crate) desired_retention: Option<f32>,
    pub(crate) decay: Option<f32>,
    pub(crate) last_review_time: Option<TimestampSecs>,
    /// JSON object or empty; exposed through the reviewer for persisting custom
    /// state
    pub(crate) custom_data: String,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct FsrsMemoryState {
    /// The expected memory stability, in days.
    pub stability: f32,
    /// A number in the range 1.0-10.0. Use difficulty() for a normalized
    /// number.
    pub difficulty: f32,
}

impl FsrsMemoryState {
    /// Returns the difficulty normalized to a 0.0-1.0 range.
    pub(crate) fn difficulty(&self) -> f32 {
        (self.difficulty - 1.0) / 9.0
    }

    /// Returns the difficulty normalized to a 0.1-1.1 range,
    /// which is used in revlog entries.
    pub(crate) fn difficulty_shifted(&self) -> f32 {
        self.difficulty() + 0.1
    }
}

impl Default for Card {
    fn default() -> Self {
        Self {
            id: CardId(0),
            note_id: NoteId(0),
            deck_id: DeckId(0),
            template_idx: 0,
            mtime: TimestampSecs(0),
            usn: Usn(0),
            ctype: CardType::New,
            queue: CardQueue::New,
            due: 0,
            interval: 0,
            ease_factor: 0,
            reps: 0,
            lapses: 0,
            remaining_steps: 0,
            original_due: 0,
            original_deck_id: DeckId(0),
            flags: 0,
            original_position: None,
            memory_state: None,
            desired_retention: None,
            decay: None,
            last_review_time: None,
            custom_data: String::new(),
        }
    }
}

impl Card {
    pub fn id(&self) -> CardId {
        self.id
    }

    pub fn note_id(&self) -> NoteId {
        self.note_id
    }

    pub fn deck_id(&self) -> DeckId {
        self.deck_id
    }

    pub fn template_idx(&self) -> u16 {
        self.template_idx
    }

    pub fn queue_number(&self) -> CardQueueNumber {
        match self.queue {
            CardQueue::New => CardQueueNumber::New,
            CardQueue::PreviewRepeat | CardQueue::Learn => CardQueueNumber::Learning,
            CardQueue::DayLearn | CardQueue::Review => CardQueueNumber::Review,
            CardQueue::Suspended | CardQueue::SchedBuried | CardQueue::UserBuried => {
                CardQueueNumber::Invalid
            }
        }
    }

    pub fn set_modified(&mut self, usn: Usn) {
        self.mtime = TimestampSecs::now();
        self.usn = usn;
    }

    pub fn clear_fsrs_data(&mut self) {
        self.memory_state = None;
        self.desired_retention = None;
        self.decay = None;
    }

    /// Caller must ensure provided deck exists and is not filtered.
    fn set_deck(&mut self, deck: DeckId) {
        self.remove_from_filtered_deck_restoring_queue();
        self.clear_fsrs_data();
        self.deck_id = deck;
    }

    /// True if flag changed.
    fn set_flag(&mut self, flag: u8) -> bool {
        // The first 3 bits represent one of the 7 supported flags, the rest of
        // the flag byte is preserved.
        let updated_flags = (self.flags & !0b111) | flag;
        if self.flags != updated_flags {
            self.flags = updated_flags;
            true
        } else {
            false
        }
    }

    /// Return the total number of steps left to do, ignoring the
    /// "steps today" number packed into the DB representation.
    pub fn remaining_steps(&self) -> u32 {
        self.remaining_steps % 1000
    }

    /// Return ease factor as a multiplier (eg 2.5)
    pub fn ease_factor(&self) -> f32 {
        (self.ease_factor as f32) / 1000.0
    }

    /// Don't use this in situations where you may be using original_due, since
    /// it only applies to the current due date. You may want to use
    /// is_unix_epoch_timestap() instead.
    pub fn is_intraday_learning(&self) -> bool {
        matches!(self.queue, CardQueue::Learn | CardQueue::PreviewRepeat)
    }

    pub fn new(note_id: NoteId, template_idx: u16, deck_id: DeckId, due: i32) -> Self {
        Card {
            note_id,
            deck_id,
            template_idx,
            due,
            ..Default::default()
        }
    }

    /// Remaining steps after configured steps have changed, disregarding
    /// "remaining today". [None] if same as before. A step counts as
    /// remaining if the card has not passed a step with the same or a
    /// greater delay, but output will be at least 1.
    fn new_remaining_steps(&self, new_steps: &[f32], old_steps: &[f32]) -> Option<u32> {
        let remaining = self.remaining_steps();

        let new_remaining = if old_steps.is_empty() {
            remaining
        } else {
            old_steps
                .len()
                .checked_sub(remaining as usize + 1)
                .and_then(|last_index| {
                    new_steps
                        .iter()
                        .rev()
                        .position(|&step| step <= old_steps[last_index])
                })
                // no last delay or last delay is less than all new steps → all steps remain
                .unwrap_or(new_steps.len())
                // (re)learning card must have at least 1 step remaining
                .max(1) as u32
        };

        (remaining != new_remaining).then_some(new_remaining)
    }

    /// Supposedly unique across all reviews in the collection.
    pub fn review_seed(&self) -> u64 {
        (self.id.0 as u64)
            .rotate_left(8)
            .wrapping_add(self.reps as u64)
    }
}

impl Collection {
    pub(crate) fn update_cards_maybe_undoable(
        &mut self,
        cards: Vec<Card>,
        undoable: bool,
    ) -> Result<OpOutput<()>> {
        if undoable {
            self.transact(Op::UpdateCard, |col| {
                for mut card in cards {
                    let existing = col.storage.get_card(card.id)?.or_not_found(card.id)?;
                    col.update_card_inner(&mut card, existing, col.usn()?)?
                }
                Ok(())
            })
        } else {
            self.transact_no_undo(|col| {
                for mut card in cards {
                    let existing = col.storage.get_card(card.id)?.or_not_found(card.id)?;
                    col.update_card_inner(&mut card, existing, col.usn()?)?;
                }
                Ok(OpOutput {
                    output: (),
                    changes: OpChanges {
                        op: Op::UpdateCard,
                        changes: StateChanges {
                            card: true,
                            ..Default::default()
                        },
                    },
                })
            })
        }
    }

    #[cfg(test)]
    pub(crate) fn get_and_update_card<F, T>(&mut self, cid: CardId, func: F) -> Result<Card>
    where
        F: FnOnce(&mut Card) -> Result<T>,
    {
        let orig = self.storage.get_card(cid)?.or_invalid("no such card")?;
        let mut card = orig.clone();
        func(&mut card)?;
        self.update_card_inner(&mut card, orig, self.usn()?)?;
        Ok(card)
    }

    /// Marks the card as modified, then saves it.
    pub(crate) fn update_card_inner(
        &mut self,
        card: &mut Card,
        original: Card,
        usn: Usn,
    ) -> Result<()> {
        card.set_modified(usn);
        self.update_card_undoable(card, original)
    }

    pub(crate) fn add_card(&mut self, card: &mut Card) -> Result<()> {
        require!(card.id.0 == 0, "card id already set");
        card.mtime = TimestampSecs::now();
        card.usn = self.usn()?;
        self.add_card_undoable(card)
    }

    /// Remove cards and any resulting orphaned notes.
    /// Expects a transaction.
    pub(crate) fn remove_cards_and_orphaned_notes(&mut self, cids: &[CardId]) -> Result<usize> {
        let usn = self.usn()?;
        let mut nids = HashSet::new();
        let mut card_count = 0;
        for cid in cids {
            if let Some(card) = self.storage.get_card(*cid)? {
                nids.insert(card.note_id);
                self.remove_card_and_add_grave_undoable(card, usn)?;
                card_count += 1;
            }
        }
        for nid in nids {
            if self.storage.note_is_orphaned(nid)? {
                self.remove_note_only_undoable(nid, usn)?;
            }
        }

        Ok(card_count)
    }

    pub fn set_deck(&mut self, cards: &[CardId], deck_id: DeckId) -> Result<OpOutput<usize>> {
        let sched = self.scheduler_version();
        if sched == SchedulerVersion::V1 {
            return Err(AnkiError::SchedulerUpgradeRequired);
        }
        let deck = self.get_deck(deck_id)?.or_not_found(deck_id)?;
        let config_id = deck.config_id().ok_or(AnkiError::FilteredDeckError {
            source: FilteredDeckError::CanNotMoveCardsInto,
        })?;
        let config = self.get_deck_config(config_id, true)?.unwrap();
        let fsrs_enabled = self.get_config_bool(BoolKey::Fsrs);
        let mut steps_adjuster = RemainingStepsAdjuster::new(&config);
        let usn = self.usn()?;
        self.transact(Op::SetCardDeck, |col| {
            let mut count = 0;
            for mut card in col.all_cards_for_ids(cards, false)? {
                if card.deck_id == deck_id {
                    continue;
                }
                count += 1;
                let original = card.clone();
                steps_adjuster.adjust_remaining_steps(col, &mut card)?;
                card.set_deck(deck_id);
                col.update_card_inner(&mut card, original, usn)?;
            }
            // Only recompute when at least one card actually moved. Besides
            // avoiding wasted work on a no-op, this prevents a set_deck call on
            // cards already in the target deck from triggering a recompute of
            // *other* memory-state-less cards in that deck.
            if fsrs_enabled && count > 0 {
                let desired_retention = deck.effective_desired_retention(&config);
                let deck_desired_retention = [(deck_id, desired_retention)].into();

                use crate::search::SearchNode::*;

                col.update_memory_state(vec![UpdateMemoryStateEntry {
                    req: Some(UpdateMemoryStateRequest {
                        params: config.fsrs_params().clone(),
                        preset_desired_retention: config.inner.desired_retention,
                        historical_retention: config.inner.historical_retention,
                        max_interval: config.inner.maximum_review_interval,
                        reschedule: false,
                        deck_desired_retention,
                    }),
                    search: SearchBuilder::all(vec![
                        DeckIdsWithoutChildren(deck_id.to_string()).into(),
                        HasMemoryState.negated(),
                    ])
                    .try_into_search()?,
                    ignore_before: ignore_revlogs_before_ms_from_config(&config)?,
                }])?;
            }
            Ok(count)
        })
    }

    pub fn set_card_flag(&mut self, cards: &[CardId], flag: u32) -> Result<OpOutput<usize>> {
        require!(flag < 8, "invalid flag");
        let flag = flag as u8;

        let usn = self.usn()?;
        self.transact(Op::SetFlag, |col| {
            let mut count = 0;
            for mut card in col.all_cards_for_ids(cards, false)? {
                let original = card.clone();
                if card.set_flag(flag) {
                    // To avoid having to rebuild the study queues, we mark the card as requiring
                    // a sync, but do not change its modification time.
                    card.usn = usn;
                    col.update_card_undoable(&mut card, original)?;
                    count += 1;
                }
            }
            Ok(count)
        })
    }

    /// Get deck config for the given card. If missing, return default values.
    #[allow(dead_code)]
    pub(crate) fn deck_config_for_card(&mut self, card: &Card) -> Result<DeckConfig> {
        if let Some(deck) = self.get_deck(card.original_or_current_deck_id())? {
            if let Some(conf_id) = deck.config_id() {
                return Ok(self.get_deck_config(conf_id, true)?.unwrap());
            }
        }

        Ok(DeckConfig::default())
    }

    /// Adjust the remaining steps of the card according to the steps change.
    /// Steps must be learning or relearning steps according to the card's type.
    pub(crate) fn adjust_remaining_steps(
        &mut self,
        card: &mut Card,
        old_steps: &[f32],
        new_steps: &[f32],
        usn: Usn,
    ) -> Result<()> {
        if let Some(new_remaining) = card.new_remaining_steps(new_steps, old_steps) {
            let original = card.clone();
            card.remaining_steps = new_remaining;
            self.update_card_inner(card, original, usn)
        } else {
            Ok(())
        }
    }
}

/// Adjusts the remaining steps of cards after their deck config has changed.
struct RemainingStepsAdjuster<'a> {
    learn_steps: &'a [f32],
    relearn_steps: &'a [f32],
    configs: HashMap<DeckId, DeckConfig>,
}

impl<'a> RemainingStepsAdjuster<'a> {
    fn new(new_config: &'a DeckConfig) -> Self {
        RemainingStepsAdjuster {
            learn_steps: &new_config.inner.learn_steps,
            relearn_steps: &new_config.inner.relearn_steps,
            configs: HashMap::new(),
        }
    }

    fn adjust_remaining_steps(&mut self, col: &mut Collection, card: &mut Card) -> Result<()> {
        if let Some(remaining) = match card.ctype {
            CardType::Learn => card.new_remaining_steps(
                self.learn_steps,
                &self.config_for_card(col, card)?.inner.learn_steps,
            ),
            CardType::Relearn => card.new_remaining_steps(
                self.relearn_steps,
                &self.config_for_card(col, card)?.inner.relearn_steps,
            ),
            _ => None,
        } {
            card.remaining_steps = remaining;
        }
        Ok(())
    }

    fn config_for_card(&mut self, col: &mut Collection, card: &Card) -> Result<&mut DeckConfig> {
        Ok(
            match self.configs.entry(card.original_or_current_deck_id()) {
                Entry::Occupied(e) => e.into_mut(),
                Entry::Vacant(e) => e.insert(col.deck_config_for_card(card)?),
            },
        )
    }
}

impl From<FsrsMemoryState> for MemoryState {
    fn from(value: FsrsMemoryState) -> Self {
        MemoryState {
            stability: value.stability,
            difficulty: value.difficulty,
        }
    }
}

impl From<MemoryState> for FsrsMemoryState {
    fn from(value: MemoryState) -> Self {
        FsrsMemoryState {
            stability: value.stability,
            difficulty: value.difficulty,
        }
    }
}

#[cfg(test)]
mod test {
    use crate::config::BoolKey;
    use crate::prelude::*;
    use crate::tests::open_test_collection_with_learning_card;
    use crate::tests::open_test_collection_with_relearning_card;
    use crate::tests::DeckAdder;

    #[test]
    fn should_increase_remaining_learning_steps_if_new_deck_has_more_unpassed_ones() {
        let mut col = open_test_collection_with_learning_card();
        let deck = DeckAdder::new("target")
            .with_config(|config| config.inner.learn_steps.push(100.))
            .add(&mut col);
        let card_id = col.get_first_card().id;
        col.set_deck(&[card_id], deck.id).unwrap();
        assert_eq!(col.get_first_card().remaining_steps, 3);
    }

    #[test]
    fn should_increase_remaining_relearning_steps_if_new_deck_has_more_unpassed_ones() {
        let mut col = open_test_collection_with_relearning_card();
        let deck = DeckAdder::new("target")
            .with_config(|config| config.inner.relearn_steps.push(100.))
            .add(&mut col);
        let card_id = col.get_first_card().id;
        col.set_deck(&[card_id], deck.id).unwrap();
        assert_eq!(col.get_first_card().remaining_steps, 2);
    }

    #[test]
    fn should_not_recalculate_remaining_steps_if_there_are_no_old_steps() -> Result<(), AnkiError> {
        let mut col = Collection::new();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let card_id = col.get_first_card().id;
        col.set_deck(&[card_id], DeckId(1))?;

        col.set_default_learn_steps(vec![1., 10.]);

        let _post_answer = col.answer_good();

        col.set_default_learn_steps(vec![]);
        col.set_default_learn_steps(vec![1., 10.]);

        assert_eq!(col.get_first_card().remaining_steps, 1);

        Ok(())
    }

    #[test]
    fn recalculate_memory_state_on_set_deck() -> Result<()> {
        let mut col = Collection::new();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        // Graduate card to review without FSRS so it has an interval but no memory
        // state
        col.answer_easy();

        col.set_config_bool(BoolKey::Fsrs, true, false)?;

        let card_id = col.get_first_card().id;
        let card = col.storage.get_card(card_id)?.unwrap();
        assert!(card.memory_state.is_none());
        assert!(card.desired_retention.is_none());
        assert!(card.decay.is_none());

        // Moving the card should trigger FSRS memory state calculation for cards
        // lacking them, and store them in the card.
        let target = DeckAdder::new("target").add(&mut col);
        col.set_deck(&[card_id], target.id)?;

        let card = col.storage.get_card(card_id)?.unwrap();
        assert!(card.memory_state.is_some());
        assert!(card.desired_retention.is_some());
        assert!(card.decay.is_some());

        // The original bug (#5327): moving a card that already has a memory
        // state must recompute it, not leave it cleared.
        let target2 = DeckAdder::new("target2").add(&mut col);
        col.set_deck(&[card_id], target2.id)?;

        assert!(col
            .storage
            .get_card(card_id)?
            .unwrap()
            .memory_state
            .is_some());
        Ok(())
    }

    // Moving cards that are already in the target deck is a no-op and must not
    // trigger a recompute of other memory-state-less cards in that deck.
    #[test]
    fn set_deck_noop_does_not_recompute() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        col.answer_easy(); // review card, still without a memory state
        col.set_config_bool(BoolKey::Fsrs, true, false)?;

        let card_id = col.get_first_card().id;
        // "Move" to the deck it is already in (DeckId(1)) -> count == 0
        col.set_deck(&[card_id], DeckId(1))?;

        assert!(col
            .storage
            .get_card(card_id)?
            .unwrap()
            .memory_state
            .is_none());
        Ok(())
    }

    // The recompute must be scoped to the target deck: a memory-state-less card
    // in a different deck must not be touched.
    #[test]
    fn set_deck_recompute_is_scoped_to_target() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();

        // Card A and card B, both review cards in the default deck.
        let mut a = nt.new_note();
        col.add_note(&mut a, DeckId(1))?;
        col.answer_easy();
        let mut b = nt.new_note();
        col.add_note(&mut b, DeckId(1))?;
        col.answer_easy();

        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        let cards = col.storage.get_all_cards();
        let (a_id, b_id) = (cards[0].id, cards[1].id);

        let target = DeckAdder::new("target").add(&mut col);
        col.set_deck(&[a_id], target.id)?; // only A moves

        assert!(col.storage.get_card(a_id)?.unwrap().memory_state.is_some());
        // B stays in the default deck and must remain without a memory state.
        assert!(col.storage.get_card(b_id)?.unwrap().memory_state.is_none());
        Ok(())
    }

    // A card that already lives in the target deck and has a memory state must
    // not be touched when another card is moved into that deck.
    #[test]
    fn set_deck_does_not_touch_existing_card_in_target_deck() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();

        col.set_config_bool(BoolKey::Fsrs, true, false)?;

        let target = DeckAdder::new("target").add(&mut col);

        let mut target_note = nt.new_note();
        col.add_note(&mut target_note, target.id)?;
        let target_card_id = col.storage.card_ids_of_notes(&[target_note.id]).unwrap()[0];
        col.grade_now(&[target_card_id], 3)?;

        let mut source_note = nt.new_note();
        col.add_note(&mut source_note, DeckId(1))?;
        let source_card_id = col.storage.card_ids_of_notes(&[source_note.id]).unwrap()[0];
        col.grade_now(&[source_card_id], 3)?;

        // Fake a sync after the cards exist, before moving anything.
        col.before_upload()?;

        col.set_deck(&[source_card_id], target.id)?;

        let before = col.storage.get_card(source_card_id)?.unwrap();
        assert!(before.memory_state.is_some());

        let after = col.storage.get_card(target_card_id)?.unwrap();

        assert_eq!(before.usn.0, -1);
        assert_ne!(after.usn.0, -1);
        Ok(())
    }

    // Moving a deck recomputes the memory state but must not reschedule the
    // card (reschedule: false): due date and interval stay unchanged.
    #[test]
    fn set_deck_does_not_reschedule() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        col.answer_easy();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;

        let before = col.get_first_card();
        let target = DeckAdder::new("target").add(&mut col);
        col.set_deck(&[before.id], target.id)?;

        let after = col.storage.get_card(before.id)?.unwrap();
        assert_eq!(after.due, before.due);
        assert_eq!(after.interval, before.interval);
        // The memory state was still recomputed, though.
        assert!(after.memory_state.is_some());
        Ok(())
    }

    // The recompute must use the target preset's parameters, not the source's.
    // Verified through the desired retention stored on the card.
    #[test]
    fn set_deck_uses_target_preset_desired_retention() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        col.answer_easy();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;

        let card_id = col.get_first_card().id;
        // Target deck with a desired retention distinct from the default (0.9).
        let target = DeckAdder::new("target")
            .with_config(|c| c.inner.desired_retention = 0.95)
            .add(&mut col);
        col.set_deck(&[card_id], target.id)?;

        let dr = col.storage.get_card(card_id)?.unwrap().desired_retention;
        assert_eq!(dr, Some(0.95));
        Ok(())
    }

    // With FSRS disabled, moving a card must not compute any memory state.
    #[test]
    fn set_deck_without_fsrs_does_not_compute() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        col.answer_easy(); // review card, FSRS stays disabled

        let card_id = col.get_first_card().id;
        let target = DeckAdder::new("target").add(&mut col);
        col.set_deck(&[card_id], target.id)?;

        assert!(col
            .storage
            .get_card(card_id)?
            .unwrap()
            .memory_state
            .is_none());
        Ok(())
    }

    // New cards have no review history and must never be given a memory state.
    #[test]
    fn set_deck_leaves_new_cards_without_memory_state() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?; // never answered -> still New
        col.set_config_bool(BoolKey::Fsrs, true, false)?;

        let card_id = col.get_first_card().id;
        let target = DeckAdder::new("target").add(&mut col);
        col.set_deck(&[card_id], target.id)?;

        assert!(col
            .storage
            .get_card(card_id)?
            .unwrap()
            .memory_state
            .is_none());
        Ok(())
    }
}
