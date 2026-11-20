// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod card;
mod custom_study;

use std::cmp::Ordering;
use std::hash::Hasher;
use std::time::Instant;

use fnv::FnvHasher;

use crate::card::Card;
use crate::config::ConfigKey;
use crate::config::SchedulerVersion;
use crate::decks::FilteredDeck;
use crate::decks::FilteredSearchOrder;
use crate::decks::FilteredSearchTerm;
use crate::error::FilteredDeckError;
use crate::prelude::*;
use crate::scheduler::fsrs::memory_state::fsrs_current_retrievability_for_state;
use crate::scheduler::fsrs::memory_state::fsrs_relative_overdueness_for_state;
use crate::scheduler::fsrs::preset::FsrsPreset;
use crate::scheduler::timing::SchedTimingToday;
use crate::search::writer::deck_search;
use crate::search::writer::normalize_search;
use crate::search::SortMode;
use crate::storage::card::filtered::order_and_limit_for_search;

/// Contains the parts of a filtered deck required for modifying its settings in
/// the UI.
pub struct FilteredDeckForUpdate {
    pub id: DeckId,
    pub human_name: String,
    pub config: FilteredDeck,
    pub allow_empty: bool,
}

pub(crate) struct DeckFilterContext<'a> {
    pub target_deck: DeckId,
    pub config: &'a FilteredDeck,
    pub usn: Usn,
    pub timing: SchedTimingToday,
}

impl Collection {
    /// Get an existing filtered deck, or create a new one if `deck_id` is 0.
    /// The new deck will not be added to the DB.
    pub fn get_or_create_filtered_deck(
        &mut self,
        deck_id: DeckId,
    ) -> Result<FilteredDeckForUpdate> {
        let deck = if deck_id.0 == 0 {
            self.new_filtered_deck_for_adding()?
        } else {
            self.storage.get_deck(deck_id)?.or_not_found(deck_id)?
        };

        deck.try_into()
    }

    /// If the provided `deck_id` is 0, add provided deck to the DB, and rebuild
    /// it. If the searches are invalid or do not match anything, adding is
    /// aborted. If an existing deck is provided, it will be updated.
    /// Invalid searches or an empty match will abort the update.
    /// Returns the deck_id, which will have changed if the id was 0.
    pub fn add_or_update_filtered_deck(
        &mut self,
        deck: FilteredDeckForUpdate,
    ) -> Result<OpOutput<DeckId>> {
        self.transact(Op::BuildFilteredDeck, |col| {
            col.add_or_update_filtered_deck_inner(deck)
        })
    }

    pub fn empty_filtered_deck(&mut self, did: DeckId) -> Result<OpOutput<()>> {
        self.transact(Op::EmptyFilteredDeck, |col| {
            let deck = col.get_deck(did)?.or_not_found(did)?;
            col.return_all_cards_in_filtered_deck(&deck)
        })
    }

    // Unlike the old Python code, this also marks the cards as modified.
    pub fn rebuild_filtered_deck(&mut self, did: DeckId) -> Result<OpOutput<usize>> {
        self.transact(Op::RebuildFilteredDeck, |col| {
            let deck = col.get_deck(did)?.or_not_found(did)?;
            col.rebuild_filtered_deck_inner(&deck, col.usn()?)
        })
    }
}

impl Collection {
    pub(crate) fn return_all_cards_in_filtered_deck(&mut self, deck: &Deck) -> Result<()> {
        if !deck.is_filtered() {
            return Err(FilteredDeckError::FilteredDeckRequired.into());
        }
        let cids = self.storage.all_cards_in_single_deck(deck.id)?;
        self.return_cards_to_home_deck(&cids)
    }

    // Unlike the old Python code, this also marks the cards as modified.
    fn return_cards_to_home_deck(&mut self, cids: &[CardId]) -> Result<()> {
        let usn = self.usn()?;
        for cid in cids {
            if let Some(mut card) = self.storage.get_card(*cid)? {
                let original = card.clone();
                card.remove_from_filtered_deck_restoring_queue();
                self.update_card_inner(&mut card, original, usn)?;
            }
        }
        Ok(())
    }

    fn build_filtered_deck(&mut self, ctx: DeckFilterContext) -> Result<usize> {
        let start = -100_000;
        let mut position = start;
        let fsrs_enabled = self.fsrs_enabled();
        for term in ctx.config.search_terms.iter().take(2) {
            position = self.move_cards_matching_term(&ctx, term, position, fsrs_enabled)?;
        }

        Ok((position - start) as usize)
    }

    /// Move matching cards into filtered deck.
    /// Returns the new starting position.
    fn move_cards_matching_term(
        &mut self,
        ctx: &DeckFilterContext,
        term: &FilteredSearchTerm,
        mut position: i32,
        fsrs: bool,
    ) -> Result<i32> {
        let search = format!(
            "{} -is:suspended -is:buried -deck:filtered",
            if term.search.trim().is_empty() {
                "".to_string()
            } else {
                format!("({})", term.search)
            }
        );

        if fsrs {
            if let Some(order) = exact_fsrs_search_order(term.order()) {
                return self.move_cards_matching_term_with_exact_fsrs_order(
                    ctx, term, &search, position, order,
                );
            }
        }

        let order = order_and_limit_for_search(term, ctx.timing, fsrs);

        for mut card in self.all_cards_for_search_in_order(&search, SortMode::Custom(order))? {
            let original = card.clone();
            card.move_into_filtered_deck(ctx, position);
            self.update_card_inner(&mut card, original, ctx.usn)?;
            position += 1;
        }

        Ok(position)
    }

    fn move_cards_matching_term_with_exact_fsrs_order(
        &mut self,
        ctx: &DeckFilterContext,
        term: &FilteredSearchTerm,
        search: &str,
        mut position: i32,
        order: ExactFsrsSearchOrder,
    ) -> Result<i32> {
        let cards = self.all_cards_for_search(search)?;
        let card_count = cards.len();
        let preset_start = Instant::now();
        let presets_by_card = self.fsrs_presets_for_cards(&cards)?;
        let preset_elapsed_ms = preset_start.elapsed().as_secs_f64() * 1000.0;
        let key_start = Instant::now();
        let mut cards_with_keys = Vec::with_capacity(card_count);
        for card in cards {
            let preset = presets_by_card
                .get(&card.id)
                .or_invalid("missing FSRS preset for card")?;
            let key = exact_fsrs_search_key_for_card(self, &card, preset, ctx.timing, order)?;
            let hash = fnvhash_card_and_mod(&card);
            cards_with_keys.push((card, key, hash));
        }
        tracing::debug!(
            cards = card_count,
            preset_elapsed_ms,
            key_elapsed_ms = key_start.elapsed().as_secs_f64() * 1000.0,
            "computed exact FSRS filtered deck keys"
        );

        cards_with_keys.sort_unstable_by(|(card_a, key_a, hash_a), (card_b, key_b, hash_b)| {
            let ord = key_a.partial_cmp(key_b).unwrap_or(Ordering::Equal);
            let ord = if order.reverse() { ord.reverse() } else { ord };
            ord.then_with(|| hash_a.cmp(hash_b))
                .then_with(|| card_a.id.cmp(&card_b.id))
        });

        for (mut card, _, _) in cards_with_keys.into_iter().take(term.limit as usize) {
            let original = card.clone();
            card.move_into_filtered_deck(ctx, position);
            self.update_card_inner(&mut card, original, ctx.usn)?;
            position += 1;
        }

        Ok(position)
    }

    fn get_next_filtered_deck_name(&self) -> NativeDeckName {
        NativeDeckName::from_native_str(format!(
            "Filtered Deck {}",
            TimestampSecs::now().time_string()
        ))
    }

    fn add_or_update_filtered_deck_inner(
        &mut self,
        mut update: FilteredDeckForUpdate,
    ) -> Result<DeckId> {
        let usn = self.usn()?;
        let allow_empty = update.allow_empty;

        // check the searches are valid, and normalize them
        for term in &mut update.config.search_terms {
            term.search = normalize_search(&term.search)?
        }

        // add or update the deck
        let mut deck: Deck;
        if update.id.0 == 0 {
            deck = Deck::new_filtered();
            apply_update_to_filtered_deck(&mut deck, update);
            self.add_deck_inner(&mut deck, usn)?;
        } else {
            let original = self.storage.get_deck(update.id)?.or_not_found(update.id)?;
            deck = original.clone();
            apply_update_to_filtered_deck(&mut deck, update);
            self.update_deck_inner(&mut deck, original, usn)?;
        }

        // rebuild it
        let count = self.rebuild_filtered_deck_inner(&deck, usn)?;

        // if it failed to match any cards, we revert the changes
        if count == 0 && !allow_empty {
            Err(FilteredDeckError::SearchReturnedNoCards.into())
        } else {
            // update current deck and return id
            self.set_config(ConfigKey::CurrentDeckId, &deck.id)?;
            Ok(deck.id)
        }
    }

    fn rebuild_filtered_deck_inner(&mut self, deck: &Deck, usn: Usn) -> Result<usize> {
        if self.scheduler_version() == SchedulerVersion::V1 {
            return Err(AnkiError::SchedulerUpgradeRequired);
        }

        let config = deck.filtered()?;
        let timing = self.timing_today()?;
        let ctx = DeckFilterContext {
            target_deck: deck.id,
            config,
            usn,
            timing,
        };

        self.return_all_cards_in_filtered_deck(deck)?;
        self.build_filtered_deck(ctx)
    }

    fn new_filtered_deck_for_adding(&mut self) -> Result<Deck> {
        let mut deck = Deck {
            name: self.get_next_filtered_deck_name(),
            ..Deck::new_filtered()
        };
        if let Some(current) = self.get_deck(self.get_current_deck_id())? {
            if !current.is_filtered() && current.id.0 != 0 {
                // start with a search based on the selected deck name
                let search = deck_search(&current.human_name());
                let term1 = deck
                    .filtered_mut()
                    .unwrap()
                    .search_terms
                    .get_mut(0)
                    .unwrap();
                term1.search = format!("{search} is:due");
                let term2 = deck
                    .filtered_mut()
                    .unwrap()
                    .search_terms
                    .get_mut(1)
                    .unwrap();
                term2.search = format!("{search} is:new");
            }
        }

        Ok(deck)
    }
}

impl TryFrom<Deck> for FilteredDeckForUpdate {
    type Error = AnkiError;

    fn try_from(value: Deck) -> Result<Self, Self::Error> {
        let human_name = value.human_name();
        match value.kind {
            DeckKind::Filtered(filtered) => Ok(FilteredDeckForUpdate {
                id: value.id,
                human_name,
                config: filtered,
                allow_empty: false,
            }),
            _ => invalid_input!("not filtered"),
        }
    }
}

fn apply_update_to_filtered_deck(deck: &mut Deck, update: FilteredDeckForUpdate) {
    deck.id = update.id;
    deck.name = NativeDeckName::from_human_name(&update.human_name);
    deck.kind = DeckKind::Filtered(update.config);
}

#[derive(Clone, Copy)]
enum ExactFsrsSearchOrder {
    Retrievability { reverse: bool },
    RelativeOverdueness,
}

impl ExactFsrsSearchOrder {
    fn reverse(self) -> bool {
        matches!(self, Self::Retrievability { reverse: true })
    }
}

fn exact_fsrs_search_order(order: FilteredSearchOrder) -> Option<ExactFsrsSearchOrder> {
    match order {
        FilteredSearchOrder::RetrievabilityAscending => {
            Some(ExactFsrsSearchOrder::Retrievability { reverse: false })
        }
        FilteredSearchOrder::RetrievabilityDescending => {
            Some(ExactFsrsSearchOrder::Retrievability { reverse: true })
        }
        FilteredSearchOrder::RelativeOverdueness => Some(ExactFsrsSearchOrder::RelativeOverdueness),
        _ => None,
    }
}

fn fnvhash_card_and_mod(card: &Card) -> i64 {
    let mut hasher = FnvHasher::default();
    hasher.write_i64(card.id.0);
    hasher.write_i64(card.mtime.0);
    hasher.finish() as i64
}

fn elapsed_seconds_since_last_review(card: &Card, timing: SchedTimingToday) -> u32 {
    if let Some(last_review_time) = card.last_review_time {
        timing.now.elapsed_secs_since_clamped(last_review_time)
    } else {
        let due = card.original_or_current_due() as i64;
        if due > 365_000 {
            let last_review_time = TimestampSecs(due.saturating_sub(card.interval as i64));
            timing.now.elapsed_secs_since_clamped(last_review_time)
        } else {
            let review_day = due.saturating_sub(card.interval as i64);
            timing.days_elapsed.saturating_sub(review_day as u32) * 86_400
        }
    }
}

fn exact_retrievability_key_for_card(
    col: &Collection,
    card: &Card,
    preset: &FsrsPreset,
    timing: SchedTimingToday,
) -> Result<f32> {
    if let Some(r) = col.rwkv_retrievability_score_for_day(card.id, timing.days_elapsed) {
        return Ok(r);
    }

    if let Some(state) = card.memory_state {
        let elapsed_days = elapsed_seconds_since_last_review(card, timing) as f32 / 86_400.0;
        fsrs_current_retrievability_for_state(&preset.params, state, elapsed_days)
    } else {
        Ok(sm2_relative_overdueness_key(card, timing))
    }
}

fn exact_fsrs_search_key_for_card(
    col: &Collection,
    card: &Card,
    preset: &FsrsPreset,
    timing: SchedTimingToday,
    order: ExactFsrsSearchOrder,
) -> Result<f32> {
    match order {
        ExactFsrsSearchOrder::Retrievability { .. } => {
            exact_retrievability_key_for_card(col, card, preset, timing)
        }
        ExactFsrsSearchOrder::RelativeOverdueness => {
            if let Some(state) = card.memory_state {
                let elapsed_days =
                    elapsed_seconds_since_last_review(card, timing) as f32 / 86_400.0;
                fsrs_relative_overdueness_for_state(
                    &preset.params,
                    state,
                    elapsed_days,
                    card.desired_retention.unwrap_or(preset.desired_retention),
                )
            } else {
                Ok(sm2_relative_overdueness_key(card, timing))
            }
        }
    }
}

fn sm2_relative_overdueness_key(card: &Card, timing: SchedTimingToday) -> f32 {
    let due = card.original_or_current_due() as i64;
    let review_day = due.saturating_sub(card.interval as i64);
    let days_elapsed = if due > 365_000 {
        (timing.next_day_at.0 as u32).saturating_sub(due as u32) / 86_400
    } else {
        timing.days_elapsed.saturating_sub(review_day as u32)
    };
    -((days_elapsed as f32) + 0.001) / (card.interval as f32).max(1.0)
}

#[cfg(test)]
mod test {
    use std::collections::HashMap;

    use anki_proto::deck_config::deck_configs_for_update::current_deck::Limits;
    use anki_proto::deck_config::UpdateDeckConfigsMode;

    use super::*;
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::card::FsrsMemoryState;
    use crate::config::BoolKey;
    use crate::deckconfig::FsrsVersion;
    use crate::deckconfig::UpdateDeckConfigsRequest;
    use crate::decks::FilteredSearchOrder;
    use crate::scheduler::fsrs::preset::AddonFsrsPreset;
    use crate::scheduler::fsrs::preset::AddonFsrsVersion;
    use crate::scheduler::fsrs::preset::FsrsPresetOverlay;
    use crate::scheduler::fsrs::preset::FsrsPresetRule;
    use crate::scheduler::fsrs::preset::FSRS_PRESET_OVERLAY_CONFIG_KEY;

    fn set_selected_fsrs7_params(col: &mut Collection, params: Vec<f32>) -> Result<()> {
        let output = col.get_deck_configs_for_update(DeckId(1))?;
        let mut input = UpdateDeckConfigsRequest {
            target_deck_id: DeckId(1),
            configs: output
                .all_config
                .into_iter()
                .map(|c| c.config.unwrap().into())
                .collect(),
            removed_config_ids: vec![],
            mode: UpdateDeckConfigsMode::Normal,
            card_state_customizer: String::new(),
            limits: Limits::default(),
            new_cards_ignore_review_limit: false,
            apply_all_parent_limits: false,
            fsrs: true,
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
        };
        input.configs[0].inner.fsrs_version = FsrsVersion::Seven as i32;
        input.configs[0].inner.fsrs_params_7 = params;
        col.update_deck_configs(input)?;
        Ok(())
    }

    #[test]
    fn filtered_deck_retrievability_order_uses_exact_model() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        set_selected_fsrs7_params(
            &mut col,
            vec![
                0.4843, 3.0562, 10.9946, 32.7202, 5.6296, 0.5900, 3.1230, 2.4679, 0.2733, 1.4895,
                0.4868, 0.0010, 0.8082, 0.1723, 0.6389, 1.5767, 0.8918, 0.3341, 3.5942, 0.3455,
                0.0022, 0.2834, 2.6418, 0.5604, 1.3042, 2.5054, 0.9376, 0.0611, 0.0830, 0.6339,
                0.9846, 0.2485, 0.6014, 0.0545,
            ],
        )?;

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note1 = nt.new_note();
        let mut note2 = nt.new_note();
        col.add_note(&mut note1, DeckId(1))?;
        col.add_note(&mut note2, DeckId(1))?;
        let mut ids = col.search_cards("", SortMode::NoOrder)?;
        ids.sort();

        let timing = col.timing_today()?;
        let mut card1 = col.storage.get_card(ids[0])?.unwrap();
        let mut card2 = col.storage.get_card(ids[1])?.unwrap();
        for card in [&mut card1, &mut card2] {
            card.ctype = CardType::Review;
            card.queue = CardQueue::Review;
            card.due = 100;
            card.interval = 20;
            card.memory_state = Some(FsrsMemoryState {
                stability: 10.0,
                stability_internal: 10.0,
                stability_fast: Some(20.0),
                difficulty: 5.0,
            });
            card.desired_retention = Some(0.8);
            card.last_review_time = Some(timing.now.adding_secs(-20 * 86_400));
        }
        card2.memory_state = Some(FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: Some(5.0),
            difficulty: 8.0,
        });
        card1.decay = Some(0.1);
        card2.decay = Some(2.0);
        col.storage.update_card(&card1)?;
        col.storage.update_card(&card2)?;
        let presets = col.fsrs_presets_for_cards(&[card1.clone(), card2.clone()])?;
        let key1 = exact_retrievability_key_for_card(&col, &card1, &presets[&card1.id], timing)?;
        let key2 = exact_retrievability_key_for_card(&col, &card2, &presets[&card2.id], timing)?;
        assert_ne!(key1, key2);

        let mut deck = col.get_or_create_filtered_deck(DeckId(0))?;
        deck.allow_empty = true;
        deck.config.search_terms[0].search = "is:review".into();
        deck.config.search_terms[0].limit = 2;
        deck.config.search_terms[0].order = FilteredSearchOrder::RetrievabilityAscending as i32;
        deck.config.search_terms[1].search = String::new();
        deck.config.search_terms[1].limit = 0;
        let filtered_did = col.add_or_update_filtered_deck(deck)?.output;

        let mut filtered_cards = col
            .storage
            .all_cards_in_single_deck(filtered_did)?
            .into_iter()
            .map(|cid| {
                let card = col.storage.get_card(cid)?.or_not_found(cid)?;
                Ok(card)
            })
            .collect::<Result<Vec<_>>>()?;
        filtered_cards.sort_by_key(|card| card.due);
        let ordered_ids: Vec<_> = filtered_cards.into_iter().map(|card| card.id).collect();

        let mut expected_order = vec![
            (card1.id, key1, fnvhash_card_and_mod(&card1)),
            (card2.id, key2, fnvhash_card_and_mod(&card2)),
        ];
        expected_order.sort_unstable_by(|(id_a, key_a, hash_a), (id_b, key_b, hash_b)| {
            key_a
                .partial_cmp(key_b)
                .unwrap_or(Ordering::Equal)
                .then_with(|| hash_a.cmp(hash_b))
                .then_with(|| id_a.cmp(id_b))
        });
        let expected_ids: Vec<_> = expected_order.into_iter().map(|(id, _, _)| id).collect();
        assert_eq!(ordered_ids, expected_ids);
        Ok(())
    }

    #[test]
    fn filtered_deck_retrievability_order_uses_overlay_preset_params() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut overlay_params = fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec();
        overlay_params[20] = 0.8;
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:test:overlay".into(),
                    name: "Overlay".into(),
                    fsrs_version: AddonFsrsVersion::Six,
                    params: overlay_params.clone(),
                    desired_retention: 0.9,
                    historical_retention: 0.9,
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: "front:overlay".into(),
                    preset_id: "addon:test:overlay".into(),
                }],
                simulator_rules: Vec::new(),
            },
        )?;
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut default_note = nt.new_note();
        let mut overlay_note = nt.new_note();
        default_note.set_field(0, "default")?;
        overlay_note.set_field(0, "overlay")?;
        col.add_note(&mut default_note, DeckId(1))?;
        col.add_note(&mut overlay_note, DeckId(1))?;
        let mut default_card = col
            .storage
            .get_card_by_ordinal(default_note.id, 0)?
            .unwrap();
        let mut overlay_card = col
            .storage
            .get_card_by_ordinal(overlay_note.id, 0)?
            .unwrap();
        let timing = SchedTimingToday {
            now: TimestampSecs(40 * 86_400),
            days_elapsed: 40,
            next_day_at: TimestampSecs(41 * 86_400),
        };
        let state = FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: None,
            difficulty: 5.0,
        };
        let default_preset = col.fsrs_preset_for_card(&default_card)?;
        let overlay_key = fsrs_current_retrievability_for_state(&overlay_params, state, 20.0)?;
        let fallback_key =
            fsrs_current_retrievability_for_state(&default_preset.params, state, 20.0)?;
        // Put the default card between the overlay and fallback scores, so ignoring
        // the overlay reverses the order instead of merely changing a tie.
        let default_elapsed = default_preset
            .fsrs()?
            .interval_at_retrievability(state.into(), (overlay_key + fallback_key) / 2.0);
        for (card, elapsed) in [
            (
                &mut default_card,
                (default_elapsed * 86_400.0).round() as i64,
            ),
            (&mut overlay_card, 20 * 86_400),
        ] {
            card.ctype = CardType::Review;
            card.queue = CardQueue::Review;
            card.due = 40;
            card.interval = 20;
            card.memory_state = Some(state);
            card.last_review_time = Some(timing.now.adding_secs(-elapsed));
            col.storage.update_card(card)?;
        }
        let default_key = fsrs_current_retrievability_for_state(
            &default_preset.params,
            state,
            elapsed_seconds_since_last_review(&default_card, timing) as f32 / 86_400.0,
        )?;
        assert!(default_key > overlay_key.min(fallback_key));
        assert!(default_key < overlay_key.max(fallback_key));
        let expected_ids = if overlay_key > default_key {
            vec![overlay_card.id, default_card.id]
        } else {
            vec![default_card.id, overlay_card.id]
        };
        let mut deck = Deck::new_filtered();
        deck.filtered_mut()?.search_terms = vec![FilteredSearchTerm {
            search: "is:review".into(),
            limit: 2,
            order: FilteredSearchOrder::RetrievabilityDescending as i32,
        }];
        col.add_deck(&mut deck)?;
        let ctx = DeckFilterContext {
            target_deck: deck.id,
            config: deck.filtered()?,
            usn: col.usn()?,
            timing,
        };

        assert_eq!(col.build_filtered_deck(ctx)?, 2);

        let mut cards = col.all_cards_for_search("deck:filtered")?;
        cards.sort_by_key(|card| card.due);
        assert_eq!(
            cards.iter().map(|card| card.id).collect::<Vec<_>>(),
            expected_ids
        );
        assert_eq!(
            cards.iter().map(|card| card.due).collect::<Vec<_>>(),
            vec![-100_000, -99_999]
        );
        Ok(())
    }

    #[test]
    fn filtered_relative_overdueness_uses_exact_target_interval() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let timing = col.timing_today()?;
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let mut card = col.storage.get_card_by_ordinal(note.id, 0)?.unwrap();
        card.memory_state = Some(FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: Some(5.0),
            difficulty: 8.0,
        });
        card.last_review_time = Some(timing.now.adding_secs(-20 * 86_400));
        let preset = col.fsrs_preset_for_card(&card)?;

        card.desired_retention = Some(0.8);
        let first = exact_fsrs_search_key_for_card(
            &col,
            &card,
            &preset,
            timing,
            ExactFsrsSearchOrder::RelativeOverdueness,
        )?;
        card.desired_retention = Some(0.95);
        let second = exact_fsrs_search_key_for_card(
            &col,
            &card,
            &preset,
            timing,
            ExactFsrsSearchOrder::RelativeOverdueness,
        )?;

        assert_ne!(first, second);
        assert!(matches!(
            exact_fsrs_search_order(FilteredSearchOrder::RelativeOverdueness),
            Some(ExactFsrsSearchOrder::RelativeOverdueness)
        ));
        Ok(())
    }

    #[test]
    fn filtered_deck_retrievability_order_prefers_rwkv_score() -> Result<()> {
        let mut col = Collection::new();
        let timing = col.timing_today()?;
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 30;
        card.memory_state = Some(FsrsMemoryState {
            stability: 30.0,
            stability_internal: 30.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        col.add_card(&mut card)?;
        col.set_rwkv_deck_count_scores(DeckId(1), HashMap::from([(card.id, 0.42)]))?;
        let preset = col.fsrs_preset_for_card(&card)?;

        assert_eq!(
            exact_retrievability_key_for_card(&col, &card, &preset, timing)?,
            0.42
        );
        Ok(())
    }
}
