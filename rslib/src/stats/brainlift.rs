// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::stats::brainlift_evidence_score::Availability;
use anki_proto::stats::brainlift_evidence_score::Confidence;
use anki_proto::stats::brainlift_evidence_score::Scale;
use anki_proto::stats::BrainliftEvidenceScore;
use anki_proto::stats::BrainliftEvidenceThresholds;
use anki_proto::stats::BrainliftScoreRange;
use anki_proto::stats::BrainliftScoreRequest;
use anki_proto::stats::BrainliftScoreSnapshotResponse;
use anki_proto::stats::BrainliftTopic;
use anki_proto::stats::BrainliftTopicMastery;
use unicase::UniCase;

use crate::collection::Collection;
use crate::error::Result;
use crate::invalid_input;
use crate::storage::BrainliftEvidenceRow;
use crate::tags::immediate_parent_name_unicase;
use crate::tags::split_tags;

pub const TOPIC_MIN_REVIEWS: u32 = 2;
pub const MEMORY_MIN_REVIEWS: u32 = 10;
pub const PERFORMANCE_MIN_REVIEWS: u32 = 10;
pub const READINESS_MIN_TOPIC_COVERAGE: f64 = 0.8;
pub const PASSING_BUTTON_MIN: u32 = 2;
const PERFORMANCE_TAG_PREFIX: &str = "brainlift::evidence::performance::";
const READINESS_SCORE_MAPPING_NOT_VALIDATED: &str = "readiness_score_mapping_not_validated";

#[derive(Default)]
struct EvidenceCounts {
    rated: u32,
    successful: u32,
    updated_at_secs: i64,
}

impl EvidenceCounts {
    fn add(&mut self, row: &BrainliftEvidenceRow) {
        self.rated += 1;
        self.successful += u32::from(u32::from(row.button_chosen) >= PASSING_BUTTON_MIN);
        self.updated_at_secs = self.updated_at_secs.max(row.review_id.as_secs().0);
    }
}

#[derive(Default)]
struct TopicCounts {
    memory: EvidenceCounts,
    performance: EvidenceCounts,
}

struct SnapshotAccumulator {
    topics: Vec<BrainliftTopic>,
    topic_by_tag: HashMap<UniCase<String>, usize>,
    counts: Vec<TopicCounts>,
    matched_topics: Vec<bool>,
    matched_topic_indices: Vec<usize>,
    memory: EvidenceCounts,
    performance: EvidenceCounts,
}

impl SnapshotAccumulator {
    fn new(topics: Vec<BrainliftTopic>) -> Self {
        let topic_count = topics.len();
        let topic_by_tag = topics
            .iter()
            .enumerate()
            .map(|(idx, topic)| (UniCase::new(topic.tag.clone()), idx))
            .collect();
        let counts = (0..topics.len()).map(|_| TopicCounts::default()).collect();
        Self {
            topics,
            topic_by_tag,
            counts,
            matched_topics: vec![false; topic_count],
            matched_topic_indices: Vec::new(),
            memory: EvidenceCounts::default(),
            performance: EvidenceCounts::default(),
        }
    }

    fn add(&mut self, row: BrainliftEvidenceRow) {
        let mut performance_cutoff_secs = None;
        for tag in split_tags(&row.tags) {
            let normalized = tag.to_ascii_lowercase();
            if let Some(cutoff) = normalized
                .strip_prefix(PERFORMANCE_TAG_PREFIX)
                .and_then(|cutoff| cutoff.parse::<i64>().ok())
            {
                performance_cutoff_secs = Some(
                    performance_cutoff_secs.map_or(cutoff, |current: i64| current.max(cutoff)),
                );
            }
            let mut candidate = tag;
            loop {
                if let Some(idx) = self.topic_by_tag.get(&UniCase::new(candidate.to_string())) {
                    if !self.matched_topics[*idx] {
                        self.matched_topics[*idx] = true;
                        self.matched_topic_indices.push(*idx);
                    }
                }
                let Some(parent) = immediate_parent_name_unicase(UniCase::new(candidate)) else {
                    break;
                };
                candidate = parent.into_inner();
            }
        }
        if self.matched_topic_indices.is_empty() {
            return;
        }

        let is_performance =
            performance_cutoff_secs.is_some_and(|cutoff| row.review_id.as_secs().0 >= cutoff);
        let aggregate = if is_performance {
            &mut self.performance
        } else {
            &mut self.memory
        };
        aggregate.add(&row);
        while let Some(idx) = self.matched_topic_indices.pop() {
            self.matched_topics[idx] = false;
            let topic = &mut self.counts[idx];
            if is_performance {
                topic.performance.add(&row);
            } else {
                topic.memory.add(&row);
            }
        }
    }

    fn finish(self) -> BrainliftScoreSnapshotResponse {
        let topic_count = self.topics.len();
        let memory_covered = self
            .counts
            .iter()
            .filter(|counts| counts.memory.rated >= TOPIC_MIN_REVIEWS)
            .count();
        let performance_covered = self
            .counts
            .iter()
            .filter(|counts| counts.performance.rated >= TOPIC_MIN_REVIEWS)
            .count();
        let jointly_covered = self
            .counts
            .iter()
            .filter(|counts| {
                counts.memory.rated >= TOPIC_MIN_REVIEWS
                    && counts.performance.rated >= TOPIC_MIN_REVIEWS
            })
            .count();
        let memory_coverage = coverage(memory_covered, topic_count);
        let performance_coverage = coverage(performance_covered, topic_count);
        let readiness_coverage = coverage(jointly_covered, topic_count);
        let memory = evidence_score(
            &self.memory,
            MEMORY_MIN_REVIEWS,
            memory_coverage,
            "memory_from_ordinary_rated_reviews",
        );
        let performance = evidence_score(
            &self.performance,
            PERFORMANCE_MIN_REVIEWS,
            performance_coverage,
            "performance_from_held_out_rated_reviews",
        );
        let readiness = readiness_score(&memory, &performance, readiness_coverage);
        let updated_at_secs = memory
            .updated_at_secs
            .max(performance.updated_at_secs)
            .max(readiness.updated_at_secs);
        let topics = self
            .topics
            .into_iter()
            .zip(self.counts)
            .map(|(topic, counts)| topic_mastery(topic, counts))
            .collect();

        BrainliftScoreSnapshotResponse {
            topics,
            memory: Some(memory),
            performance: Some(performance),
            readiness: Some(readiness),
            thresholds: Some(BrainliftEvidenceThresholds {
                topic_min_reviews: TOPIC_MIN_REVIEWS,
                memory_min_reviews: MEMORY_MIN_REVIEWS,
                performance_min_reviews: PERFORMANCE_MIN_REVIEWS,
                readiness_min_topic_coverage: READINESS_MIN_TOPIC_COVERAGE,
                passing_button_min: PASSING_BUTTON_MIN,
            }),
            updated_at_secs,
            readiness_formula: String::new(),
        }
    }
}

impl Collection {
    pub fn brainlift_score_snapshot(
        &mut self,
        input: BrainliftScoreRequest,
    ) -> Result<BrainliftScoreSnapshotResponse> {
        validate_topics(&input.topics)?;
        let mut accumulator = SnapshotAccumulator::new(input.topics);
        if accumulator.topics.is_empty() {
            return Ok(accumulator.finish());
        }

        self.storage.for_each_brainlift_evidence_row(|row| {
            accumulator.add(row);
            Ok(())
        })?;
        Ok(accumulator.finish())
    }
}

fn validate_topics(topics: &[BrainliftTopic]) -> Result<()> {
    let mut seen = HashMap::new();
    for topic in topics {
        if topic.name.trim().is_empty() || topic.tag.trim().is_empty() {
            invalid_input!("brainlift topics require non-empty names and tags");
        }
        if seen.insert(UniCase::new(topic.tag.clone()), ()).is_some() {
            invalid_input!("brainlift topic tags must be unique");
        }
    }
    Ok(())
}

fn topic_mastery(topic: BrainliftTopic, counts: TopicCounts) -> BrainliftTopicMastery {
    let memory_range = wilson_range(counts.memory.successful, counts.memory.rated);
    let performance_range = wilson_range(counts.performance.successful, counts.performance.rated);
    BrainliftTopicMastery {
        name: topic.name,
        tag: topic.tag,
        rated_reviews: counts.memory.rated,
        successful_reviews: counts.memory.successful,
        mastery: memory_range.as_ref().map_or(0.0, |range| range.lower),
        average_recall: ratio(counts.memory.successful, counts.memory.rated),
        average_recall_range: memory_range,
        covered: counts.memory.rated >= TOPIC_MIN_REVIEWS,
        performance_rated_reviews: counts.performance.rated,
        performance_successful_reviews: counts.performance.successful,
        performance_average: ratio(counts.performance.successful, counts.performance.rated),
        performance_range,
        performance_covered: counts.performance.rated >= TOPIC_MIN_REVIEWS,
    }
}

fn evidence_score(
    counts: &EvidenceCounts,
    minimum: u32,
    coverage: f64,
    source_reason: &str,
) -> BrainliftEvidenceScore {
    let available = counts.rated >= minimum;
    let reasons = if available {
        vec![source_reason.into()]
    } else if counts.rated == 0 {
        vec!["no_qualifying_reviews".into()]
    } else {
        vec![format!("minimum_rated_reviews_not_met:{minimum}")]
    };
    BrainliftEvidenceScore {
        availability: if available {
            Availability::Available as i32
        } else {
            Availability::Abstained as i32
        },
        scale: Scale::Probability as i32,
        estimate: available
            .then(|| ratio(counts.successful, counts.rated))
            .unwrap_or_default(),
        range: available
            .then(|| wilson_range(counts.successful, counts.rated))
            .flatten(),
        coverage,
        confidence: if available {
            confidence(counts.rated, coverage) as i32
        } else {
            Confidence::None as i32
        },
        updated_at_secs: counts.updated_at_secs,
        reasons,
        rated_reviews: counts.rated,
        successful_reviews: counts.successful,
    }
}

fn readiness_score(
    memory: &BrainliftEvidenceScore,
    performance: &BrainliftEvidenceScore,
    coverage: f64,
) -> BrainliftEvidenceScore {
    let memory_available = memory.availability() == Availability::Available;
    let performance_available = performance.availability() == Availability::Available;
    let coverage_available = coverage >= READINESS_MIN_TOPIC_COVERAGE;
    let mut reasons = vec![READINESS_SCORE_MAPPING_NOT_VALIDATED.into()];
    if !memory_available {
        reasons.push("memory_unavailable".into());
    }
    if !performance_available {
        reasons.push("performance_unavailable".into());
    }
    if !coverage_available {
        reasons.push(format!(
            "joint_topic_coverage_below:{READINESS_MIN_TOPIC_COVERAGE}"
        ));
    }

    BrainliftEvidenceScore {
        availability: Availability::Abstained as i32,
        scale: Scale::Mcat as i32,
        estimate: 0.0,
        range: None,
        coverage,
        confidence: Confidence::None as i32,
        updated_at_secs: memory.updated_at_secs.max(performance.updated_at_secs),
        reasons,
        rated_reviews: memory.rated_reviews + performance.rated_reviews,
        successful_reviews: memory.successful_reviews + performance.successful_reviews,
    }
}

fn confidence(rated: u32, coverage: f64) -> Confidence {
    if rated >= 100 && coverage >= 1.0 {
        Confidence::High
    } else if rated >= 30 && coverage >= READINESS_MIN_TOPIC_COVERAGE {
        Confidence::Medium
    } else {
        Confidence::Low
    }
}

fn coverage(covered: usize, topics: usize) -> f64 {
    if topics == 0 {
        0.0
    } else {
        covered as f64 / topics as f64
    }
}

fn ratio(successful: u32, rated: u32) -> f64 {
    if rated == 0 {
        0.0
    } else {
        f64::from(successful) / f64::from(rated)
    }
}

fn wilson_range(successful: u32, rated: u32) -> Option<BrainliftScoreRange> {
    if rated == 0 {
        return None;
    }
    let n = f64::from(rated);
    let p = f64::from(successful) / n;
    let z = 1.959_963_984_540_054;
    let z_squared = z * z;
    let denominator = 1.0 + z_squared / n;
    let center = (p + z_squared / (2.0 * n)) / denominator;
    let margin = z * ((p * (1.0 - p) / n + z_squared / (4.0 * n * n)).sqrt()) / denominator;
    Some(BrainliftScoreRange {
        lower: (center - margin).max(0.0),
        upper: (center + margin).min(1.0),
    })
}

#[cfg(test)]
mod tests {
    use anki_proto::stats::brainlift_evidence_score::Availability;
    use anki_proto::stats::BrainliftScoreRequest;
    use anki_proto::stats::BrainliftTopic;

    use super::MEMORY_MIN_REVIEWS;
    use super::PERFORMANCE_MIN_REVIEWS;
    use super::READINESS_MIN_TOPIC_COVERAGE;
    use super::READINESS_SCORE_MAPPING_NOT_VALIDATED;
    use crate::collection::Collection;
    use crate::decks::DeckId;
    use crate::prelude::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogId;
    use crate::revlog::RevlogReviewKind;

    const BIOLOGY_TAG: &str = "mcat::biology";
    const CHEMISTRY_TAG: &str = "mcat::chemistry";
    const PERFORMANCE_TAG: &str = "brainlift::evidence::performance::0";

    #[test]
    fn empty_snapshot_abstains_and_returns_requested_topics() -> Result<()> {
        let mut col = Collection::new();

        let snapshot = col.brainlift_score_snapshot(request())?;

        assert_eq!(snapshot.topics.len(), 2);
        assert_eq!(snapshot.topics[0].name, "Biology");
        assert!(!snapshot.topics[0].covered);
        assert_eq!(
            snapshot.memory.unwrap().availability(),
            Availability::Abstained
        );
        assert_eq!(
            snapshot.performance.unwrap().availability(),
            Availability::Abstained
        );
        let readiness = snapshot.readiness.unwrap();
        assert_eq!(readiness.availability(), Availability::Abstained);
        assert_eq!(readiness.estimate, 0.0);
        assert!(readiness.range.is_none());
        assert_eq!(readiness.confidence(), super::Confidence::None);
        assert!(readiness
            .reasons
            .contains(&READINESS_SCORE_MAPPING_NOT_VALIDATED.into()));
        assert!(snapshot.readiness_formula.is_empty());
        Ok(())
    }

    #[test]
    fn snapshot_separates_memory_performance_and_readiness() -> Result<()> {
        let mut col = Collection::new();
        let biology = add_tagged_card(&mut col, &[BIOLOGY_TAG]);
        let chemistry = add_tagged_card(&mut col, &[CHEMISTRY_TAG, PERFORMANCE_TAG]);

        add_reviews(
            &mut col,
            biology,
            MEMORY_MIN_REVIEWS,
            MEMORY_MIN_REVIEWS - 2,
            1_700_000_000_000,
            RevlogReviewKind::Review,
        )?;
        add_reviews(
            &mut col,
            chemistry,
            PERFORMANCE_MIN_REVIEWS,
            PERFORMANCE_MIN_REVIEWS - 1,
            1_700_000_100_000,
            RevlogReviewKind::Review,
        )?;
        add_reviews(
            &mut col,
            biology,
            2,
            2,
            1_700_000_200_000,
            RevlogReviewKind::Manual,
        )?;

        let snapshot = col.brainlift_score_snapshot(request())?;
        let memory = snapshot.memory.unwrap();
        let performance = snapshot.performance.unwrap();
        let readiness = snapshot.readiness.unwrap();

        assert_eq!(memory.availability(), Availability::Available);
        assert_eq!(memory.rated_reviews, MEMORY_MIN_REVIEWS);
        assert_eq!(performance.availability(), Availability::Available);
        assert_eq!(performance.rated_reviews, PERFORMANCE_MIN_REVIEWS);
        assert_eq!(readiness.availability(), Availability::Abstained);
        assert_eq!(readiness.estimate, 0.0);
        assert!(readiness.range.is_none());
        assert!(readiness
            .reasons
            .contains(&READINESS_SCORE_MAPPING_NOT_VALIDATED.into()));
        assert!(readiness.reasons.contains(&format!(
            "joint_topic_coverage_below:{READINESS_MIN_TOPIC_COVERAGE}"
        )));
        assert_eq!(snapshot.topics[0].rated_reviews, MEMORY_MIN_REVIEWS);
        assert_eq!(snapshot.topics[1].rated_reviews, 0);
        assert_eq!(snapshot.thresholds.unwrap().topic_min_reviews, 2);
        Ok(())
    }

    #[test]
    fn snapshot_is_read_only_and_preserves_undo_and_integrity() -> Result<()> {
        let mut col = Collection::new();
        let card_id = add_tagged_card(&mut col, &[BIOLOGY_TAG]);
        add_reviews(
            &mut col,
            card_id,
            MEMORY_MIN_REVIEWS,
            MEMORY_MIN_REVIEWS,
            1_700_000_300_000,
            RevlogReviewKind::Review,
        )?;

        let before_timestamps = col.storage.get_collection_timestamps()?;
        let before_undo = col.undo_status();
        let before_cards: i64 = col.storage.db_scalar("select count(*) from cards")?;
        let before_revlog: i64 = col.storage.db_scalar("select count(*) from revlog")?;

        let _snapshot = col.brainlift_score_snapshot(request())?;

        let after_timestamps = col.storage.get_collection_timestamps()?;
        let after_undo = col.undo_status();
        assert_eq!(
            before_timestamps.collection_change,
            after_timestamps.collection_change
        );
        assert_eq!(
            before_timestamps.schema_change,
            after_timestamps.schema_change
        );
        assert_eq!(before_undo.undo, after_undo.undo);
        assert_eq!(before_undo.redo, after_undo.redo);
        assert_eq!(before_undo.last_step, after_undo.last_step);
        let after_cards: i64 = col.storage.db_scalar("select count(*) from cards")?;
        let after_revlog: i64 = col.storage.db_scalar("select count(*) from revlog")?;
        assert_eq!(before_cards, after_cards);
        assert_eq!(before_revlog, after_revlog);
        assert_eq!(
            col.storage.db_scalar::<String>("pragma integrity_check")?,
            "ok"
        );
        Ok(())
    }

    #[test]
    fn readiness_abstains_without_a_validated_score_mapping() -> Result<()> {
        let mut col = Collection::new();
        let biology_memory = add_tagged_card(&mut col, &[BIOLOGY_TAG]);
        let chemistry_memory = add_tagged_card(&mut col, &[CHEMISTRY_TAG]);
        let biology_performance = add_tagged_card(&mut col, &[BIOLOGY_TAG, PERFORMANCE_TAG]);
        let chemistry_performance = add_tagged_card(&mut col, &[CHEMISTRY_TAG, PERFORMANCE_TAG]);

        add_reviews(
            &mut col,
            biology_memory,
            5,
            4,
            1_700_000_400_000,
            RevlogReviewKind::Review,
        )?;
        add_reviews(
            &mut col,
            chemistry_memory,
            5,
            4,
            1_700_000_500_000,
            RevlogReviewKind::Review,
        )?;
        add_reviews(
            &mut col,
            biology_performance,
            5,
            3,
            1_700_000_600_000,
            RevlogReviewKind::Review,
        )?;
        add_reviews(
            &mut col,
            chemistry_performance,
            5,
            3,
            1_700_000_700_000,
            RevlogReviewKind::Review,
        )?;

        let snapshot = col.brainlift_score_snapshot(request())?;
        let readiness = snapshot.readiness.unwrap();
        assert_eq!(readiness.availability(), Availability::Abstained);
        assert_eq!(readiness.coverage, 1.0);
        assert_eq!(readiness.estimate, 0.0);
        assert!(readiness.range.is_none());
        assert_eq!(readiness.confidence(), super::Confidence::None);
        assert_eq!(readiness.reasons, [READINESS_SCORE_MAPPING_NOT_VALIDATED]);
        assert!(snapshot.readiness_formula.is_empty());
        Ok(())
    }

    #[test]
    fn performance_marker_cutoff_does_not_reclassify_older_reviews() -> Result<()> {
        let mut col = Collection::new();
        let card_id = add_tagged_card(
            &mut col,
            &[BIOLOGY_TAG, "brainlift::evidence::performance::1700000100"],
        );
        add_reviews(
            &mut col,
            card_id,
            2,
            2,
            1_700_000_000_000,
            RevlogReviewKind::Review,
        )?;
        add_reviews(
            &mut col,
            card_id,
            3,
            3,
            1_700_000_200_000,
            RevlogReviewKind::Review,
        )?;

        let snapshot = col.brainlift_score_snapshot(request())?;

        assert_eq!(snapshot.memory.unwrap().rated_reviews, 2);
        assert_eq!(snapshot.performance.unwrap().rated_reviews, 3);
        Ok(())
    }

    fn request() -> BrainliftScoreRequest {
        BrainliftScoreRequest {
            topics: vec![
                BrainliftTopic {
                    name: "Biology".into(),
                    tag: BIOLOGY_TAG.into(),
                },
                BrainliftTopic {
                    name: "Chemistry".into(),
                    tag: CHEMISTRY_TAG.into(),
                },
            ],
        }
    }

    fn add_tagged_card(col: &mut Collection, tags: &[&str]) -> CardId {
        let mut note = col.basic_notetype().new_note();
        note.tags = tags.iter().map(ToString::to_string).collect();
        col.add_note(&mut note, DeckId(1)).unwrap();
        col.storage.card_ids_of_notes(&[note.id]).unwrap()[0]
    }

    fn add_reviews(
        col: &mut Collection,
        card_id: CardId,
        count: u32,
        passing: u32,
        first_id: i64,
        review_kind: RevlogReviewKind,
    ) -> Result<()> {
        for idx in 0..count {
            col.storage.add_revlog_entry(
                &RevlogEntry {
                    id: RevlogId(first_id + i64::from(idx)),
                    cid: card_id,
                    button_chosen: if idx < passing { 3 } else { 1 },
                    review_kind,
                    ..Default::default()
                },
                false,
            )?;
        }
        Ok(())
    }
}
