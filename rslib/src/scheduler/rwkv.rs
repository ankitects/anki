// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::hash_map::Entry;
use std::collections::HashMap;
use std::collections::HashSet;

use anki_proto::scheduler;
use anki_proto::scheduler::RwkvHistoricalReviewFingerprintRequest;
use anki_proto::scheduler::RwkvHistoricalReviewFingerprintResponse;
use anki_proto::scheduler::RwkvReviewInputRowsForCardsRequest;
use anki_proto::scheduler::RwkvReviewInputRowsForCardsResponse;
use anki_proto::scheduler::RwkvReviewInputRowsForDeckReviewQueueRequest;
use anki_proto::scheduler::RwkvReviewInputRowsForSearchRequest;
use blake2::digest::consts::U8;
use blake2::digest::Digest as Blake2Digest;
use blake2::Blake2b;
use sha2::Digest;
use sha2::Sha256;

use crate::card::Card;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::card::FsrsMemoryState;
use crate::deckconfig::DeckConfig;
use crate::deckconfig::DeckConfigId;
use crate::decks::Deck;
use crate::decks::DeckId;
use crate::ops::Op;
use crate::prelude::*;
use crate::scheduler::addon_desired_retention::AddonRetentionRules;
use crate::scheduler::fsrs::memory_state::fsrs_memory_state_for_s90;
use crate::scheduler::fsrs::preset::FsrsPreset;
use crate::scheduler::fsrs::preset::FsrsPresetId;
use crate::scheduler::timing::SchedTimingToday;
use crate::search::parse_search;
use crate::search::Node;
use crate::search::PropertyKind;
use crate::search::SearchNode;
use crate::search::SortMode;
use crate::search::StateKind;
use crate::storage::RwkvHistoricalReviewRow;

mod history;
mod offline;
mod offline_engine;
mod offline_state;

pub(crate) use offline::RwkvOfflineRuntime;

const RWKV_HISTORY_HASH_DOMAIN: &[u8] = b"anki-rwkv-state-cache-history-v1\0";

#[derive(Default)]
struct RwkvHistoricalReplayOptions {
    preserved_learning_start_cutoffs: HashMap<i64, i64>,
    first_review_uses_creation: Option<bool>,
    recovery_checkpoint_max_age_millis: Option<i64>,
    /// Visit only this card's reviews (the hash then covers only those).
    card_id: Option<CardId>,
}

pub(crate) struct RwkvReviewRescheduleItem {
    pub(crate) card_id: CardId,
    pub(crate) interval_days: u32,
    pub(crate) elapsed_days: u32,
    pub(crate) s90: f32,
    pub(crate) target_retention: Option<f32>,
}

impl Collection {
    pub(crate) fn rwkv_historical_review_fingerprint(
        &mut self,
        input: RwkvHistoricalReviewFingerprintRequest,
    ) -> Result<RwkvHistoricalReviewFingerprintResponse> {
        self.visit_rwkv_historical_reviews(input, Default::default(), |_, _, _| {})
    }

    /// Share canonical replay semantics between cache validation and rebuilds.
    /// The visitor receives each retained review after hashing it, so
    /// checkpoint metadata and its identity describe exactly the same
    /// chronological prefix.
    fn visit_rwkv_historical_reviews(
        &mut self,
        input: RwkvHistoricalReviewFingerprintRequest,
        options: RwkvHistoricalReplayOptions,
        visit: impl FnMut(RwkvHistoricalFingerprintReview, [u8; 32], bool),
    ) -> Result<RwkvHistoricalReviewFingerprintResponse> {
        self.read_rwkv_historical_reviews(input, options)?
            .walk(visit)
    }

    /// The collection reads of `visit_rwkv_historical_reviews`. Walking what
    /// they return needs no collection: kuma3's phone build does it on the
    /// RWKV build thread, so a 4M-review history doesn't hold the collection.
    fn read_rwkv_historical_reviews(
        &mut self,
        mut input: RwkvHistoricalReviewFingerprintRequest,
        options: RwkvHistoricalReplayOptions,
    ) -> Result<RwkvHistoricalReviews> {
        let mut ignored_review_ids = std::mem::take(&mut input.ignored_review_ids)
            .into_iter()
            .map(RevlogId)
            .collect::<Vec<_>>();
        ignored_review_ids.sort_unstable();
        ignored_review_ids.dedup();
        let (rows, active_ignored_review_ids, revlog_scan) =
            self.storage.rwkv_historical_review_rows_with_cutoffs(
                &ignored_review_ids,
                &options.preserved_learning_start_cutoffs,
                options.card_id,
            )?;
        let timing = self.timing_today()?;

        // Only an add-on overlay rule moves a card off its home deck's preset;
        // without one, `walk` resolves the ids per home deck and no card is
        // loaded.
        let has_overlay_rules = self.fsrs_preset_overlay_has_rules()?;
        let card_ids = if has_overlay_rules || input.dynamic_preset_replay {
            rows.iter()
                .map(|row| CardId(row.card_id))
                .collect::<HashSet<_>>()
        } else {
            HashSet::new()
        };
        let stable_preset_ids_by_card = if has_overlay_rules {
            let cards =
                self.all_cards_for_ids(&card_ids.iter().copied().collect::<Vec<_>>(), false)?;
            let presets_by_card = self.fsrs_presets_for_cards(&cards)?;
            Some(
                presets_by_card
                    .into_iter()
                    .map(|(card_id, preset)| {
                        Ok((
                            card_id,
                            rwkv_stable_preset_id(&preset.id, &input.stable_preset_ids)?,
                        ))
                    })
                    .collect::<Result<HashMap<_, _>>>()?,
            )
        } else {
            None
        };
        let preset_routes = if input.dynamic_preset_replay {
            self.rwkv_historical_preset_routes(&card_ids, &input.stable_preset_ids)?
        } else {
            Vec::new()
        };
        let decks_by_id = self.storage.get_decks_map()?;
        let configs_by_id = self.storage.get_deck_config_map()?;
        Ok(RwkvHistoricalReviews {
            input,
            options,
            ignored_review_ids,
            rows,
            active_ignored_review_ids,
            revlog_scan,
            timing,
            stable_preset_ids_by_card,
            preset_routes,
            decks_by_id,
            configs_by_id,
        })
    }
}

/// A review history as read from the collection
/// (`read_rwkv_historical_reviews`).
struct RwkvHistoricalReviews {
    input: RwkvHistoricalReviewFingerprintRequest,
    options: RwkvHistoricalReplayOptions,
    ignored_review_ids: Vec<RevlogId>,
    rows: Vec<RwkvHistoricalReviewRow>,
    active_ignored_review_ids: Vec<i64>,
    /// Revlog rows read for `rows`, and the newest one's id (all of revlog
    /// for the whole history without ignored ids).
    revlog_scan: (u64, i64),
    timing: SchedTimingToday,
    /// None: every card's preset is its home deck's.
    stable_preset_ids_by_card: Option<HashMap<CardId, i64>>,
    preset_routes: Vec<RwkvHistoricalPresetRoute>,
    decks_by_id: HashMap<DeckId, Deck>,
    configs_by_id: HashMap<DeckConfigId, DeckConfig>,
}

impl RwkvHistoricalReviews {
    fn walk(
        self,
        mut visit: impl FnMut(RwkvHistoricalFingerprintReview, [u8; 32], bool),
    ) -> Result<RwkvHistoricalReviewFingerprintResponse> {
        let started = std::time::Instant::now();
        let RwkvHistoricalReviews {
            input,
            options,
            ignored_review_ids,
            rows,
            active_ignored_review_ids,
            revlog_scan: _,
            timing,
            stable_preset_ids_by_card,
            preset_routes,
            decks_by_id,
            configs_by_id,
        } = self;
        let queried_review_count = rows.len() as u64;
        let checkpoint_review_count = options.recovery_checkpoint_max_age_millis.and_then(|age| {
            let cutoff = rows.last()?.review_id - age;
            // Match the desktop checkpoint: retain at least one review in the
            // prefix, and leave at least one review for recovery replay.
            let count = rows.partition_point(|row| row.review_id <= cutoff).max(1);
            (count < rows.len()).then_some(count)
        });

        let mut previous_review_id_by_card = HashMap::new();
        let mut stable_preset_ids_by_deck = HashMap::new();
        let mut previous_interval_days_by_card = HashMap::new();
        let mut review_count_by_card = HashMap::new();
        let mut history_hash = rwkv_empty_history_hash();
        let mut last_review_id = 0;
        let expected_prefix_len = input
            .expected_identity
            .as_ref()
            .map(|expected| expected.review_count)
            .filter(|&count| count > 0 && count < queried_review_count);
        let mut prefix_matches_expected = false;

        for (index, row) in rows.into_iter().enumerate() {
            if Some(index as u64) == expected_prefix_len {
                // The first review after the prefix must be strictly newer, as
                // incremental replay resumes after the prefix's last review id.
                prefix_matches_expected = prefix_matches_expected && row.review_id > last_review_id;
            }
            let card_id = CardId(row.card_id);
            let day_offset = rwkv_historical_day_offset(row.review_id, &timing);
            let previous_review_id = previous_review_id_by_card.insert(card_id, row.review_id);
            let (elapsed_days, elapsed_seconds) = if let Some(previous_review_id) =
                previous_review_id
            {
                (
                    (day_offset - rwkv_historical_day_offset(previous_review_id, &timing)).max(0),
                    ((row.review_id - previous_review_id) / 1000).max(0),
                )
            } else if row.is_learning_start
                && options.first_review_uses_creation.unwrap_or_else(|| {
                    rwkv_first_review_uses_card_creation(
                        row.deck_id,
                        &decks_by_id,
                        &configs_by_id,
                        &input.first_review_uses_creation_by_config_id,
                    )
                })
            {
                let elapsed_seconds = ((row.review_id - row.card_id) / 1000).max(0);
                (elapsed_seconds / 86_400, elapsed_seconds)
            } else {
                (-1, -1)
            };
            let review_count_so_far = *review_count_by_card.get(&card_id).unwrap_or(&0);
            let previous_interval_days =
                *previous_interval_days_by_card.get(&card_id).unwrap_or(&0);
            let stable_preset_id = match preset_routes
                .iter()
                .find(|route| route.matches(card_id, review_count_so_far, previous_interval_days))
            {
                Some(route) => route.stable_preset_id,
                None => match &stable_preset_ids_by_card {
                    Some(by_card) => by_card
                        .get(&card_id)
                        .copied()
                        .or_invalid("missing stable RWKV preset id")?,
                    None => match stable_preset_ids_by_deck.entry(row.deck_id) {
                        Entry::Occupied(entry) => *entry.get(),
                        Entry::Vacant(entry) => *entry.insert(rwkv_home_deck_stable_preset_id(
                            DeckId(row.deck_id),
                            &decks_by_id,
                            &configs_by_id,
                            &input.stable_preset_ids,
                        )?),
                    },
                },
            };

            previous_interval_days_by_card.insert(card_id, row.interval_days);
            review_count_by_card.insert(card_id, review_count_so_far + 1);
            last_review_id = last_review_id.max(row.review_id);
            let review = RwkvHistoricalFingerprintReview {
                row,
                stable_preset_id,
                day_offset,
                elapsed_days,
                elapsed_seconds,
            };
            history_hash = rwkv_history_hash_after_review(history_hash, &review);
            visit(
                review,
                history_hash,
                Some(index + 1) == checkpoint_review_count,
            );
            if Some(index as u64 + 1) == expected_prefix_len {
                prefix_matches_expected =
                    input.expected_identity.as_ref().is_some_and(|expected| {
                        expected.last_review_id == last_review_id
                            && expected.history_hash == rwkv_history_hash_hex(history_hash)
                    });
            }
        }

        tracing::debug!(
            reviews = queried_review_count,
            elapsed_ms = started.elapsed().as_secs_f64() * 1000.0,
            "computed RWKV historical review fingerprint in Rust"
        );
        let history_hash = rwkv_history_hash_hex(history_hash);
        let all_ignored_review_ids_are_active = ignored_review_ids
            .iter()
            .map(|review_id| review_id.0)
            .eq(active_ignored_review_ids.iter().copied());
        let history_is_valid = all_ignored_review_ids_are_active
            && input.expected_identity.is_some_and(|expected| {
                expected.last_review_id == last_review_id
                    && expected.review_count == queried_review_count
                    && expected.history_hash == history_hash
            });
        let history_prefix_is_valid = all_ignored_review_ids_are_active && prefix_matches_expected;
        Ok(RwkvHistoricalReviewFingerprintResponse {
            last_review_id,
            review_count: queried_review_count,
            history_hash,
            active_ignored_review_ids,
            queried_review_count,
            history_is_valid,
            history_prefix_is_valid,
        })
    }
}

impl Collection {
    fn rwkv_historical_preset_routes(
        &mut self,
        included_card_ids: &HashSet<CardId>,
        stable_preset_ids: &HashMap<String, i64>,
    ) -> Result<Vec<RwkvHistoricalPresetRoute>> {
        let mut search_cache: HashMap<String, HashSet<CardId>> = HashMap::new();
        self.fsrs_preset_simulator_rules()?
            .into_iter()
            .map(|(rule, preset)| {
                let card_ids = match rule
                    .search
                    .as_deref()
                    .map(str::trim)
                    .filter(|search| !search.is_empty())
                {
                    Some(search) => {
                        if let Some(card_ids) = search_cache.get(search) {
                            Some(card_ids.clone())
                        } else {
                            let card_ids = self
                                .search_cards(search, SortMode::NoOrder)?
                                .into_iter()
                                .filter(|card_id| included_card_ids.contains(card_id))
                                .collect::<HashSet<_>>();
                            search_cache.insert(search.to_string(), card_ids.clone());
                            Some(card_ids)
                        }
                    }
                    None => None,
                };
                Ok(RwkvHistoricalPresetRoute {
                    stable_preset_id: rwkv_stable_preset_id(&preset.id, stable_preset_ids)?,
                    card_ids,
                    min_reps: rule.min_reps,
                    max_reps: rule.max_reps,
                    min_interval_days: rule.min_interval_days,
                    max_interval_days: rule.max_interval_days,
                })
            })
            .collect()
    }

    pub(crate) fn apply_rwkv_review_reschedule(
        &mut self,
        items: Vec<RwkvReviewRescheduleItem>,
    ) -> Result<OpOutput<usize>> {
        let timing = self.timing_today()?;
        let usn = self.usn()?;

        self.transact(Op::Custom("RWKV reschedule".into()), |col| {
            let mut updated = 0;
            for item in items {
                require!(item.interval_days >= 1, "invalid RWKV interval");
                require!(item.s90.is_finite() && item.s90 > 0.0, "invalid RWKV S90");
                if let Some(target_retention) = item.target_retention {
                    require!(
                        valid_card_desired_retention(target_retention),
                        "invalid RWKV target retention"
                    );
                }

                let Some(mut card) = col.storage.get_card(item.card_id)? else {
                    continue;
                };
                if !(card.ctype == CardType::Review && card.queue == CardQueue::Review) {
                    continue;
                }

                let original = card.clone();
                card.interval = item.interval_days;
                card.memory_state = Some(if card.memory_state.is_some() {
                    rwkv_rescheduled_memory_state(&card, item.s90)
                } else {
                    let preset = col.fsrs_preset_for_card(&card)?;
                    fsrs_memory_state_for_s90(&preset.params, item.s90)?
                });
                if let Some(target_retention) = item.target_retention {
                    card.desired_retention = Some(target_retention);
                }

                let due = if card.original_due != 0 {
                    &mut card.original_due
                } else {
                    &mut card.due
                };
                *due = rwkv_rescheduled_due_day(
                    timing.days_elapsed,
                    item.elapsed_days,
                    item.interval_days,
                );

                col.update_card_inner(&mut card, original, usn)?;
                updated += 1;
            }

            Ok(updated)
        })
    }

    pub(crate) fn rwkv_review_input_rows_for_cards(
        &mut self,
        input: RwkvReviewInputRowsForCardsRequest,
    ) -> Result<RwkvReviewInputRowsForCardsResponse> {
        let mut card_ids: Vec<CardId> = input.card_ids.into_iter().map(Into::into).collect();
        if card_ids.is_empty() {
            return Ok(RwkvReviewInputRowsForCardsResponse::default());
        }
        // search_cids is unique: a repeated id (e.g. a just-answered card that
        // is also among an approximate update's top cards) would fail the query
        let mut seen = HashSet::new();
        card_ids.retain(|id| seen.insert(*id));

        let timing = self.timing_today()?;
        let decks_by_id = self.storage.get_decks_map()?;
        let configs_by_id = self.storage.get_deck_config_map()?;
        let enabled_deck_ids = (!input.include_disabled_decks)
            .then(|| rwkv_enabled_deck_ids(&decks_by_id, &configs_by_id));
        let cards = self.storage.rwkv_review_input_candidate_cards_for_ids(
            &card_ids,
            input.include_suspended_review,
            input.include_new_cards,
            enabled_deck_ids.as_ref(),
        )?;
        let mut response = self.rwkv_review_input_rows_from_cards(
            cards,
            timing,
            &decks_by_id,
            &configs_by_id,
            RwkvReviewInputRowOptions {
                include_suspended_review: input.include_suspended_review,
                include_disabled_decks: input.include_disabled_decks,
                include_comparison_data: false,
            },
        )?;
        response.searched_cards = card_ids.len() as u32;
        Ok(response)
    }

    pub(crate) fn rwkv_review_input_rows_for_search(
        &mut self,
        input: RwkvReviewInputRowsForSearchRequest,
    ) -> Result<RwkvReviewInputRowsForCardsResponse> {
        let timing = self.timing_today()?;
        let decks_by_id = self.storage.get_decks_map()?;
        let configs_by_id = self.storage.get_deck_config_map()?;
        let enabled_deck_ids = (!input.include_disabled_decks)
            .then(|| rwkv_enabled_deck_ids(&decks_by_id, &configs_by_id));
        let parsed_search = parse_search(&input.search)?;
        let candidate_search =
            broaden_retrievability_properties(Node::Group(parsed_search.clone()), false);
        let guard = self.search_cards_into_table(candidate_search, SortMode::NoOrder)?;
        let searched_cards = guard.cards as u32;
        let include_new_cards =
            input.include_new_cards || search_explicitly_includes_new_cards(&parsed_search);
        let cards = guard
            .col
            .storage
            .rwkv_review_input_candidate_cards_in_search(
                input.include_suspended_review,
                include_new_cards,
                enabled_deck_ids.as_ref(),
            )?;
        let mut response = guard.col.rwkv_review_input_rows_from_cards(
            cards,
            timing,
            &decks_by_id,
            &configs_by_id,
            RwkvReviewInputRowOptions {
                include_suspended_review: input.include_suspended_review,
                include_disabled_decks: input.include_disabled_decks,
                include_comparison_data: input.include_comparison_data,
            },
        )?;
        response.searched_cards = searched_cards;
        Ok(response)
    }

    pub(crate) fn rwkv_review_input_rows_for_deck_review_queue(
        &mut self,
        input: RwkvReviewInputRowsForDeckReviewQueueRequest,
    ) -> Result<RwkvReviewInputRowsForCardsResponse> {
        let deck_id = DeckId(input.deck_id);
        let Some(deck) = self.get_deck(deck_id)? else {
            return Ok(RwkvReviewInputRowsForCardsResponse::default());
        };
        let deck_ids = self.storage.deck_id_with_children(deck.as_ref())?;
        let timing = self.timing_today()?;
        let decks_by_id = self.storage.get_decks_map()?;
        let configs_by_id = self.storage.get_deck_config_map()?;
        let enabled_deck_ids = (!input.include_disabled_decks)
            .then(|| rwkv_enabled_deck_ids(&decks_by_id, &configs_by_id));
        let (searched_cards, cards) = self
            .storage
            .rwkv_review_input_candidate_cards_for_deck_review_queue(
                &deck_ids,
                enabled_deck_ids.as_ref(),
                input.include_new_cards,
            )?;
        let mut response = self.rwkv_review_input_rows_from_cards(
            cards,
            timing,
            &decks_by_id,
            &configs_by_id,
            RwkvReviewInputRowOptions {
                include_suspended_review: false,
                include_disabled_decks: input.include_disabled_decks,
                include_comparison_data: false,
            },
        )?;
        response.searched_cards = searched_cards;
        Ok(response)
    }

    fn rwkv_review_input_rows_from_cards(
        &mut self,
        mut cards: Vec<Card>,
        timing: SchedTimingToday,
        decks_by_id: &HashMap<DeckId, Deck>,
        configs_by_id: &HashMap<DeckConfigId, DeckConfig>,
        options: RwkvReviewInputRowOptions,
    ) -> Result<RwkvReviewInputRowsForCardsResponse> {
        self.populate_rwkv_last_review_times(&mut cards)?;

        let mut deck_config_decks = HashSet::new();
        let mut cards_with_supported_state = 0;
        let mut disabled_config_cards = 0;
        let mut eligible = Vec::new();
        let loaded_cards = cards.len() as u32;

        for card in cards {
            let Some(state) = self.rwkv_review_input_state(
                &card,
                timing,
                options.include_suspended_review,
                true,
            )?
            else {
                continue;
            };
            cards_with_supported_state += 1;

            let current_deck_id = card.original_deck_id.or(card.deck_id);
            deck_config_decks.insert(current_deck_id);
            let Some(deck) = decks_by_id.get(&current_deck_id) else {
                continue;
            };
            let Some(config_id) = deck.config_id() else {
                continue;
            };
            let Some(config) = configs_by_id.get(&config_id) else {
                continue;
            };
            if !rwkv_config_active(config) && !options.include_disabled_decks {
                disabled_config_cards += 1;
                continue;
            }
            eligible.push(RwkvReviewInputRowPartial {
                target_retention: deck.effective_desired_retention(config),
                batch_size: config.inner.rwkv_review_batch_size,
                enforce_grade_order: config.inner.rwkv_review_enforce_grade_order,
                card,
                current_deck_id,
                state,
            });
        }

        let preset_cards: Vec<_> = eligible
            .iter()
            .map(|partial| partial.card.clone())
            .collect();
        let presets_by_card = self.fsrs_presets_for_cards(&preset_cards)?;
        let addon_targets = if let Some(rules) = AddonRetentionRules::from_collection(self) {
            rules.matches(self, &preset_cards)?
        } else {
            HashMap::new()
        };
        let rows = eligible
            .into_iter()
            .filter_map(|partial| {
                let preset = presets_by_card.get(&partial.card.id)?;
                Some(scheduler::rwkv_review_input_rows_for_cards_response::Row {
                    card_id: partial.card.id.0,
                    note_id: partial.card.note_id.0,
                    deck_id: partial.current_deck_id.0,
                    preset_id: rwkv_fsrs_preset_id_to_string(preset.id.clone()),
                    card_type: partial.card.ctype as i32,
                    card_queue: partial.card.queue as i32,
                    card_due: partial.card.due,
                    interval_days: partial.card.interval,
                    ease_factor: partial.card.ease_factor.into(),
                    reps: partial.card.reps,
                    lapses: partial.card.lapses,
                    day_offset: timing.days_elapsed,
                    current_state_kind: partial.state.state_kind,
                    current_normal_state_kind: partial.state.normal_state_kind,
                    current_elapsed_days: partial.state.elapsed_days,
                    current_elapsed_seconds: partial.state.elapsed_seconds,
                    current_elapsed_since_secs: partial.state.elapsed_since.map(|time| time.0),
                    remaining_steps: Some(partial.card.remaining_steps()),
                    in_filtered_deck: partial.card.original_deck_id.0 != 0,
                    target_retention: valid_rwkv_target_retention(
                        addon_targets
                            .get(&partial.card.id)
                            .map(|result| result.retention)
                            .unwrap_or(partial.target_retention),
                    ),
                    batch_size: partial.batch_size,
                    enforce_grade_order: Some(partial.enforce_grade_order),
                    fsrs_memory_state: options
                        .include_comparison_data
                        .then(|| partial.card.memory_state.map(Into::into))
                        .flatten(),
                    fsrs_parameters: if options.include_comparison_data {
                        preset.params.clone()
                    } else {
                        Vec::new()
                    },
                    fsrs_historical_retention: options
                        .include_comparison_data
                        .then_some(preset.historical_retention),
                })
            })
            .collect();

        Ok(RwkvReviewInputRowsForCardsResponse {
            rows,
            loaded_cards,
            cards_with_supported_state,
            disabled_config_cards,
            deck_configs: deck_config_decks.len() as u32,
            searched_cards: 0,
        })
    }

    fn rwkv_review_input_state(
        &self,
        card: &Card,
        timing: SchedTimingToday,
        include_suspended_review: bool,
        first_review_elapsed_from_card_creation: bool,
    ) -> Result<Option<RwkvReviewInputState>> {
        match (card.ctype, card.queue) {
            (CardType::New, CardQueue::New) => {
                let elapsed_since =
                    first_review_elapsed_from_card_creation.then(|| card.id.as_secs());
                let elapsed_seconds =
                    elapsed_since.map(|since| timing.now.elapsed_secs_since_clamped(since));
                Ok(Some(RwkvReviewInputState {
                    state_kind: "normal".to_string(),
                    normal_state_kind: "new".to_string(),
                    elapsed_days: elapsed_seconds.map(|elapsed_seconds| elapsed_seconds / 86_400),
                    elapsed_seconds,
                    elapsed_since,
                }))
            }
            (CardType::Review, CardQueue::Review | CardQueue::Suspended) => {
                if card.queue == CardQueue::Suspended && !include_suspended_review {
                    return Ok(None);
                }

                let last_review_time = self.rwkv_last_review_time(card)?;
                let elapsed_days = last_review_time.map(|last_review_time| {
                    timing
                        .next_day_at
                        .elapsed_days_since_clamped(last_review_time)
                });
                let elapsed_seconds = last_review_time.map(|last_review_time| {
                    TimestampSecs::now().elapsed_secs_since_clamped(last_review_time)
                });
                Ok(Some(RwkvReviewInputState {
                    state_kind: elapsed_days
                        .map(|_| "normal".to_string())
                        .unwrap_or_default(),
                    normal_state_kind: elapsed_days
                        .map(|_| "review".to_string())
                        .unwrap_or_default(),
                    elapsed_days,
                    elapsed_seconds,
                    elapsed_since: last_review_time,
                }))
            }
            (CardType::Learn, CardQueue::Learn | CardQueue::DayLearn) => {
                let elapsed_since = self.rwkv_last_review_time(card)?;
                let elapsed_seconds = elapsed_since.map(|last_review_time| {
                    TimestampSecs::now().elapsed_secs_since_clamped(last_review_time)
                });
                Ok(Some(RwkvReviewInputState {
                    state_kind: elapsed_seconds
                        .map(|_| "normal".to_string())
                        .unwrap_or_default(),
                    normal_state_kind: elapsed_seconds
                        .map(|_| "learning".to_string())
                        .unwrap_or_default(),
                    elapsed_days: None,
                    elapsed_seconds,
                    elapsed_since,
                }))
            }
            (CardType::Relearn, CardQueue::Learn | CardQueue::DayLearn) => {
                let Some(last_review_time) = self.rwkv_last_review_time(card)? else {
                    return Ok(Some(RwkvReviewInputState {
                        state_kind: String::new(),
                        normal_state_kind: String::new(),
                        elapsed_days: None,
                        elapsed_seconds: None,
                        elapsed_since: None,
                    }));
                };
                Ok(Some(RwkvReviewInputState {
                    state_kind: "normal".to_string(),
                    normal_state_kind: "relearning".to_string(),
                    elapsed_days: Some(
                        timing
                            .next_day_at
                            .elapsed_days_since_clamped(last_review_time),
                    ),
                    elapsed_seconds: Some(
                        TimestampSecs::now().elapsed_secs_since_clamped(last_review_time),
                    ),
                    elapsed_since: Some(last_review_time),
                }))
            }
            _ => Ok(None),
        }
    }

    fn rwkv_last_review_time(&self, card: &Card) -> Result<Option<TimestampSecs>> {
        Ok(card.last_review_time)
    }

    /// Keep older, unlogged new-card resets when a scheduling operation makes
    /// the card non-new. The card update and boundary share the operation's
    /// undo.
    pub(crate) fn prepare_rwkv_new_card_reset(&mut self, card: &mut Card) -> Result<()> {
        if card.ctype == CardType::New {
            if let Some(previous) = self.storage.last_unreset_review(card.id)? {
                card.rwkv_history_reset_after = Some(
                    card.rwkv_history_reset_after
                        .unwrap_or_default()
                        .max(previous.0),
                );
            }
            self.mirror_rwkv_history_reset(card)?;
        }
        Ok(())
    }

    /// Keep the mirror in the same undoable operation as the card change.
    pub(crate) fn mirror_rwkv_history_reset(&mut self, card: &Card) -> Result<()> {
        let key = crate::config::rwkv_history_reset_config_key(card.id);
        let boundary = card.rwkv_history_reset_after.unwrap_or_default().max(0);
        let previous: Option<crate::config::RwkvHistoryResetMirror> =
            self.storage.get_config_value(&key)?;
        if (boundary > 0 || previous.is_some())
            && previous.map_or(true, |mirror| mirror.after != Some(boundary))
        {
            let mirror = crate::config::RwkvHistoryResetMirror::new(Some(boundary), previous);
            self.set_config(key.as_str(), &mirror)?;
        }
        Ok(())
    }

    pub(crate) fn migrate_rwkv_history_reset_mirrors(&mut self) -> Result<()> {
        let boundaries = self.storage.unmirrored_rwkv_history_resets()?;
        if !boundaries.is_empty() {
            self.transact_no_undo(|col| {
                for (card_id, boundary, modified) in boundaries {
                    let key = crate::config::rwkv_history_reset_config_key(card_id);
                    let mirror = crate::config::RwkvHistoryResetMirror {
                        after: Some(boundary),
                        updated: modified.0.saturating_mul(1000),
                    };
                    col.set_config(key.as_str(), &mirror)?;
                }
                Ok(())
            })?;
        }
        Ok(())
    }

    pub(crate) fn mirror_rwkv_card_only_reset(&mut self, card: &Card) -> Result<()> {
        if let Some(boundary) = card.rwkv_history_reset_after.filter(|after| *after > 0) {
            let key = crate::config::rwkv_history_reset_config_key(card.id);
            let mirror = crate::config::RwkvHistoryResetMirror {
                after: Some(boundary),
                updated: card.mtime.0.saturating_mul(1000),
            };
            self.set_config(key.as_str(), &mirror)?;
        }
        Ok(())
    }

    pub(crate) fn populate_rwkv_last_review_times(&mut self, cards: &mut [Card]) -> Result<()> {
        for card in cards.iter_mut() {
            self.storage.populate_rwkv_history_reset(card)?;
        }
        let needs_review_time = |card: &Card| {
            card.last_review_time.is_none()
                || card.rwkv_history_reset_after.is_some_and(|cutoff| {
                    card.last_review_time
                        .is_some_and(|time| time.0.saturating_mul(1000) <= cutoff)
                })
        };
        // A review changes mtime and reps; a synced boundary can change
        // independently of either, so it must be part of the cache key.
        let key = |card: &Card| (card.mtime, card.reps, card.rwkv_history_reset_after);
        let cache = &self.state.rwkv_last_review_times;
        let missing_card_ids: Vec<_> = cards
            .iter()
            .filter(|card| {
                needs_review_time(card) && cache.get(&card.id).is_none_or(|(k, _)| *k != key(card))
            })
            .map(|card| card.id)
            .collect();
        let review_times = self.storage.rwkv_times_of_last_review(&missing_card_ids)?;
        let cache = &mut self.state.rwkv_last_review_times;
        for card in cards {
            if !needs_review_time(card) {
                continue;
            }
            card.last_review_time = match cache.get(&card.id) {
                Some((k, time)) if *k == key(card) => *time,
                _ => review_times.get(&card.id).copied(),
            };
            cache.insert(card.id, (key(card), card.last_review_time));
        }

        Ok(())
    }
}

/// Broaden RWKV-dependent conditions so RWKV can score every potential match.
///
/// The final search runs after the resulting scores have been cached. Treating
/// each model-dependent predicate as independently satisfiable may load extra
/// candidates, but cannot omit a card that the final search could match.
fn broaden_retrievability_properties(node: Node, negated: bool) -> Node {
    match node {
        Node::Not(inner) => Node::Not(Box::new(broaden_retrievability_properties(
            *inner, !negated,
        ))),
        Node::Group(nodes) => Node::Group(
            nodes
                .into_iter()
                .map(|node| broaden_retrievability_properties(node, negated))
                .collect(),
        ),
        Node::Search(SearchNode::Property {
            kind: PropertyKind::RwkvRetrievability(_) | PropertyKind::RwkvCurveRetrievability(_),
            ..
        })
        | Node::Search(SearchNode::State(StateKind::RwkvDue | StateKind::RwkvCurveDue)) => {
            boolean_search_node(!negated)
        }
        other => other,
    }
}

fn boolean_search_node(value: bool) -> Node {
    let all_cards = Node::Search(SearchNode::WholeCollection);
    if value {
        all_cards
    } else {
        Node::Not(Box::new(all_cards))
    }
}

fn search_explicitly_includes_new_cards(nodes: &[Node]) -> bool {
    nodes
        .iter()
        .any(|node| node_explicitly_includes_new_cards(node, false))
}

fn node_explicitly_includes_new_cards(node: &Node, negated: bool) -> bool {
    match node {
        Node::Search(SearchNode::State(StateKind::New)) => !negated,
        Node::Not(inner) => node_explicitly_includes_new_cards(inner, !negated),
        Node::Group(nodes) => nodes
            .iter()
            .any(|node| node_explicitly_includes_new_cards(node, negated)),
        Node::And | Node::Or | Node::Search(_) => false,
    }
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct RwkvReviewCandidateMetadata {
    pub(crate) target_retention: f32,
    pub(crate) reviewed_today: bool,
    pub(crate) same_day_review_allowed: bool,
    pub(crate) elapsed_secs_since_last_review: Option<u32>,
    pub(crate) current_deck_id: DeckId,
    pub(crate) source_deck_id: DeckId,
    pub(crate) fsrs_due_today: bool,
}

pub(crate) fn rwkv_review_candidate_metadata(
    col: &mut Collection,
    card_ids: &[CardId],
    timing: SchedTimingToday,
) -> Result<HashMap<CardId, RwkvReviewCandidateMetadata>> {
    let mut cards = col.all_cards_for_ids(card_ids, false)?;
    col.populate_rwkv_last_review_times(&mut cards)?;
    let mut metadata = HashMap::with_capacity(cards.len());
    let mut partial_by_card = HashMap::new();
    let mut without_card_target = Vec::new();
    let same_day_review_allowed = col.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled);

    for card in cards {
        if card.queue != CardQueue::Review {
            continue;
        }

        let partial = RwkvReviewCandidatePartial {
            reviewed_today: card_reviewed_today(&card, timing),
            elapsed_secs_since_last_review: card
                .last_review_time
                .map(|last_review_time| timing.now.elapsed_secs_since_clamped(last_review_time)),
            current_deck_id: card.deck_id,
            source_deck_id: card.original_deck_id.or(card.deck_id),
            fsrs_due_today: card.due <= timing.days_elapsed as i32,
        };
        if let Some(desired_retention) = card_desired_retention(&card) {
            metadata.insert(
                card.id,
                partial.with_target_retention(desired_retention, same_day_review_allowed),
            );
        } else {
            partial_by_card.insert(card.id, partial);
            without_card_target.push(card);
        }
    }

    for (card_id, preset) in col.fsrs_presets_for_cards(&without_card_target)? {
        if let Some(partial) = partial_by_card.remove(&card_id) {
            metadata.insert(
                card_id,
                partial.with_target_retention(preset.desired_retention, same_day_review_allowed),
            );
        }
    }

    Ok(metadata)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum RwkvReviewScoreEligibility {
    Eligible,
    Deferred {
        required_intervening_reviews: Option<u32>,
        intervening_reviews: Option<u32>,
        remaining_elapsed_secs: Option<u32>,
    },
    Blocked,
}

pub(crate) fn rwkv_review_score_eligibility(
    score: f32,
    metadata: &RwkvReviewCandidateMetadata,
    min_intervening_reviews: u32,
    min_elapsed_secs: u32,
    intervening_reviews: Option<u32>,
    target_retention: Option<f32>,
) -> RwkvReviewScoreEligibility {
    rwkv_review_score_eligibility_inner(
        score,
        metadata,
        min_intervening_reviews,
        min_elapsed_secs,
        intervening_reviews,
        RwkvReviewTarget::Enforce(target_retention),
    )
}

pub(crate) fn rwkv_review_score_eligibility_ignoring_retention(
    score: f32,
    metadata: &RwkvReviewCandidateMetadata,
    min_intervening_reviews: u32,
    min_elapsed_secs: u32,
    intervening_reviews: Option<u32>,
) -> RwkvReviewScoreEligibility {
    rwkv_review_score_eligibility_inner(
        score,
        metadata,
        min_intervening_reviews,
        min_elapsed_secs,
        intervening_reviews,
        RwkvReviewTarget::Ignore,
    )
}

enum RwkvReviewTarget {
    Enforce(Option<f32>),
    Ignore,
}

pub(crate) fn relative_overdueness(retrievability: f32, target_retention: f32) -> f32 {
    retrievability / target_retention.max(0.0001)
}

pub(crate) fn rwkv_review_relative_overdueness(
    retrievability: f32,
    metadata: &RwkvReviewCandidateMetadata,
    target_retention: Option<f32>,
) -> f32 {
    relative_overdueness(
        retrievability,
        rwkv_review_target_retention(metadata, target_retention),
    )
}

fn rwkv_review_target_retention(
    metadata: &RwkvReviewCandidateMetadata,
    target_retention: Option<f32>,
) -> f32 {
    target_retention
        .filter(|target| target.is_finite() && (0.0..=1.0).contains(target))
        .unwrap_or(metadata.target_retention)
}

fn rwkv_review_score_eligibility_inner(
    score: f32,
    metadata: &RwkvReviewCandidateMetadata,
    min_intervening_reviews: u32,
    min_elapsed_secs: u32,
    intervening_reviews: Option<u32>,
    target: RwkvReviewTarget,
) -> RwkvReviewScoreEligibility {
    let score_above_target = match target {
        RwkvReviewTarget::Enforce(target_retention) => {
            score > rwkv_review_target_retention(metadata, target_retention)
        }
        RwkvReviewTarget::Ignore => false,
    };

    if !score.is_finite()
        || score_above_target
        || (metadata.reviewed_today && !metadata.same_day_review_allowed)
    {
        return RwkvReviewScoreEligibility::Blocked;
    }

    rwkv_repeat_spacing_eligibility(
        metadata.elapsed_secs_since_last_review,
        min_intervening_reviews,
        min_elapsed_secs,
        intervening_reviews,
    )
}

pub(crate) fn rwkv_repeat_spacing_eligibility(
    elapsed_secs_since_last_review: Option<u32>,
    min_intervening_reviews: u32,
    min_elapsed_secs: u32,
    intervening_reviews: Option<u32>,
) -> RwkvReviewScoreEligibility {
    let required_intervening_reviews =
        (!rwkv_review_intervening_reviews_elapsed(intervening_reviews, min_intervening_reviews))
            .then_some(min_intervening_reviews);
    let remaining_elapsed_secs = elapsed_secs_since_last_review
        .filter(|elapsed_secs| *elapsed_secs < min_elapsed_secs)
        .map(|elapsed_secs| min_elapsed_secs - elapsed_secs);
    if required_intervening_reviews.is_some() || remaining_elapsed_secs.is_some() {
        RwkvReviewScoreEligibility::Deferred {
            required_intervening_reviews,
            intervening_reviews,
            remaining_elapsed_secs,
        }
    } else {
        RwkvReviewScoreEligibility::Eligible
    }
}

fn rwkv_review_intervening_reviews_elapsed(
    intervening_reviews: Option<u32>,
    min_intervening_reviews: u32,
) -> bool {
    min_intervening_reviews == 0
        || intervening_reviews.is_none_or(|reviews| reviews >= min_intervening_reviews)
}

#[derive(Debug, Clone, Copy)]
struct RwkvReviewCandidatePartial {
    reviewed_today: bool,
    elapsed_secs_since_last_review: Option<u32>,
    current_deck_id: DeckId,
    source_deck_id: DeckId,
    fsrs_due_today: bool,
}

impl RwkvReviewCandidatePartial {
    fn with_target_retention(
        self,
        target_retention: f32,
        same_day_review_allowed: bool,
    ) -> RwkvReviewCandidateMetadata {
        RwkvReviewCandidateMetadata {
            target_retention,
            reviewed_today: self.reviewed_today,
            same_day_review_allowed,
            elapsed_secs_since_last_review: self.elapsed_secs_since_last_review,
            current_deck_id: self.current_deck_id,
            source_deck_id: self.source_deck_id,
            fsrs_due_today: self.fsrs_due_today,
        }
    }
}

#[derive(Debug)]
struct RwkvHistoricalPresetRoute {
    stable_preset_id: i64,
    card_ids: Option<HashSet<CardId>>,
    min_reps: Option<u32>,
    max_reps: Option<u32>,
    min_interval_days: Option<f32>,
    max_interval_days: Option<f32>,
}

impl RwkvHistoricalPresetRoute {
    fn matches(&self, card_id: CardId, reps: u32, interval_days: i64) -> bool {
        self.card_ids
            .as_ref()
            .is_none_or(|card_ids| card_ids.contains(&card_id))
            && self.min_reps.is_none_or(|minimum| reps >= minimum)
            && self.max_reps.is_none_or(|maximum| reps <= maximum)
            && self
                .min_interval_days
                .is_none_or(|minimum| interval_days as f32 >= minimum)
            && self
                .max_interval_days
                .is_none_or(|maximum| interval_days as f32 <= maximum)
    }
}

#[derive(Debug, Clone, Copy)]
struct RwkvHistoricalFingerprintReview {
    row: RwkvHistoricalReviewRow,
    stable_preset_id: i64,
    day_offset: i64,
    elapsed_days: i64,
    elapsed_seconds: i64,
}

/// The preset id `fsrs_presets_for_cards` gives a card of this home deck
/// when no overlay rule moves it, with the same lookups and errors.
fn rwkv_home_deck_stable_preset_id(
    deck_id: DeckId,
    decks_by_id: &HashMap<DeckId, Deck>,
    configs_by_id: &HashMap<DeckConfigId, DeckConfig>,
    stable_preset_ids: &HashMap<String, i64>,
) -> Result<i64> {
    let deck = decks_by_id.get(&deck_id).or_not_found(deck_id)?;
    let config_id = deck.config_id().or_invalid("home deck is filtered")?;
    let config = configs_by_id.get(&config_id).or_not_found(config_id)?;
    rwkv_stable_preset_id(
        &FsrsPreset::from_deck_config(config, deck)?.id,
        stable_preset_ids,
    )
}

fn rwkv_stable_preset_id(
    preset_id: &FsrsPresetId,
    stable_preset_ids: &HashMap<String, i64>,
) -> Result<i64> {
    match preset_id {
        FsrsPresetId::DeckConfig(id) => Ok(id.0),
        FsrsPresetId::Addon(id) => id
            .parse()
            .ok()
            .or_else(|| stable_preset_ids.get(id).copied())
            .or_else(|| rwkv_stable_preset_id_from_string(id))
            .or_invalid("missing stable id for add-on FSRS preset"),
    }
}

/// Match desktop's `_stable_preset_id`: deck ids are decimal i64s, while
/// add-on ids (which start with `addon:`) use an eight-byte BLAKE2b digest.
/// This is BLAKE2b configured for an eight-byte output, not a truncated
/// 64-byte digest; the output length is part of BLAKE2's parameters.
fn rwkv_stable_preset_id_from_string(id: &str) -> Option<i64> {
    if id.is_empty() {
        return None;
    }
    if id.bytes().all(|byte| byte.is_ascii_digit()) {
        return id.parse().ok();
    }
    let digest = Blake2b::<U8>::digest(id.as_bytes());
    Some(i64::from_be_bytes(digest.into()) & i64::MAX)
}

fn rwkv_first_review_uses_card_creation(
    deck_id: i64,
    decks_by_id: &HashMap<DeckId, Deck>,
    configs_by_id: &HashMap<DeckConfigId, DeckConfig>,
    requested_values_by_config_id: &HashMap<i64, bool>,
) -> bool {
    decks_by_id
        .get(&DeckId(deck_id))
        .and_then(Deck::config_id)
        .is_some_and(|config_id| {
            requested_values_by_config_id
                .get(&config_id.0)
                .copied()
                .or_else(|| configs_by_id.get(&config_id).map(|_| true))
                .unwrap_or(false)
        })
}

fn rwkv_historical_day_offset(review_id: i64, timing: &SchedTimingToday) -> i64 {
    let review_secs = review_id / 1000;
    let days_before_today = (timing.next_day_at.0 - 1 - review_secs).max(0) / 86_400;
    (timing.days_elapsed as i64 - days_before_today).max(0)
}

fn rwkv_empty_history_hash() -> [u8; 32] {
    Sha256::digest(RWKV_HISTORY_HASH_DOMAIN).into()
}

fn rwkv_history_hash_after_review(
    previous_hash: [u8; 32],
    review: &RwkvHistoricalFingerprintReview,
) -> [u8; 32] {
    let mut digest = Sha256::new();
    digest.update(RWKV_HISTORY_HASH_DOMAIN);
    digest.update(previous_hash);
    digest.update(rwkv_historical_review_record(review));
    digest.finalize().into()
}

fn rwkv_historical_review_record(review: &RwkvHistoricalFingerprintReview) -> Vec<u8> {
    let row = review.row;
    let mut out = Vec::with_capacity(192);
    rwkv_write_i64(&mut out, row.review_id);
    rwkv_write_i64(&mut out, row.card_id);
    rwkv_write_optional_i64(&mut out, Some(row.note_id));
    rwkv_write_optional_i64(&mut out, Some(row.deck_id));
    rwkv_write_optional_i64(&mut out, Some(review.stable_preset_id));
    out.push(0);
    rwkv_write_optional_i64(&mut out, Some(row.ease));
    rwkv_write_optional_i64(&mut out, Some(row.duration_millis));
    rwkv_write_optional_i64(
        &mut out,
        Some(if row.is_learning_start {
            0
        } else {
            row.review_kind + 1
        }),
    );
    let queue = match row.review_kind {
        0 => CardQueue::Learn as i8 as i64,
        2 => CardQueue::DayLearn as i8 as i64,
        _ => CardQueue::Review as i8 as i64,
    };
    rwkv_write_optional_i64(&mut out, Some(queue));
    rwkv_write_optional_i64(&mut out, None);
    rwkv_write_optional_i64(&mut out, Some(row.interval_days));
    rwkv_write_optional_i64(&mut out, Some(row.ease_factor));
    rwkv_write_optional_i64(&mut out, None);
    rwkv_write_optional_i64(&mut out, None);
    rwkv_write_optional_i64(&mut out, Some(review.day_offset));
    let (state_kind, normal_state_kind) = match row.review_kind {
        0 => (Some("normal"), Some("learning")),
        2 => (Some("normal"), Some("relearning")),
        3 => (Some("filtered"), None),
        _ => (Some("normal"), Some("review")),
    };
    rwkv_write_optional_string(&mut out, state_kind);
    rwkv_write_optional_string(&mut out, normal_state_kind);
    rwkv_write_optional_i64(&mut out, Some(review.elapsed_days));
    rwkv_write_optional_i64(&mut out, Some(review.elapsed_seconds));
    out
}

fn rwkv_write_i64(out: &mut Vec<u8>, value: i64) {
    out.extend_from_slice(&value.to_le_bytes());
}

fn rwkv_write_optional_i64(out: &mut Vec<u8>, value: Option<i64>) {
    match value {
        Some(value) => {
            out.push(1);
            rwkv_write_i64(out, value);
        }
        None => out.push(0),
    }
}

fn rwkv_write_optional_string(out: &mut Vec<u8>, value: Option<&str>) {
    match value {
        Some(value) => {
            out.push(1);
            let value = value.as_bytes();
            out.extend_from_slice(&(value.len() as u32).to_le_bytes());
            out.extend_from_slice(value);
        }
        None => out.push(0),
    }
}

fn rwkv_history_hash_hex(hash: [u8; 32]) -> String {
    use std::fmt::Write;

    hash.into_iter()
        .fold(String::with_capacity(64), |mut output, byte| {
            write!(output, "{byte:02x}").unwrap();
            output
        })
}

struct RwkvReviewInputRowOptions {
    include_suspended_review: bool,
    include_disabled_decks: bool,
    include_comparison_data: bool,
}

#[derive(Debug)]
struct RwkvReviewInputRowPartial {
    card: Card,
    current_deck_id: DeckId,
    state: RwkvReviewInputState,
    target_retention: f32,
    batch_size: u32,
    enforce_grade_order: bool,
}

#[derive(Debug)]
struct RwkvReviewInputState {
    state_kind: String,
    normal_state_kind: String,
    elapsed_days: Option<u32>,
    elapsed_seconds: Option<u32>,
    elapsed_since: Option<TimestampSecs>,
}

fn rwkv_fsrs_preset_id_to_string(id: FsrsPresetId) -> String {
    match id {
        FsrsPresetId::DeckConfig(id) => id.0.to_string(),
        FsrsPresetId::Addon(id) => id,
    }
}

fn valid_rwkv_target_retention(target_retention: f32) -> f32 {
    if target_retention.is_finite() && (0.0..=1.0).contains(&target_retention) {
        target_retention
    } else {
        0.9
    }
}

fn rwkv_enabled_deck_ids(
    decks_by_id: &HashMap<DeckId, Deck>,
    configs_by_id: &HashMap<DeckConfigId, DeckConfig>,
) -> HashSet<DeckId> {
    decks_by_id
        .iter()
        .filter_map(|(deck_id, deck)| {
            let config_id = deck.config_id()?;
            configs_by_id
                .get(&config_id)
                .is_some_and(rwkv_config_active)
                .then_some(*deck_id)
        })
        .collect()
}

fn rwkv_config_active(config: &DeckConfig) -> bool {
    config.inner.rwkv_review_enabled || config.inner.rwkv_review_instant_order_enabled
}

fn card_desired_retention(card: &Card) -> Option<f32> {
    card.desired_retention
        .filter(|dr| valid_card_desired_retention(*dr))
}

fn valid_card_desired_retention(desired_retention: f32) -> bool {
    desired_retention.is_finite() && desired_retention > 0.0 && desired_retention < 1.0
}

pub(crate) fn card_reviewed_today(card: &Card, timing: SchedTimingToday) -> bool {
    card.last_review_time
        .is_some_and(|last_review_time| timing.is_today(last_review_time))
}

fn rwkv_rescheduled_memory_state(card: &Card, s90: f32) -> FsrsMemoryState {
    let existing = card.memory_state;
    FsrsMemoryState {
        stability: s90,
        stability_internal: existing
            .map(|state| state.stability_internal)
            .filter(|stability| stability.is_finite() && *stability > 0.0)
            .unwrap_or(s90),
        stability_fast: existing
            .and_then(|state| state.stability_fast)
            .filter(|stability| stability.is_finite() && *stability > 0.0)
            .or(Some(s90)),
        difficulty: existing
            .map(|state| state.difficulty)
            .filter(|difficulty| difficulty.is_finite() && *difficulty > 0.0)
            .unwrap_or(5.0),
    }
}

fn rwkv_rescheduled_due_day(today: u32, elapsed_days: u32, interval_days: u32) -> i32 {
    ((today as i64) - (elapsed_days as i64) + (interval_days as i64)).clamp(0, i32::MAX as i64)
        as i32
}

#[cfg(test)]
mod test {
    use anki_proto::scheduler::RwkvHistoricalReviewFingerprintRequest;
    use anki_proto::scheduler::RwkvReviewInputRowsForCardsRequest;
    use anki_proto::scheduler::RwkvReviewInputRowsForDeckReviewQueueRequest;
    use anki_proto::scheduler::RwkvReviewInputRowsForSearchRequest;

    use super::*;
    use crate::notes::NoteId;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;

    #[test]
    fn stable_preset_ids_match_desktop_blake2b_vectors() {
        // Generated with Python hashlib.blake2b(..., digest_size=8), including
        // a UTF-8 ID and a digest whose high bit must be cleared.
        for (id, expected) in [
            ("addon:test:moved", 334_249_656_965_035_719),
            (
                "addon:fsrs-dynamic-preset-selection:日本語",
                5_066_060_561_763_284_113,
            ),
            (
                "addon:uuid:550e8400-e29b-41d4-a716-446655440000",
                5_599_663_058_818_127_186,
            ),
            ("000123", 123),
        ] {
            assert_eq!(rwkv_stable_preset_id_from_string(id), Some(expected));
        }
        assert_eq!(rwkv_stable_preset_id_from_string(""), None);
        assert_eq!(
            rwkv_stable_preset_id_from_string("9223372036854775808"),
            None
        );
    }

    #[test]
    fn addon_preset_history_replays_without_a_desktop_lookup_table() -> Result<()> {
        use crate::scheduler::fsrs::preset::AddonFsrsPreset;
        use crate::scheduler::fsrs::preset::FsrsPresetOverlay;
        use crate::scheduler::fsrs::preset::FsrsPresetRule;
        use crate::scheduler::fsrs::preset::FsrsPresetSimulatorRule;
        use crate::scheduler::fsrs::preset::FSRS_PRESET_OVERLAY_CONFIG_KEY;

        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let card_id = col.answer_good().card_id;
        let id = "addon:test:moved";
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: id.into(),
                    name: "Moved".into(),
                    desired_retention: 0.9,
                    historical_retention: 0.9,
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: format!("cid:{}", card_id.0),
                    preset_id: id.into(),
                }],
                simulator_rules: vec![FsrsPresetSimulatorRule {
                    preset_id: id.into(),
                    ..Default::default()
                }],
            },
        )?;

        for dynamic_preset_replay in [false, true] {
            let request = |stable_preset_ids| RwkvHistoricalReviewFingerprintRequest {
                stable_preset_ids,
                dynamic_preset_replay,
                ..Default::default()
            };
            let mobile = col.rwkv_historical_review_fingerprint(request(HashMap::new()))?;
            let desktop = col.rwkv_historical_review_fingerprint(request(HashMap::from([(
                id.into(),
                334_249_656_965_035_719,
            )])))?;
            assert_eq!(mobile.review_count, 1);
            assert_eq!(mobile.history_hash, desktop.history_hash);
        }
        Ok(())
    }

    #[test]
    fn historical_fingerprint_matches_python_cache_encoding() {
        let row = RwkvHistoricalReviewRow {
            review_id: 1_700_000_000_123,
            card_id: 1_699_999_000_123,
            note_id: 42,
            deck_id: 100,
            ease: 3,
            duration_millis: 2_345,
            review_kind: RevlogReviewKind::Learning as i64,
            interval_days: 4,
            ease_factor: 2_500,
            is_learning_start: true,
        };
        let hash = rwkv_history_hash_after_review(
            rwkv_empty_history_hash(),
            &RwkvHistoricalFingerprintReview {
                row,
                stable_preset_id: 1_000,
                day_offset: 10,
                elapsed_days: 0,
                elapsed_seconds: 1_000,
            },
        );

        assert_eq!(
            rwkv_history_hash_hex(hash),
            "05a02f4c5e7696f6272742ca01a412e984a07227a561eaa873cbf69a4ebaa226"
        );
    }

    #[test]
    fn historical_fingerprint_retains_latest_learning_sequence_and_ignores_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut card = Card::new(NoteId(10), 0, DeckId(1), 0);
        card.ctype = CardType::Review;
        col.add_card(&mut card)?;
        let first_review_id = card.id.0 + 10_000;
        let review_ids = [
            first_review_id,
            first_review_id + 1_000,
            first_review_id + 2_000,
            first_review_id + 3_000,
        ];
        for (index, (review_id, review_kind)) in review_ids
            .into_iter()
            .zip([
                RevlogReviewKind::Review,
                RevlogReviewKind::Learning,
                RevlogReviewKind::Learning,
                RevlogReviewKind::Review,
            ])
            .enumerate()
        {
            col.storage.add_revlog_entry(
                &RevlogEntry {
                    id: RevlogId(review_id),
                    cid: card.id,
                    usn: Usn(0),
                    button_chosen: (index % 4 + 1) as u8,
                    interval: index as i32 + 1,
                    ease_factor: 2_500,
                    taken_millis: 1_000,
                    review_kind,
                    ..Default::default()
                },
                false,
            )?;
        }

        let fingerprint = col
            .rwkv_historical_review_fingerprint(RwkvHistoricalReviewFingerprintRequest::default())?;
        assert_eq!(fingerprint.last_review_id, review_ids[3]);
        assert_eq!(fingerprint.review_count, 3);
        assert_eq!(fingerprint.queried_review_count, 3);
        assert!(fingerprint.active_ignored_review_ids.is_empty());
        assert_eq!(fingerprint.history_hash.len(), 64);
        assert!(!fingerprint.history_is_valid);

        let matching_fingerprint =
            col.rwkv_historical_review_fingerprint(RwkvHistoricalReviewFingerprintRequest {
                expected_identity: Some(scheduler::RwkvHistoricalReviewIdentity {
                    last_review_id: fingerprint.last_review_id,
                    review_count: fingerprint.review_count,
                    history_hash: fingerprint.history_hash.clone(),
                }),
                ..Default::default()
            })?;
        assert!(matching_fingerprint.history_is_valid);

        let fingerprint =
            col.rwkv_historical_review_fingerprint(RwkvHistoricalReviewFingerprintRequest {
                ignored_review_ids: vec![review_ids[2]],
                ..Default::default()
            })?;
        assert_eq!(fingerprint.last_review_id, review_ids[3]);
        assert_eq!(fingerprint.review_count, 2);
        assert_eq!(fingerprint.queried_review_count, 2);
        assert_eq!(fingerprint.active_ignored_review_ids, vec![review_ids[2]]);
        assert!(!fingerprint.history_is_valid);

        let ignored_identity = Some(scheduler::RwkvHistoricalReviewIdentity {
            last_review_id: fingerprint.last_review_id,
            review_count: fingerprint.review_count,
            history_hash: fingerprint.history_hash.clone(),
        });
        let matching_ignored_fingerprint =
            col.rwkv_historical_review_fingerprint(RwkvHistoricalReviewFingerprintRequest {
                ignored_review_ids: vec![review_ids[2]],
                expected_identity: ignored_identity.clone(),
                ..Default::default()
            })?;
        assert!(matching_ignored_fingerprint.history_is_valid);

        let stale_ignored_fingerprint =
            col.rwkv_historical_review_fingerprint(RwkvHistoricalReviewFingerprintRequest {
                ignored_review_ids: vec![review_ids[2], review_ids[3] + 10_000],
                expected_identity: ignored_identity,
                ..Default::default()
            })?;
        assert!(!stale_ignored_fingerprint.history_is_valid);

        Ok(())
    }

    #[test]
    fn historical_fingerprint_validates_appended_history_prefix() -> Result<()> {
        let mut col = Collection::new();
        let mut card = Card::new(NoteId(10), 0, DeckId(1), 0);
        card.ctype = CardType::Review;
        col.add_card(&mut card)?;
        let first_review_id = card.id.0 + 10_000;
        let add_review = |col: &mut Collection, review_id: i64, review_kind| {
            col.storage.add_revlog_entry(
                &RevlogEntry {
                    id: RevlogId(review_id),
                    cid: card.id,
                    usn: Usn(0),
                    button_chosen: 3,
                    interval: 1,
                    ease_factor: 2_500,
                    taken_millis: 1_000,
                    review_kind,
                    ..Default::default()
                },
                false,
            )
        };
        add_review(&mut col, first_review_id, RevlogReviewKind::Learning)?;
        add_review(&mut col, first_review_id + 2_000, RevlogReviewKind::Review)?;
        let prefix = col
            .rwkv_historical_review_fingerprint(RwkvHistoricalReviewFingerprintRequest::default())?;
        let fingerprint_against_prefix = |col: &mut Collection| {
            col.rwkv_historical_review_fingerprint(RwkvHistoricalReviewFingerprintRequest {
                expected_identity: Some(scheduler::RwkvHistoricalReviewIdentity {
                    last_review_id: prefix.last_review_id,
                    review_count: prefix.review_count,
                    history_hash: prefix.history_hash.clone(),
                }),
                ..Default::default()
            })
        };

        let unchanged = fingerprint_against_prefix(&mut col)?;
        assert!(unchanged.history_is_valid);
        assert!(!unchanged.history_prefix_is_valid);

        add_review(&mut col, first_review_id + 3_000, RevlogReviewKind::Review)?;
        let appended = fingerprint_against_prefix(&mut col)?;
        assert!(!appended.history_is_valid);
        assert!(appended.history_prefix_is_valid);

        add_review(&mut col, first_review_id + 1_000, RevlogReviewKind::Review)?;
        let inserted = fingerprint_against_prefix(&mut col)?;
        assert!(!inserted.history_is_valid);
        assert!(!inserted.history_prefix_is_valid);

        col.storage
            .remove_revlog_entry(RevlogId(first_review_id + 1_000))?;
        add_review(
            &mut col,
            first_review_id + 4_000,
            RevlogReviewKind::Learning,
        )?;
        let relearned = fingerprint_against_prefix(&mut col)?;
        assert!(!relearned.history_is_valid);
        assert!(!relearned.history_prefix_is_valid);

        Ok(())
    }

    /// Without overlay rules the fingerprint resolves preset ids per deck; it
    /// must give the per-card lookup's hash, and a rule that moves a card must
    /// still change it.
    #[test]
    fn historical_fingerprint_agrees_with_the_per_card_preset_lookup() -> Result<()> {
        use crate::scheduler::fsrs::preset::AddonFsrsPreset;
        use crate::scheduler::fsrs::preset::FsrsPresetOverlay;
        use crate::scheduler::fsrs::preset::FsrsPresetRule;
        use crate::scheduler::fsrs::preset::FSRS_PRESET_OVERLAY_CONFIG_KEY;

        let mut col = Collection::new();
        let mut card_ids = Vec::new();
        for index in 0..3 {
            let mut card = Card::new(NoteId(10 + index), 0, DeckId(1), 0);
            card.ctype = CardType::Review;
            col.add_card(&mut card)?;
            for step in 0..3 {
                col.storage.add_revlog_entry(
                    &RevlogEntry {
                        id: RevlogId(card.id.0 + 10_000 + step * 1_000),
                        cid: card.id,
                        usn: Usn(0),
                        button_chosen: 3,
                        interval: step as i32 + 1,
                        ease_factor: 2_500,
                        taken_millis: 1_000,
                        review_kind: RevlogReviewKind::Learning,
                        ..Default::default()
                    },
                    false,
                )?;
            }
            card_ids.push(card.id);
        }

        let request = || RwkvHistoricalReviewFingerprintRequest {
            stable_preset_ids: HashMap::from([("addon:test:moved".to_string(), 77_000)]),
            ..Default::default()
        };
        let by_deck = col.rwkv_historical_review_fingerprint(request())?;
        assert_eq!(by_deck.review_count, 9);

        let overlay = |search: String| FsrsPresetOverlay {
            presets: vec![AddonFsrsPreset {
                id: "addon:test:moved".into(),
                name: "Moved".into(),
                desired_retention: 0.9,
                historical_retention: 0.9,
                ..Default::default()
            }],
            rules: vec![FsrsPresetRule {
                search,
                preset_id: "addon:test:moved".into(),
            }],
            simulator_rules: Vec::new(),
        };

        // a rule that moves no card: per-card lookup, same history
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &overlay(format!("cid:{}", card_ids[0].0 + 1_000_000)),
        )?;
        let by_card = col.rwkv_historical_review_fingerprint(request())?;
        assert_eq!(by_card.history_hash, by_deck.history_hash);

        // a rule that moves a card changes the history
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &overlay(format!("cid:{}", card_ids[0].0)),
        )?;
        let moved = col.rwkv_historical_review_fingerprint(request())?;
        assert_ne!(moved.history_hash, by_deck.history_hash);

        Ok(())
    }

    #[test]
    fn apply_review_reschedule_does_not_write_revlog() -> Result<()> {
        let mut col = Collection::new();
        let timing = col.timing_today()?;
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32 + 8);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 4;
        col.add_card(&mut card)?;

        let revlogs_before = col.storage.get_revlog_entries_for_card(card.id)?.len();
        let result = col.apply_rwkv_review_reschedule(vec![RwkvReviewRescheduleItem {
            card_id: card.id,
            interval_days: 12,
            elapsed_days: 4,
            s90: 9.5,
            target_retention: Some(0.75),
        }])?;

        let updated = col.storage.get_card(card.id)?.unwrap();
        assert_eq!(result.output, 1);
        assert_eq!(updated.interval, 12);
        let memory_state = updated.memory_state.unwrap();
        assert_eq!(memory_state.stability, 9.5);
        let fsrs = fsrs::FSRS::new(&fsrs::DEFAULT_PARAMETERS)?;
        let s90 = fsrs.interval_at_retrievability(memory_state.into(), 0.9);
        assert!((s90 - 9.5).abs() < 0.01, "{s90}");
        assert_eq!(updated.desired_retention, Some(0.75));
        assert_eq!(
            col.storage.get_revlog_entries_for_card(card.id)?.len(),
            revlogs_before
        );

        Ok(())
    }

    #[test]
    fn apply_review_reschedule_rejects_endpoint_target_retentions() -> Result<()> {
        let mut col = Collection::new();
        let timing = col.timing_today()?;
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32 + 8);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 4;
        col.add_card(&mut card)?;

        for target_retention in [0.0, 1.0] {
            let result = col.apply_rwkv_review_reschedule(vec![RwkvReviewRescheduleItem {
                card_id: card.id,
                interval_days: 12,
                elapsed_days: 4,
                s90: 9.5,
                target_retention: Some(target_retention),
            }]);
            assert!(result.is_err());
        }

        let stored = col.storage.get_card(card.id)?.unwrap();
        assert_eq!(stored.interval, 4);
        assert_eq!(stored.desired_retention, None);

        Ok(())
    }

    #[test]
    fn review_input_rows_return_cards_when_only_instant_is_enabled() -> Result<()> {
        let mut col = Collection::new();
        col.update_default_deck_config(|config| {
            config.rwkv_review_enabled = false;
            config.rwkv_review_instant_order_enabled = true;
            config.rwkv_review_batch_size = 1024;
            config.rwkv_review_enforce_grade_order = false;
            config.desired_retention = 0.86;
        });
        let timing = col.timing_today()?;
        let last_review_time = timing.next_day_at.adding_secs(-39 * 86_400);
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32 + 8);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 4;
        card.ease_factor = 2500;
        card.reps = 5;
        card.lapses = 1;
        card.last_review_time = Some(last_review_time);
        col.add_card(&mut card)?;

        let response =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0],
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
            })?;

        assert_eq!(response.loaded_cards, 1);
        assert_eq!(response.cards_with_supported_state, 1);
        assert_eq!(response.disabled_config_cards, 0);
        assert_eq!(response.deck_configs, 1);
        assert_eq!(response.rows.len(), 1);
        let row = &response.rows[0];
        assert_eq!(row.card_id, card.id.0);
        assert_eq!(row.note_id, card.note_id.0);
        assert_eq!(row.deck_id, 1);
        assert_eq!(row.preset_id, "1");
        assert_eq!(row.card_type, CardType::Review as i32);
        assert_eq!(row.card_queue, CardQueue::Review as i32);
        assert_eq!(row.card_due, card.due);
        assert_eq!(row.interval_days, 4);
        assert_eq!(row.ease_factor, 2500);
        assert_eq!(row.reps, 5);
        assert_eq!(row.lapses, 1);
        assert_eq!(row.day_offset, timing.days_elapsed);
        assert_eq!(row.current_state_kind, "normal");
        assert_eq!(row.current_normal_state_kind, "review");
        assert_eq!(row.current_elapsed_days, Some(39));
        let elapsed_seconds = row.current_elapsed_seconds.unwrap();
        assert!((38 * 86_400..=39 * 86_400).contains(&elapsed_seconds));
        assert_eq!(row.current_elapsed_since_secs, Some(last_review_time.0));
        assert_eq!(row.target_retention, 0.86);
        assert_eq!(row.batch_size, 1024);
        assert_eq!(row.enforce_grade_order, Some(false));

        Ok(())
    }

    #[test]
    fn review_input_rows_use_revlog_last_review_time_when_card_data_missing() -> Result<()> {
        let mut col = Collection::new();
        col.update_default_deck_config(|config| {
            config.rwkv_review_enabled = true;
            config.rwkv_review_batch_size = 1024;
        });
        let timing = col.timing_today()?;
        let last_review_time = timing.next_day_at.adding_secs(-39 * 86_400);
        let ignored_filtered_time = timing.next_day_at.adding_secs(-3 * 86_400);
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32 + 8);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 4;
        card.ease_factor = 2500;
        card.reps = 5;
        card.lapses = 1;
        col.add_card(&mut card)?;
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId(last_review_time.0 * 1000),
                cid: card.id,
                usn: Usn(0),
                button_chosen: 3,
                interval: 4,
                last_interval: 3,
                ease_factor: 2500,
                review_kind: RevlogReviewKind::Review,
                ..Default::default()
            },
            false,
        )?;
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId(ignored_filtered_time.0 * 1000),
                cid: card.id,
                usn: Usn(0),
                button_chosen: 3,
                review_kind: RevlogReviewKind::Filtered,
                ..Default::default()
            },
            false,
        )?;

        let response =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0],
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
            })?;

        assert_eq!(response.loaded_cards, 1);
        assert_eq!(response.cards_with_supported_state, 1);
        assert_eq!(response.rows.len(), 1);
        assert_eq!(response.rows[0].current_elapsed_days, Some(39));
        assert!(response.rows[0].current_elapsed_seconds.is_some());
        assert_eq!(
            response.rows[0].current_elapsed_since_secs,
            Some(last_review_time.0)
        );

        Ok(())
    }

    #[test]
    fn review_input_rows_leave_missing_review_time_unset() -> Result<()> {
        let mut col = Collection::new();
        col.update_default_deck_config(|config| {
            config.rwkv_review_enabled = true;
        });
        let timing = col.timing_today()?;
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        col.add_card(&mut card)?;

        let response =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0],
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
            })?;

        assert_eq!(response.rows.len(), 1);
        assert_eq!(response.rows[0].current_elapsed_seconds, None);
        assert_eq!(response.rows[0].current_elapsed_since_secs, None);
        Ok(())
    }

    #[test]
    fn review_input_rows_expose_normalized_learning_steps() -> Result<()> {
        let mut col = Collection::new();
        let timing = col.timing_today()?;
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.now.0 as i32);
        card.ctype = CardType::Learn;
        card.queue = CardQueue::Learn;
        card.remaining_steps = 2002;
        card.original_deck_id = DeckId(1);
        col.add_card(&mut card)?;
        let response =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0],
                include_suspended_review: false,
                include_disabled_decks: true,
                include_new_cards: false,
            })?;
        assert_eq!(response.rows.len(), 1);
        assert_eq!(response.rows[0].remaining_steps, Some(2));
        assert!(response.rows[0].in_filtered_deck);
        Ok(())
    }

    #[test]
    fn candidate_metadata_uses_revlog_last_review_time_when_card_data_missing() -> Result<()> {
        let mut col = Collection::new();
        let timing = col.timing_today()?;
        let last_review_time = timing.now.adding_secs(-120);
        let ignored_filtered_time = timing.now.adding_secs(-60);
        let metadata_timing = SchedTimingToday {
            now: timing.now,
            days_elapsed: timing.days_elapsed,
            next_day_at: timing.now.adding_secs(3_600),
        };
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32 + 8);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 4;
        card.desired_retention = Some(0.9);
        col.add_card(&mut card)?;
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId(last_review_time.0 * 1000),
                cid: card.id,
                usn: Usn(0),
                button_chosen: 3,
                interval: 4,
                last_interval: 3,
                ease_factor: 2500,
                review_kind: RevlogReviewKind::Review,
                ..Default::default()
            },
            false,
        )?;
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId(ignored_filtered_time.0 * 1000),
                cid: card.id,
                usn: Usn(0),
                button_chosen: 3,
                review_kind: RevlogReviewKind::Filtered,
                ..Default::default()
            },
            false,
        )?;

        let metadata = rwkv_review_candidate_metadata(&mut col, &[card.id], metadata_timing)?;
        let metadata = metadata.get(&card.id).unwrap();
        assert!(metadata.reviewed_today);
        assert_eq!(metadata.elapsed_secs_since_last_review, Some(120));

        Ok(())
    }

    #[test]
    fn review_input_rows_filter_disabled_decks_before_loading() -> Result<()> {
        let mut col = Collection::new();
        let timing = col.timing_today()?;
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 4;
        card.last_review_time = Some(timing.next_day_at.adding_secs(-4 * 86_400));
        col.add_card(&mut card)?;

        let filtered =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0],
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
            })?;
        assert_eq!(filtered.loaded_cards, 0);
        assert!(filtered.rows.is_empty());

        let included =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0],
                include_suspended_review: false,
                include_disabled_decks: true,
                include_new_cards: false,
            })?;
        assert_eq!(included.loaded_cards, 1);
        assert_eq!(included.rows.len(), 1);

        // kuma3: an approximate queue update can ask for an answered card twice
        let repeated =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0, card.id.0],
                include_suspended_review: false,
                include_disabled_decks: true,
                include_new_cards: false,
            })?;
        assert_eq!(repeated.rows.len(), 1);

        Ok(())
    }

    #[test]
    fn review_input_rows_for_cards_can_include_new_cards() -> Result<()> {
        let mut col = Collection::new();
        col.update_default_deck_config(|config| {
            config.rwkv_review_enabled = true;
            config.rwkv_review_first_review_elapsed_from_card_creation = false;
        });
        let timing = col.timing_today()?;
        let mut card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32);
        col.add_card(&mut card)?;

        let excluded =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0],
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
            })?;
        assert_eq!(excluded.loaded_cards, 0);
        assert!(excluded.rows.is_empty());

        let included =
            col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                card_ids: vec![card.id.0],
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: true,
            })?;
        assert_eq!(included.loaded_cards, 1);
        assert_eq!(included.cards_with_supported_state, 1);
        assert_eq!(included.rows.len(), 1);
        assert_eq!(included.rows[0].card_id, card.id.0);
        assert_eq!(included.rows[0].current_state_kind, "normal");
        assert_eq!(included.rows[0].current_normal_state_kind, "new");
        assert!(included.rows[0].current_elapsed_days.is_some());
        assert!(included.rows[0].current_elapsed_seconds.is_some());
        assert_eq!(
            included.rows[0].current_elapsed_since_secs,
            Some(card.id.as_secs().0)
        );
        Ok(())
    }

    #[test]
    fn new_review_input_uses_card_creation_time() -> Result<()> {
        let col = Collection::new();
        let card = Card {
            id: CardId(1_700_000_000_000),
            ctype: CardType::New,
            queue: CardQueue::New,
            ..Default::default()
        };
        let timing = SchedTimingToday {
            now: TimestampSecs(1_700_086_401),
            days_elapsed: 1,
            next_day_at: TimestampSecs(1_700_100_000),
        };

        let state = col
            .rwkv_review_input_state(&card, timing, false, true)?
            .unwrap();

        assert_eq!(state.elapsed_since, Some(TimestampSecs(1_700_000_000)));
        assert_eq!(state.elapsed_seconds, Some(86_401));
        assert_eq!(state.elapsed_days, Some(1));
        Ok(())
    }

    #[test]
    fn review_input_rows_for_search_uses_search_table() -> Result<()> {
        let mut col = Collection::new();
        col.update_default_deck_config(|config| {
            config.rwkv_review_enabled = true;
            config.rwkv_review_batch_size = 1024;
        });
        let timing = col.timing_today()?;
        let mut review_card = Card::new(NoteId(10), 0, DeckId(1), timing.days_elapsed as i32);
        review_card.ctype = CardType::Review;
        review_card.queue = CardQueue::Review;
        review_card.interval = 4;
        review_card.memory_state = Some(FsrsMemoryState {
            stability: 11.0,
            stability_internal: 10.0,
            stability_fast: Some(6.0),
            difficulty: 5.0,
        });
        review_card.last_review_time = Some(timing.next_day_at.adding_secs(-4 * 86_400));
        col.add_card(&mut review_card)?;
        let mut new_card = Card::new(NoteId(20), 0, DeckId(1), timing.days_elapsed as i32);
        col.add_card(&mut new_card)?;

        let response =
            col.rwkv_review_input_rows_for_search(RwkvReviewInputRowsForSearchRequest {
                search: format!("cid:{},{}", review_card.id.0, new_card.id.0),
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
                include_comparison_data: false,
            })?;

        assert_eq!(response.searched_cards, 2);
        assert_eq!(response.loaded_cards, 1);
        assert_eq!(response.cards_with_supported_state, 1);
        assert_eq!(response.rows.len(), 1);
        assert_eq!(response.rows[0].card_id, review_card.id.0);
        assert!(response.rows[0].fsrs_memory_state.is_none());

        let comparison =
            col.rwkv_review_input_rows_for_search(RwkvReviewInputRowsForSearchRequest {
                search: format!("cid:{}", review_card.id.0),
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
                include_comparison_data: true,
            })?;
        let memory = comparison.rows[0].fsrs_memory_state.as_ref().unwrap();
        assert_eq!(memory.stability_internal, Some(10.0));
        assert_eq!(memory.stability_fast, Some(6.0));
        assert_eq!(memory.difficulty, 5.0);
        assert!(comparison.rows[0].fsrs_historical_retention.is_some());

        let response =
            col.rwkv_review_input_rows_for_search(RwkvReviewInputRowsForSearchRequest {
                search: format!("cid:{},{} is:new", review_card.id.0, new_card.id.0),
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
                include_comparison_data: false,
            })?;

        assert_eq!(response.searched_cards, 1);
        assert_eq!(response.loaded_cards, 1);
        assert_eq!(response.cards_with_supported_state, 1);
        assert_eq!(response.rows.len(), 1);
        let row = &response.rows[0];
        assert_eq!(row.card_id, new_card.id.0);
        assert_eq!(row.current_state_kind, "normal");
        assert_eq!(row.current_normal_state_kind, "new");
        let elapsed_seconds = row.current_elapsed_seconds.unwrap();
        assert_eq!(row.current_elapsed_days, Some(elapsed_seconds / 86_400));
        assert_eq!(
            row.current_elapsed_since_secs,
            Some(new_card.id.as_secs().0)
        );

        let response =
            col.rwkv_review_input_rows_for_search(RwkvReviewInputRowsForSearchRequest {
                search: format!("cid:{} prop:r<0", review_card.id.0),
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
                include_comparison_data: false,
            })?;

        assert_eq!(response.searched_cards, 0);
        assert!(response.rows.is_empty());

        let response =
            col.rwkv_review_input_rows_for_search(RwkvReviewInputRowsForSearchRequest {
                search: format!("cid:{} prop:rwkv:r<0", review_card.id.0),
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
                include_comparison_data: false,
            })?;

        assert_eq!(response.searched_cards, 1);
        assert_eq!(response.rows.len(), 1);
        assert_eq!(response.rows[0].card_id, review_card.id.0);

        let response =
            col.rwkv_review_input_rows_for_search(RwkvReviewInputRowsForSearchRequest {
                search: format!("cid:{} prop:rwkv-curve:r<0", review_card.id.0),
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: false,
                include_comparison_data: false,
            })?;

        assert_eq!(response.searched_cards, 1);
        assert_eq!(response.rows.len(), 1);
        assert_eq!(response.rows[0].card_id, review_card.id.0);

        Ok(())
    }

    #[test]
    fn review_input_rows_for_deck_review_queue_uses_child_decks() -> Result<()> {
        let mut col = Collection::new();
        col.update_default_deck_config(|config| {
            config.rwkv_review_enabled = true;
            config.rwkv_review_batch_size = 1024;
        });
        let parent = col.get_or_create_normal_deck("Parent")?;
        let child = col.get_or_create_normal_deck("Parent::Child")?;
        let timing = col.timing_today()?;
        let last_review_time = timing.next_day_at.adding_secs(-4 * 86_400);
        let mut review_card = Card::new(NoteId(10), 0, child.id, timing.days_elapsed as i32 + 8);
        review_card.ctype = CardType::Review;
        review_card.queue = CardQueue::Review;
        review_card.interval = 4;
        review_card.last_review_time = Some(last_review_time);
        col.add_card(&mut review_card)?;
        let mut new_card = Card::new(NoteId(20), 0, child.id, timing.days_elapsed as i32);
        col.add_card(&mut new_card)?;

        let response = col.rwkv_review_input_rows_for_deck_review_queue(
            RwkvReviewInputRowsForDeckReviewQueueRequest {
                deck_id: parent.id.0,
                include_disabled_decks: false,
                include_new_cards: false,
            },
        )?;

        assert_eq!(response.searched_cards, 1);
        assert_eq!(response.loaded_cards, 1);
        assert_eq!(response.cards_with_supported_state, 1);
        assert_eq!(response.rows.len(), 1);
        assert_eq!(response.rows[0].card_id, review_card.id.0);
        assert_eq!(response.rows[0].deck_id, child.id.0);

        Ok(())
    }

    #[test]
    fn review_input_rows_for_deck_review_queue_can_include_new_cards() -> Result<()> {
        let mut col = Collection::new();
        col.update_default_deck_config(|config| {
            config.rwkv_review_enabled = true;
            config.rwkv_review_first_review_elapsed_from_card_creation = false;
        });
        let deck = col.get_or_create_normal_deck("Default")?;
        let timing = col.timing_today()?;
        let last_review_time = timing.next_day_at.adding_secs(-4 * 86_400);
        let mut review_card = Card::new(NoteId(10), 0, deck.id, timing.days_elapsed as i32 + 8);
        review_card.ctype = CardType::Review;
        review_card.queue = CardQueue::Review;
        review_card.interval = 4;
        review_card.last_review_time = Some(last_review_time);
        col.add_card(&mut review_card)?;
        let mut new_card = Card::new(NoteId(20), 0, deck.id, timing.days_elapsed as i32);
        col.add_card(&mut new_card)?;

        let response = col.rwkv_review_input_rows_for_deck_review_queue(
            RwkvReviewInputRowsForDeckReviewQueueRequest {
                deck_id: deck.id.0,
                include_disabled_decks: false,
                include_new_cards: true,
            },
        )?;

        assert_eq!(response.searched_cards, 2);
        assert_eq!(response.loaded_cards, 2);
        assert_eq!(response.cards_with_supported_state, 2);
        assert_eq!(response.rows.len(), 2);
        let new_row = response
            .rows
            .iter()
            .find(|row| row.card_id == new_card.id.0)
            .unwrap();
        assert_eq!(new_row.current_state_kind, "normal");
        assert_eq!(new_row.current_normal_state_kind, "new");
        let elapsed_seconds = new_row.current_elapsed_seconds.unwrap();
        assert_eq!(new_row.current_elapsed_days, Some(elapsed_seconds / 86_400));
        assert_eq!(
            new_row.current_elapsed_since_secs,
            Some(new_card.id.as_secs().0)
        );

        Ok(())
    }
}
