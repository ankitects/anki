// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Concept-aware Need-to-Review (NTR) engine for the MCAT fork.
//!
//! This is a deterministic layer on top of FSRS. There is NO AI here: a card's
//! "concepts" are derived from its hierarchical tags using a caller-supplied
//! taxonomy, and a per-concept Need-to-Review signal is computed purely from
//! FSRS retrievability and topic weights.
//!
//! Why this lives in Rust and not Python: it must scan up to ~50k cards (and
//! their tags) and compute FSRS retrievability per card to power a dashboard
//! within the latency budget; the same engine ships unchanged to the phone
//! build; and the protobuf contract gives every client a type-safe API. See
//! the fork notes for the full rationale.
//!
//! These RPCs are read-only: they never mutate the collection or FSRS
//! intervals, so undo is unaffected.

mod service;

use fsrs::FSRS;
use fsrs::FSRS5_DEFAULT_DECAY;

use crate::card::FsrsMemoryState;
use crate::prelude::*;
use crate::scheduler::timing::SchedTimingToday;
use crate::tags::split_tags;

/// A card is "mastered" for a concept when its FSRS retrievability is at least
/// this. Stated as a constant so the threshold is explicit and testable.
pub const MASTERY_RETRIEVABILITY_THRESHOLD: f32 = 0.9;

/// Default topic weight applied when a rule supplies a non-positive weight.
pub const DEFAULT_TOPIC_WEIGHT: f64 = 1.0;

/// A single normalized concept rule (the engine-side view of a proto
/// `ConceptRule`).
#[derive(Debug, Clone)]
pub struct ConceptRule {
    pub id: String,
    pub topic_weight: f64,
    pub tag_patterns: Vec<String>,
}

impl ConceptRule {
    fn weight(&self) -> f64 {
        if self.topic_weight > 0.0 {
            self.topic_weight
        } else {
            DEFAULT_TOPIC_WEIGHT
        }
    }
}

/// The taxonomy passed in by the caller. The engine never reads this from a
/// file; it is always supplied per request, keeping the engine decoupled from
/// the taxonomy data (owned by another layer).
#[derive(Debug, Clone, Default)]
pub struct Taxonomy {
    pub rules: Vec<ConceptRule>,
}

/// Does a single tag match a single pattern?
///
/// Matching rules (case-insensitive):
/// - `*` anywhere acts as a wildcard boundary; a trailing `*` is a prefix match
///   (e.g. `mcat::bio*`).
/// - Otherwise the tag matches if it equals the pattern, or the pattern is a
///   hierarchical prefix of the tag (e.g. pattern `mcat::bio` matches tag
///   `mcat::bio::cell`). Anki uses `::` as the tag hierarchy separator.
fn tag_matches_pattern(tag: &str, pattern: &str) -> bool {
    if pattern.is_empty() {
        return false;
    }
    let tag_l = tag.to_lowercase();
    let pat_l = pattern.to_lowercase();

    if let Some(prefix) = pat_l.strip_suffix('*') {
        // Wildcard / prefix pattern.
        let prefix = prefix.trim_end_matches("::");
        if prefix.is_empty() {
            return true;
        }
        return tag_l == prefix || tag_l.starts_with(&format!("{prefix}::"));
    }

    if pat_l.contains('*') {
        // Generic wildcard: split on '*' and require each fragment present in
        // order. Keeps things deterministic without pulling in a regex dep.
        return glob_contains(&tag_l, &pat_l);
    }

    // Exact match or hierarchical-prefix match.
    tag_l == pat_l || tag_l.starts_with(&format!("{pat_l}::"))
}

/// Ordered-fragment wildcard match for patterns containing internal `*`.
fn glob_contains(tag: &str, pattern: &str) -> bool {
    let mut pos = 0usize;
    let fragments: Vec<&str> = pattern.split('*').collect();
    let last = fragments.len() - 1;
    for (i, frag) in fragments.iter().enumerate() {
        if frag.is_empty() {
            continue;
        }
        if i == 0 {
            if !tag[pos..].starts_with(frag) {
                return false;
            }
            pos += frag.len();
        } else if i == last {
            return tag[pos..].ends_with(frag);
        } else if let Some(found) = tag[pos..].find(frag) {
            pos += found + frag.len();
        } else {
            return false;
        }
    }
    true
}

/// Given the taxonomy and a card's tags, yield the set of matching concept ids,
/// in taxonomy order and de-duplicated. A card may match zero, one, or many
/// concepts.
pub fn concepts_for_tags(taxonomy: &Taxonomy, tags: &[String]) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for rule in &taxonomy.rules {
        let matched = rule
            .tag_patterns
            .iter()
            .any(|pat| tags.iter().any(|tag| tag_matches_pattern(tag, pat)));
        if matched && !out.contains(&rule.id) {
            out.push(rule.id.clone());
        }
    }
    out
}

/// Per-card data needed by the concept engine. `retrievability` is `None` when
/// the card has no FSRS memory state (treated as unknown / max-priority weak).
#[derive(Debug, Clone)]
pub struct CardConcepts {
    pub card_id: CardId,
    pub concepts: Vec<String>,
    pub retrievability: Option<f32>,
}

/// Aggregated practice-question performance for a single concept — the MCAT
/// fork's small "Applying" signal. Supplied by the caller per request (the
/// engine never stores it). Blended into NTR alongside card recall.
#[derive(Debug, Clone, Copy, Default, PartialEq)]
pub struct QuestionStat {
    pub attempts: u32,
    pub correct: u32,
}

impl QuestionStat {
    /// Accuracy in [0, 1]; 0.0 when there are no attempts.
    fn accuracy(&self) -> f64 {
        if self.attempts == 0 {
            0.0
        } else {
            (self.correct.min(self.attempts) as f64) / self.attempts as f64
        }
    }

    /// Number of wrong answers (clamped so `correct > attempts` can't go
    /// negative).
    fn wrong(&self) -> f64 {
        self.attempts.saturating_sub(self.correct) as f64
    }
}

/// Lookup of concept id -> question performance. Empty means "no question
/// evidence anywhere", which reduces NTR to the original card-only behaviour.
pub type QuestionStats = std::collections::HashMap<String, QuestionStat>;

/// Accumulator for a single concept while aggregating cards.
#[derive(Debug, Default, Clone)]
struct ConceptAccumulator {
    cards_total: u32,
    cards_mastered: u32,
    recall_sum: f64,
    recall_count: u32,
}

/// Computed mastery for one concept.
#[derive(Debug, Clone, PartialEq)]
pub struct ConceptMastery {
    pub concept_id: String,
    pub cards_total: u32,
    pub cards_mastered: u32,
    pub avg_recall: f64,
    pub ntr: f64,
    pub topic_weight: f64,
    pub questions_total: u32,
    pub questions_correct: u32,
    pub question_accuracy: f64,
}

/// Weakness for a concept, blending card forgetting and question error rate on
/// a single 0..1 scale, weighted by how much evidence each side carries.
///
/// `recall_sum` is the sum of FSRS retrievabilities over the concept's cards
/// that have a memory state (so `recall_count - recall_sum` is the expected
/// number of *forgotten* cards). Question error mass is simply the count of
/// wrong answers. Dividing their sum by the total evidence (cards + attempts)
/// keeps the result in [0, 1] and makes the two signals commensurate:
///
/// ```text
/// weakness = (cards_forgotten + questions_wrong) / (cards_with_state + attempts)
/// ```
///
/// With no question evidence this is exactly `1 - avg_recall` (the original
/// formula); with no evidence at all it is the maximum, 1.0.
fn weakness(recall_sum: f64, recall_count: u32, q: QuestionStat) -> f64 {
    let card_evidence = recall_count as f64;
    let total_evidence = card_evidence + q.attempts as f64;
    if total_evidence == 0.0 {
        return 1.0;
    }
    let cards_forgotten = (card_evidence - recall_sum).max(0.0);
    ((cards_forgotten + q.wrong()) / total_evidence).clamp(0.0, 1.0)
}

/// Compute per-concept mastery + NTR over the given cards, blending in any
/// per-concept question performance. Set-based: one pass over the cards, no
/// per-card queries. Concepts are returned in taxonomy order so output is
/// deterministic. Concepts with no cards in scope are still reported
/// (cards_total 0); their NTR reflects question evidence if present, otherwise
/// `topic_weight`.
pub fn compute_mastery(
    taxonomy: &Taxonomy,
    cards: &[CardConcepts],
    questions: &QuestionStats,
) -> Vec<ConceptMastery> {
    use std::collections::HashMap;
    let mut acc: HashMap<&str, ConceptAccumulator> = taxonomy
        .rules
        .iter()
        .map(|r| (r.id.as_str(), ConceptAccumulator::default()))
        .collect();

    for card in cards {
        for concept in &card.concepts {
            if let Some(entry) = acc.get_mut(concept.as_str()) {
                entry.cards_total += 1;
                if let Some(r) = card.retrievability {
                    entry.recall_sum += r as f64;
                    entry.recall_count += 1;
                    if r >= MASTERY_RETRIEVABILITY_THRESHOLD {
                        entry.cards_mastered += 1;
                    }
                }
            }
        }
    }

    taxonomy
        .rules
        .iter()
        .map(|rule| {
            let a = acc.get(rule.id.as_str()).cloned().unwrap_or_default();
            let q = questions.get(&rule.id).copied().unwrap_or_default();
            let avg_recall = if a.recall_count == 0 {
                0.0
            } else {
                a.recall_sum / a.recall_count as f64
            };
            let ntr = rule.weight() * weakness(a.recall_sum, a.recall_count, q);
            ConceptMastery {
                concept_id: rule.id.clone(),
                cards_total: a.cards_total,
                cards_mastered: a.cards_mastered,
                avg_recall,
                ntr,
                topic_weight: rule.weight(),
                questions_total: q.attempts,
                questions_correct: q.correct.min(q.attempts),
                question_accuracy: q.accuracy(),
            }
        })
        .collect()
}

/// A single entry in the concept-aware queue.
#[derive(Debug, Clone, PartialEq)]
pub struct QueueEntry {
    pub card_id: CardId,
    pub priority: f64,
    pub concepts: Vec<String>,
}

/// Order cards by concept-aware NTR priority, highest first. A card's priority
/// is the max NTR over its concepts; cards with no concept get priority 0 and
/// sort last. Ties break by card id for determinism.
///
/// This re-orders an already-selected set of due cards. It does not change FSRS
/// intervals or which cards are due.
pub fn order_by_ntr(
    taxonomy: &Taxonomy,
    cards: &[CardConcepts],
    questions: &QuestionStats,
) -> Vec<QueueEntry> {
    // NTR per concept comes from the same mastery computation, so input and
    // output of FSRS stay consistent.
    let mastery = compute_mastery(taxonomy, cards, questions);
    let ntr_by_concept: std::collections::HashMap<&str, f64> = mastery
        .iter()
        .map(|m| (m.concept_id.as_str(), m.ntr))
        .collect();

    let mut entries: Vec<QueueEntry> = cards
        .iter()
        .map(|card| {
            let priority = card
                .concepts
                .iter()
                .filter_map(|c| ntr_by_concept.get(c.as_str()).copied())
                .fold(0.0_f64, f64::max);
            QueueEntry {
                card_id: card.card_id,
                priority,
                concepts: card.concepts.clone(),
            }
        })
        .collect();

    entries.sort_by(|a, b| {
        b.priority
            .partial_cmp(&a.priority)
            .unwrap_or(std::cmp::Ordering::Equal)
            .then(a.card_id.0.cmp(&b.card_id.0))
    });
    entries
}

// ----- Collection glue: load card/tag/retrievability data set-based ----------

/// A row joining a card to its note's tags, plus the FSRS fields needed to
/// compute retrievability.
struct CardTagRow {
    card_id: CardId,
    tags: String,
    memory_state: Option<FsrsMemoryState>,
    decay: Option<f32>,
    last_review_time: Option<TimestampSecs>,
    due: i32,
    interval: u32,
}

impl Collection {
    /// Build the per-card concept data for the cards matched by `search`.
    /// One set-based SQL join (cards -> notes); retrievability is computed in a
    /// single pass. Suitable for 50k cards.
    pub(crate) fn card_concepts_for_search(
        &mut self,
        taxonomy: &Taxonomy,
        search: &str,
    ) -> Result<Vec<CardConcepts>> {
        let timing = self.timing_today()?;
        let rows = self.load_card_tag_rows(search)?;
        let fsrs = FSRS::new(None)?;
        let sched_timing = SchedTimingToday {
            days_elapsed: timing.days_elapsed,
            now: TimestampSecs::now(),
            next_day_at: timing.next_day_at,
        };

        let out = rows
            .into_iter()
            .map(|row| {
                let tags: Vec<String> = split_tags(&row.tags).map(ToString::to_string).collect();
                let concepts = concepts_for_tags(taxonomy, &tags);
                let retrievability = row.memory_state.map(|state| {
                    // Mirror stats/graphs retrievability computation.
                    let elapsed = card_seconds_since_last_review(&row, &sched_timing);
                    fsrs.current_retrievability_seconds(
                        state.into(),
                        elapsed,
                        row.decay.unwrap_or(FSRS5_DEFAULT_DECAY),
                    )
                });
                CardConcepts {
                    card_id: row.card_id,
                    concepts,
                    retrievability,
                }
            })
            .collect();
        Ok(out)
    }

    /// Set-based join of the searched cards to their note tags + FSRS data.
    fn load_card_tag_rows(&mut self, search: &str) -> Result<Vec<CardTagRow>> {
        use crate::search::SortMode;
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        let mut stmt = guard.col.storage.db.prepare_cached(
            "SELECT c.id, n.tags, c.data, c.due, c.ivl
             FROM cards c
             JOIN notes n ON n.id = c.nid
             WHERE c.id IN (SELECT cid FROM search_cids)",
        )?;
        let rows = stmt
            .query_and_then([], |row| -> Result<CardTagRow> {
                let card_id: CardId = row.get(0)?;
                let tags: String = row.get(1)?;
                let data_str: String = row.get(2).unwrap_or_default();
                let data = crate::storage::card::data::CardData::from_str(&data_str);
                Ok(CardTagRow {
                    card_id,
                    tags,
                    memory_state: data.memory_state(),
                    decay: data.decay,
                    last_review_time: data.last_review_time,
                    due: row.get(3).ok().unwrap_or_default(),
                    interval: row.get(4)?,
                })
            })?
            .collect::<Result<Vec<_>>>()?;
        Ok(rows)
    }
}

/// Seconds since the card's last review, mirroring
/// `Card::seconds_since_last_review` but using only the lightweight fields we
/// loaded.
fn card_seconds_since_last_review(row: &CardTagRow, timing: &SchedTimingToday) -> u32 {
    if let Some(last) = row.last_review_time {
        timing.now.elapsed_secs_since(last) as u32
    } else {
        // Fall back to the due-date heuristic used by the stats graphs for
        // review cards lacking an explicit last-review stamp.
        let due_secs = timing
            .next_day_at
            .adding_secs(-((timing.days_elapsed as i64 - row.due as i64) * 86_400));
        let started = due_secs.adding_secs(-86_400 * row.interval as i64);
        timing.now.elapsed_secs_since(started).max(0) as u32
    }
}

#[cfg(test)]
mod test {
    use super::*;

    fn rule(id: &str, weight: f64, patterns: &[&str]) -> ConceptRule {
        ConceptRule {
            id: id.to_string(),
            topic_weight: weight,
            tag_patterns: patterns.iter().map(|s| s.to_string()).collect(),
        }
    }

    fn taxonomy() -> Taxonomy {
        Taxonomy {
            rules: vec![
                rule("1A", 2.0, &["mcat::bio::biochem*"]),
                rule("1B", 1.0, &["mcat::bio::cell"]),
                rule("4C", 3.0, &["mcat::physics::*"]),
            ],
        }
    }

    fn card(id: i64, concepts: &[&str], r: Option<f32>) -> CardConcepts {
        CardConcepts {
            card_id: CardId(id),
            concepts: concepts.iter().map(|s| s.to_string()).collect(),
            retrievability: r,
        }
    }

    /// No question evidence -> NTR reduces to the card-only formula.
    fn no_q() -> QuestionStats {
        QuestionStats::new()
    }

    fn q_stats(pairs: &[(&str, u32, u32)]) -> QuestionStats {
        pairs
            .iter()
            .map(|(id, attempts, correct)| {
                (
                    id.to_string(),
                    QuestionStat {
                        attempts: *attempts,
                        correct: *correct,
                    },
                )
            })
            .collect()
    }

    #[test]
    fn tag_to_concept_matching() {
        let tax = taxonomy();
        // Prefix/wildcard match.
        assert_eq!(
            concepts_for_tags(&tax, &["mcat::bio::biochem::enzymes".into()]),
            vec!["1A".to_string()]
        );
        // Hierarchical prefix match (exact stem matches child tags).
        assert_eq!(
            concepts_for_tags(&tax, &["mcat::bio::cell::membrane".into()]),
            vec!["1B".to_string()]
        );
        // A card with MULTIPLE concepts.
        let many = concepts_for_tags(
            &tax,
            &[
                "MCAT::Bio::Biochem::Metabolism".into(), // case-insensitive -> 1A
                "mcat::physics::thermo".into(),          // -> 4C
            ],
        );
        assert_eq!(many, vec!["1A".to_string(), "4C".to_string()]);
        // A card with NO concept.
        assert!(concepts_for_tags(&tax, &["marked".into(), "leech".into()]).is_empty());
        // Card with no tags at all.
        assert!(concepts_for_tags(&tax, &[]).is_empty());
    }

    #[test]
    fn mastery_counts_and_avg_recall() {
        let tax = taxonomy();
        let cards = vec![
            card(1, &["1A"], Some(0.95)), // mastered
            card(2, &["1A"], Some(0.50)), // not mastered
            card(3, &["1B"], None),       // no memory state -> excluded from avg
            card(4, &["4C"], Some(0.90)), // exactly at threshold -> mastered
        ];
        let m = compute_mastery(&tax, &cards, &no_q());
        let by_id = |id: &str| m.iter().find(|x| x.concept_id == id).unwrap();

        let a = by_id("1A");
        assert_eq!(a.cards_total, 2);
        assert_eq!(a.cards_mastered, 1);
        assert!((a.avg_recall - 0.725).abs() < 1e-6);
        // weakness = 1 - 0.725 = 0.275; ntr = topic_weight(2.0) * 0.275
        assert!((a.ntr - 2.0 * 0.275).abs() < 1e-6);

        let b = by_id("1B");
        assert_eq!(b.cards_total, 1);
        assert_eq!(b.cards_mastered, 0);
        assert_eq!(b.avg_recall, 0.0);
        // No card with memory state -> weakness 1.0 -> ntr = topic_weight.
        assert!((b.ntr - 1.0).abs() < 1e-6);

        let c = by_id("4C");
        assert_eq!(c.cards_mastered, 1); // 0.90 >= threshold
        assert!((c.ntr - 3.0 * (1.0 - 0.90)).abs() < 1e-6);
    }

    #[test]
    fn queue_ordering_by_ntr() {
        let tax = taxonomy();
        let cards = vec![
            // 1A: avg 0.725 -> ntr 0.55
            card(1, &["1A"], Some(0.95)),
            card(2, &["1A"], Some(0.50)),
            // 4C: avg 0.20 -> weakness 0.8 -> ntr 3.0*0.8 = 2.4 (highest)
            card(3, &["4C"], Some(0.20)),
            // 1B: no memory state -> ntr 1.0
            card(4, &["1B"], None),
            // no concept -> priority 0, sorts last
            card(5, &[], Some(0.10)),
        ];
        let ordered = order_by_ntr(&tax, &cards, &no_q());
        let ids: Vec<i64> = ordered.iter().map(|e| e.card_id.0).collect();
        // Cards 1 & 2 share concept 1A (priority 0.55). 4C (2.4) first,
        // then 1B (1.0), then the two 1A cards (0.55) by id, then no-concept.
        assert_eq!(ids, vec![3, 4, 1, 2, 5]);
        // No-concept card has zero priority.
        assert_eq!(ordered.last().unwrap().priority, 0.0);
        // Highest priority is concept 4C's NTR.
        assert!((ordered[0].priority - 2.4).abs() < 1e-6);
    }

    #[test]
    fn card_with_multiple_concepts_takes_max_ntr() {
        let tax = taxonomy();
        // This card maps to both 1A (low ntr) and 4C (high ntr); priority = max.
        let cards = vec![
            card(1, &["1A"], Some(0.95)),
            card(2, &["1A"], Some(0.50)),
            card(3, &["4C"], Some(0.20)),
            card(10, &["1A", "4C"], Some(0.80)),
        ];
        let ordered = order_by_ntr(&tax, &cards, &no_q());
        let entry = ordered.iter().find(|e| e.card_id.0 == 10).unwrap();
        // Card 10 is itself tagged to both concepts, so it contributes to each
        // concept's average: 1A over {0.95,0.50,0.80}=0.75 -> ntr 0.5; 4C over
        // {0.20,0.80}=0.50 -> ntr 1.5. The card takes the max, 1.5.
        assert!((entry.priority - 1.5).abs() < 1e-6);
    }

    #[test]
    fn wildcard_and_prefix_patterns() {
        assert!(tag_matches_pattern(
            "mcat::bio::biochem::x",
            "mcat::bio::biochem*"
        ));
        assert!(tag_matches_pattern(
            "mcat::physics::thermo",
            "mcat::physics::*"
        ));
        assert!(tag_matches_pattern("mcat::physics", "mcat::physics::*"));
        assert!(tag_matches_pattern("a::b::c", "a::*::c"));
        assert!(!tag_matches_pattern("a::x::d", "a::*::c"));
        // hierarchical prefix without wildcard
        assert!(tag_matches_pattern(
            "mcat::bio::cell::wall",
            "mcat::bio::cell"
        ));
        assert!(!tag_matches_pattern("mcat::biology", "mcat::bio"));
    }

    #[test]
    fn questions_raise_and_lower_ntr() {
        let tax = taxonomy();
        // One 1A card at 0.8 recall: forgotten mass 0.2, weakness 0.2, and with
        // topic_weight 2.0 -> card-only NTR 0.4.
        let cards = vec![card(1, &["1A"], Some(0.8))];

        // f32 retrievability widened to f64 isn't exact, so use the same 1e-6
        // tolerance as the other tests here.
        let baseline = compute_mastery(&tax, &cards, &no_q());
        let a0 = baseline.iter().find(|m| m.concept_id == "1A").unwrap();
        assert!((a0.ntr - 0.4).abs() < 1e-6);
        assert_eq!(a0.questions_total, 0);
        assert_eq!(a0.question_accuracy, 0.0);

        // Bad questions (1/4) push NTR up: weakness = (0.2 + 3)/(1 + 4) = 0.64.
        let worse = compute_mastery(&tax, &cards, &q_stats(&[("1A", 4, 1)]));
        let a1 = worse.iter().find(|m| m.concept_id == "1A").unwrap();
        assert!((a1.ntr - 2.0 * 0.64).abs() < 1e-6);
        assert_eq!(a1.questions_total, 4);
        assert_eq!(a1.questions_correct, 1);
        assert!((a1.question_accuracy - 0.25).abs() < 1e-9);
        // Card recall is unchanged by questions (Memory stays card-only).
        assert!((a1.avg_recall - 0.8).abs() < 1e-6);

        // Strong questions (9/9) pull NTR down: weakness = (0.2 + 0)/(1 + 9).
        let better = compute_mastery(&tax, &cards, &q_stats(&[("1A", 9, 9)]));
        let a2 = better.iter().find(|m| m.concept_id == "1A").unwrap();
        assert!((a2.ntr - 2.0 * (0.2 / 10.0)).abs() < 1e-6);
        assert!(a2.ntr < a0.ntr);
    }

    #[test]
    fn questions_drive_ntr_with_no_card_evidence() {
        let tax = taxonomy();
        // 1B has a card but no memory state; only question evidence remains.
        let cards = vec![card(3, &["1B"], None)];
        let m = compute_mastery(&tax, &cards, &q_stats(&[("1B", 2, 1)]));
        let b = m.iter().find(|x| x.concept_id == "1B").unwrap();
        // weakness = wrong(1) / attempts(2) = 0.5; topic_weight 1.0 -> ntr 0.5
        // (vs the no-evidence default of 1.0).
        assert!((b.ntr - 0.5).abs() < 1e-9);
        assert!((b.question_accuracy - 0.5).abs() < 1e-9);
    }

    #[test]
    fn questions_reorder_the_queue() {
        let tax = taxonomy();
        // Both concepts look identical from cards alone (0.8 recall each), but
        // 1A's questions are far worse, so 1A's card must sort ahead of 1B's.
        let cards = vec![card(1, &["1A"], Some(0.8)), card(2, &["1B"], Some(0.8))];
        let questions = q_stats(&[("1A", 5, 0), ("1B", 5, 5)]);
        let ordered = order_by_ntr(&tax, &cards, &questions);
        let ids: Vec<i64> = ordered.iter().map(|e| e.card_id.0).collect();
        assert_eq!(ids, vec![1, 2]);
        assert!(ordered[0].priority > ordered[1].priority);
    }
}
