// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use fsrs::compute_parameters;
use fsrs::compute_parameters_from_card_prefixes;
use fsrs::CardPrefix;
use fsrs::CardPrefixes;
use fsrs::CardReviews;
use fsrs::ComputeParametersInput;
use fsrs::ComputeParametersVersion;
use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::FSRS;
use itertools::Itertools;

use super::filtered_reviews_for_fsrs_with_progress;
use super::fsrs_review_retrievability_progress_wants_abort;
use super::FsrsReviewRetrievabilityProgress;
use super::TrainingItemsForFsrs;
use super::TrainingTargetCounts;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::scheduler::fsrs::legacy_fsrs_params;

/// Training targets in review order, with each card's history stored once.
#[derive(Clone, Default)]
pub(crate) struct CompactTrainingItems {
    pub(super) prefixes: CardPrefixes,
    pub(super) revlog_ids: Vec<RevlogId>,
}

impl CompactTrainingItems {
    pub(super) fn len(&self) -> usize {
        self.prefixes.prefixes.len()
    }

    pub(super) fn is_empty(&self) -> bool {
        self.prefixes.prefixes.is_empty()
    }

    pub(super) fn reviews(&self, prefix: CardPrefix) -> &[FSRSReview] {
        &self.prefixes.cards[prefix.card_index as usize].reviews[..prefix.len as usize]
    }

    pub(super) fn target_counts(&self) -> TrainingTargetCounts {
        let total_targets = self.len();
        let long_term_targets = self
            .prefixes
            .prefixes
            .iter()
            .filter(|&&prefix| {
                self.reviews(prefix)
                    .last()
                    .is_some_and(|r| r.delta_t >= 1.0)
            })
            .count();
        TrainingTargetCounts {
            total_targets,
            long_term_targets,
            short_term_targets: total_targets - long_term_targets,
        }
    }

    pub(super) fn without_same_day_targets(&self) -> Self {
        let mut filtered = Self {
            prefixes: CardPrefixes {
                cards: self.prefixes.cards.clone(),
                prefixes: Vec::new(),
            },
            revlog_ids: Vec::new(),
        };
        for (&prefix, &revlog_id) in self.prefixes.prefixes.iter().zip_eq(&self.revlog_ids) {
            if self
                .reviews(prefix)
                .last()
                .is_some_and(|r| r.delta_t >= 1.0)
            {
                filtered.prefixes.prefixes.push(prefix);
                filtered.revlog_ids.push(revlog_id);
            }
        }
        filtered
    }

    pub(super) fn expanded(&self) -> TrainingItemsForFsrs {
        let (items, card_ids): (Vec<_>, Vec<_>) = self
            .prefixes
            .prefixes
            .iter()
            .map(|&prefix| {
                (
                    FSRSItem {
                        reviews: self.reviews(prefix).to_vec(),
                    },
                    self.prefixes.cards[prefix.card_index as usize].card_id,
                )
            })
            .unzip();
        TrainingItemsForFsrs::with_card_and_revlog_ids(items, card_ids, self.revlog_ids.clone())
    }
}

pub(super) fn compute_compact_parameters(
    prefixes: &CardPrefixes,
    mut input: ComputeParametersInput,
) -> Result<Vec<f32>> {
    if input.model_version == ComputeParametersVersion::Fsrs7 {
        Ok(compute_parameters_from_card_prefixes(prefixes, input)?)
    } else {
        (input.train_set, input.card_ids) = (
            prefixes
                .prefixes
                .iter()
                .map(|prefix| FSRSItem {
                    reviews: prefixes.cards[prefix.card_index as usize].reviews
                        [..prefix.len as usize]
                        .to_vec(),
                })
                .collect(),
            Some(
                prefixes
                    .prefixes
                    .iter()
                    .map(|prefix| prefixes.cards[prefix.card_index as usize].card_id)
                    .collect(),
            ),
        );
        Ok(compute_parameters(input)?)
    }
}

pub(super) fn compact_fsrs_items_for_training(
    revlogs: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    review_revlogs_before: TimestampMillis,
    include_same_day: bool,
) -> Result<CompactTrainingItems> {
    compact_fsrs_items_for_training_with_progress(
        revlogs,
        next_day_at,
        review_revlogs_before,
        include_same_day,
        || Ok(()),
    )
}

pub(super) fn compact_fsrs_items_for_training_with_progress(
    revlogs: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    review_revlogs_before: TimestampMillis,
    include_same_day: bool,
    mut check_cancelled: impl FnMut() -> Result<()>,
) -> Result<CompactTrainingItems> {
    let mut cards = Vec::new();
    let mut targets = Vec::new();
    for (cid, entries) in &revlogs.into_iter().chunk_by(|r| r.cid) {
        check_cancelled()?;
        let Some(filtered) = filtered_reviews_for_fsrs_with_progress(
            entries.collect(),
            next_day_at,
            true,
            review_revlogs_before,
            include_same_day,
            &mut check_cancelled,
        )?
        else {
            continue;
        };
        let card_index = u32::try_from(cards.len()).map_err(|_| fsrs::FSRSError::InvalidInput)?;
        let mut reviews = Vec::with_capacity(filtered.entries.len());
        let first_target = targets.len();
        for (idx, (entry, delta_t)) in filtered
            .entries
            .into_iter()
            .zip_eq(filtered.delta_ts)
            .enumerate()
        {
            if idx % 128 == 0 {
                check_cancelled()?;
            }
            reviews.push(FSRSReview {
                rating: entry.button_chosen as u32,
                delta_t,
            });
            if idx >= 1 && (delta_t > 0.0 || include_same_day) {
                let len =
                    u32::try_from(reviews.len()).map_err(|_| fsrs::FSRSError::InvalidInput)?;
                targets.push((entry.id, CardPrefix { card_index, len }));
            }
        }
        if targets.len() != first_target {
            cards.push(CardReviews {
                card_id: cid.0,
                reviews,
            });
        }
    }
    targets.sort_by_key(|(revlog_id, _)| revlog_id.0);
    let (revlog_ids, prefixes) = targets.into_iter().unzip();
    Ok(CompactTrainingItems {
        prefixes: CardPrefixes { cards, prefixes },
        revlog_ids,
    })
}

pub(super) fn fsrs_review_retrievability_predictions(
    params: &[f32],
    items: &CompactTrainingItems,
    range: std::ops::Range<usize>,
    progress: Option<&FsrsReviewRetrievabilityProgress>,
) -> Result<Vec<(RevlogId, f32)>> {
    if fsrs_review_retrievability_progress_wants_abort(progress) {
        return Err(AnkiError::Interrupted);
    }
    if range.is_empty() {
        return Ok(Vec::new());
    }

    let predictions = FSRS::new(legacy_fsrs_params(params))?
        .predict_retrievability_from_card_prefixes(
            &items.prefixes.cards,
            &items.prefixes.prefixes[range.clone()],
            |_| !fsrs_review_retrievability_progress_wants_abort(progress),
        )?;
    Ok(items.revlog_ids[range]
        .iter()
        .copied()
        .zip_eq(predictions)
        .filter(|(_, prediction)| prediction.is_finite() && (0.0..=1.0).contains(prediction))
        .collect())
}
