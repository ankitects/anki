// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;
use std::collections::HashSet;

use crate::collection::RwkvCountCards;
use crate::deckconfig::DeckConfig;
use crate::deckconfig::DeckConfigId;
use crate::decks::limits::LimitTreeMap;
use crate::prelude::*;
use crate::scheduler::rwkv::rwkv_review_candidate_metadata;
use crate::scheduler::rwkv::rwkv_review_score_eligibility;
use crate::scheduler::rwkv::rwkv_review_score_eligibility_ignoring_retention;
use crate::scheduler::rwkv::RwkvReviewCandidateMetadata;
use crate::scheduler::rwkv::RwkvReviewScoreEligibility;
use crate::scheduler::timing::SchedTimingToday;

#[derive(Debug, Clone, Default)]
pub(crate) struct DueCounts {
    pub new: u32,
    pub review: u32,
    pub review_limit_exempt: u32,
    /// interday+intraday
    pub learning: u32,

    pub intraday_learning: u32,
    pub interday_learning: u32,
    pub interday_learning_limit_exempt: u32,
    pub total_cards: u32,
}

pub(crate) type ScopedDueCounts = HashMap<DeckId, HashMap<DeckId, DueCounts>>;

struct RwkvReviewCountContext<'a> {
    decks: &'a HashMap<DeckId, Deck>,
    configs: &'a HashMap<DeckConfigId, DeckConfig>,
    timing: SchedTimingToday,
    filtered_review_counts: &'a [(DeckId, u32)],
}

impl Deck {
    /// Return the studied counts if studied today.
    /// May be negative if user has extended limits.
    pub(crate) fn new_rev_counts(&self, today: u32) -> (i32, i32) {
        if self.common.last_day_studied == today {
            (self.common.new_studied, self.common.review_studied)
        } else {
            (0, 0)
        }
    }
}

impl Collection {
    /// Get due counts for decks at the given timestamp.
    pub(crate) fn due_counts(
        &mut self,
        timing: SchedTimingToday,
        learn_cutoff: u32,
    ) -> Result<HashMap<DeckId, DueCounts>> {
        self.storage.due_counts(
            timing.days_elapsed,
            learn_cutoff,
            timing.next_day_at.adding_secs(-86_400),
            timing.next_day_at,
        )
    }

    pub(crate) fn rwkv_review_queue_counts(
        &mut self,
        counts: &HashMap<DeckId, DueCounts>,
        decks: &HashMap<DeckId, Deck>,
        configs: &HashMap<DeckConfigId, DeckConfig>,
        timing: SchedTimingToday,
    ) -> Result<ScopedDueCounts> {
        let deck_count_scores = self.take_rwkv_deck_count_scores_for_day(timing.days_elapsed);
        if !deck_count_scores.is_empty() {
            let filtered_review_counts = self.storage.filtered_review_counts_by_original_deck()?;
            let context = RwkvReviewCountContext {
                decks,
                configs,
                timing,
                filtered_review_counts: &filtered_review_counts,
            };
            // Child deck scopes repeat their parent's cards, so card metadata is
            // loaded once for every scope that needs it.
            let scored_ids: Vec<_> = deck_count_scores
                .iter()
                .filter(|(&score_deck_id, _)| {
                    rwkv_scope_order_settings(&context, score_deck_id).is_some()
                })
                .flat_map(|(_, scores)| scores.keys().copied())
                .collect::<HashSet<_>>()
                .into_iter()
                .collect();
            let result =
                rwkv_review_candidate_metadata(self, &scored_ids, timing).and_then(|metadata| {
                    deck_count_scores
                        .iter()
                        .map(|(&score_deck_id, scores)| {
                            let scoped_counts = self.rwkv_score_scope_counts(
                                counts,
                                &context,
                                score_deck_id,
                                scores,
                                &metadata,
                            )?;
                            Ok((score_deck_id, scoped_counts))
                        })
                        .collect()
                });
            self.restore_rwkv_deck_count_scores(timing.days_elapsed, deck_count_scores);
            return result;
        }

        if let Some((score_deck_id, scores)) =
            self.rwkv_review_queue_scores_for_day(timing.days_elapsed)
        {
            let filtered_review_counts = self.storage.filtered_review_counts_by_original_deck()?;
            let context = RwkvReviewCountContext {
                decks,
                configs,
                timing,
                filtered_review_counts: &filtered_review_counts,
            };
            let metadata = if rwkv_scope_order_settings(&context, score_deck_id).is_some() {
                let scored_ids: Vec<_> = scores.keys().copied().collect();
                rwkv_review_candidate_metadata(self, &scored_ids, timing)?
            } else {
                HashMap::new()
            };
            let scoped_counts =
                self.rwkv_score_scope_counts(counts, &context, score_deck_id, &scores, &metadata)?;
            return Ok(HashMap::from([(score_deck_id, scoped_counts)]));
        }

        Ok(HashMap::new())
    }

    fn rwkv_score_scope_counts(
        &mut self,
        counts: &HashMap<DeckId, DueCounts>,
        context: &RwkvReviewCountContext<'_>,
        score_deck_id: DeckId,
        scores: &HashMap<CardId, crate::collection::RwkvReviewQueueScoreEntry>,
        metadata: &HashMap<CardId, RwkvReviewCandidateMetadata>,
    ) -> Result<HashMap<DeckId, DueCounts>> {
        let Some(root_deck) = context.decks.get(&score_deck_id) else {
            return Ok(HashMap::new());
        };
        let mut decks = self.storage.child_decks(root_deck)?;
        decks.insert(0, root_deck.clone());
        let scope_deck_ids: HashSet<_> = decks.iter().map(|deck| deck.id).collect();
        let mut counts: HashMap<_, _> = scope_deck_ids
            .iter()
            .filter_map(|deck_id| counts.get(deck_id).map(|counts| (*deck_id, counts.clone())))
            .collect();
        let Some((min_intervening_reviews, min_elapsed_secs)) =
            rwkv_scope_order_settings(context, score_deck_id)
        else {
            return Ok(counts);
        };

        let mut pull_candidates = Vec::new();
        let mut waiting = HashSet::new();
        for (card_id, score) in scores {
            let Some(metadata) = metadata.get(card_id) else {
                continue;
            };
            if !scope_deck_ids.contains(&metadata.current_deck_id) {
                continue;
            }
            if context
                .decks
                .get(&metadata.current_deck_id)
                .is_some_and(Deck::is_filtered)
            {
                continue;
            }

            let eligibility = rwkv_review_score_eligibility(
                score.retrievability,
                metadata,
                min_intervening_reviews,
                min_elapsed_secs,
                score.intervening_reviews,
                score.target_retention,
            );
            let rwkv_due = matches!(eligibility, RwkvReviewScoreEligibility::Eligible);
            if !rwkv_due
                && score
                    .target_retention
                    .is_some_and(|target| score.retrievability < target)
            {
                waiting.insert(*card_id);
            }
            let Some(counts) = counts.get_mut(&metadata.current_deck_id) else {
                continue;
            };
            let same_day_ignore_review_limit = context
                .decks
                .get(&metadata.source_deck_id)
                .and_then(Deck::config_id)
                .and_then(|config_id| context.configs.get(&config_id))
                .is_some_and(|config| config.inner.same_day_reviews_ignore_review_limit)
                && metadata.reviewed_today;
            if rwkv_due != metadata.fsrs_due_today {
                if rwkv_due {
                    counts.review = counts.review.saturating_add(1);
                    if same_day_ignore_review_limit {
                        counts.review_limit_exempt = counts.review_limit_exempt.saturating_add(1);
                    }
                } else {
                    counts.review = counts.review.saturating_sub(1);
                    if same_day_ignore_review_limit {
                        counts.review_limit_exempt = counts.review_limit_exempt.saturating_sub(1);
                    }
                }
            }

            if !same_day_ignore_review_limit
                && matches!(eligibility, RwkvReviewScoreEligibility::Blocked)
                && matches!(
                    rwkv_review_score_eligibility_ignoring_retention(
                        score.retrievability,
                        metadata,
                        min_intervening_reviews,
                        min_elapsed_secs,
                        score.intervening_reviews,
                    ),
                    RwkvReviewScoreEligibility::Eligible
                )
            {
                pull_candidates.push((*card_id, score.retrievability, metadata.current_deck_id));
            }
        }

        // Counts are uncapped by ancestors here; the deck-tree aggregation
        // applies ancestor limits after calculating this study scope.
        let mut minimums = LimitTreeMap::build(
            &decks,
            context.configs,
            context.timing.days_elapsed,
            false,
            score_deck_id,
        );
        for &(original_deck_id, count) in context.filtered_review_counts {
            minimums.reserve_rwkv_reviews_if_present(original_deck_id, count);
        }
        // Interday learning reserves capacity before ordinary reviews in the
        // queue. Reviews blocked by a subdeck maximum cannot meet a minimum.
        for interday in [true, false] {
            for deck in &decks {
                if deck.is_filtered() {
                    continue;
                }
                if let Some(count) = counts.get(&deck.id) {
                    let count = if interday {
                        count
                            .interday_learning
                            .saturating_sub(count.interday_learning_limit_exempt)
                    } else {
                        count.review.saturating_sub(count.review_limit_exempt)
                    };
                    minimums.reserve_counted_rwkv_reviews(deck.id, count)?;
                }
            }
        }

        pull_candidates.sort_unstable_by(|(card_a, score_a, _), (card_b, score_b, _)| {
            score_a.total_cmp(score_b).then_with(|| card_a.cmp(card_b))
        });
        let mut minimum = HashSet::new();
        for depth in minimums.rwkv_review_minimum_depths() {
            for &(card_id, _, deck_id) in &pull_candidates {
                if minimum.contains(&card_id)
                    || !minimums.rwkv_review_minimum_remaining_at_depth(deck_id, depth)?
                {
                    continue;
                }
                if let Some(counts) = counts.get_mut(&deck_id) {
                    if minimums.reserve_counted_rwkv_reviews(deck_id, 1)? == 0 {
                        continue;
                    }
                    counts.review = counts.review.saturating_add(1);
                    minimum.insert(card_id);
                }
            }
        }
        self.state
            .rwkv_count_cards
            .insert(score_deck_id, RwkvCountCards { minimum, waiting });

        Ok(counts)
    }

    pub(crate) fn counts_for_deck_today(
        &mut self,
        did: DeckId,
    ) -> Result<anki_proto::scheduler::CountsForDeckTodayResponse> {
        let today = self.current_due_day(0)?;
        let mut deck = self.storage.get_deck(did)?.or_not_found(did)?;
        deck.reset_stats_if_day_changed(today);
        Ok(anki_proto::scheduler::CountsForDeckTodayResponse {
            new: deck.common.new_studied,
            review: deck.common.review_studied,
        })
    }
}

/// The scope deck's RWKV repeat-spacing settings, or None when instant RWKV
/// ordering is off and the FSRS counts stand.
fn rwkv_scope_order_settings(
    context: &RwkvReviewCountContext<'_>,
    score_deck_id: DeckId,
) -> Option<(u32, u32)> {
    context
        .decks
        .get(&score_deck_id)
        .and_then(|deck| deck.config_id())
        .and_then(|config_id| context.configs.get(&config_id))
        .filter(|config| config.inner.rwkv_review_instant_order_enabled)
        .map(|config| {
            (
                config.inner.rwkv_review_min_intervening_reviews,
                config.inner.rwkv_review_min_elapsed_secs,
            )
        })
}
