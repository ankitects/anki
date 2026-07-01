// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Implements the generated `ConceptsService` trait on `Collection`. Because
//! `BackendConceptsService` is empty in the proto, these methods are
//! auto-delegated to the `Backend` via `with_col`.

use anki_proto::concepts as pb;

use super::compute_mastery;
use super::order_by_ntr;
use super::ConceptRule;
use super::QuestionStat;
use super::QuestionStats;
use super::Taxonomy;
use crate::collection::Collection;
use crate::error;

impl From<pb::ConceptTaxonomy> for Taxonomy {
    fn from(value: pb::ConceptTaxonomy) -> Self {
        Taxonomy {
            rules: value
                .rules
                .into_iter()
                .map(|r| ConceptRule {
                    id: r.id,
                    topic_weight: r.topic_weight,
                    tag_patterns: r.tag_patterns,
                })
                .collect(),
        }
    }
}

/// Collapse the caller-supplied question stats into a concept-keyed map. Later
/// entries for the same concept overwrite earlier ones, so the caller should
/// pre-aggregate per concept (the Python side does).
fn question_stats(stats: Vec<pb::ConceptQuestionStat>) -> QuestionStats {
    stats
        .into_iter()
        .map(|s| {
            (
                s.concept_id,
                QuestionStat {
                    attempts: s.attempts,
                    correct: s.correct,
                },
            )
        })
        .collect()
}

impl crate::services::ConceptsService for Collection {
    fn concept_aware_queue(
        &mut self,
        input: pb::ConceptAwareQueueRequest,
    ) -> error::Result<pb::ConceptAwareQueueResponse> {
        let taxonomy: Taxonomy = input.taxonomy.unwrap_or_default().into();
        let questions = question_stats(input.question_stats);
        // Re-order the cards the scheduler would surface now (due or new) within
        // the scope. We never mutate the collection here, so FSRS intervals and
        // undo are untouched.
        let search = scoped_study_search(&input.search);
        let cards = self.card_concepts_for_search(&taxonomy, &search)?;
        let ordered = order_by_ntr(&taxonomy, &cards, &questions);
        Ok(pb::ConceptAwareQueueResponse {
            entries: ordered
                .into_iter()
                .map(|e| pb::ConceptAwareQueueEntry {
                    card_id: e.card_id.0,
                    priority: e.priority,
                    concepts: e.concepts,
                })
                .collect(),
        })
    }

    fn concept_mastery(
        &mut self,
        input: pb::ConceptMasteryRequest,
    ) -> error::Result<pb::ConceptMasteryResponse> {
        let taxonomy: Taxonomy = input.taxonomy.unwrap_or_default().into();
        let questions = question_stats(input.question_stats);
        let cards = self.card_concepts_for_search(&taxonomy, &input.search)?;
        let mastery = compute_mastery(&taxonomy, &cards, &questions);
        Ok(pb::ConceptMasteryResponse {
            entries: mastery
                .into_iter()
                .map(|m| pb::ConceptMasteryEntry {
                    concept_id: m.concept_id,
                    cards_total: m.cards_total,
                    cards_mastered: m.cards_mastered,
                    avg_recall: m.avg_recall,
                    ntr: m.ntr,
                    topic_weight: m.topic_weight,
                    questions_total: m.questions_total,
                    questions_correct: m.questions_correct,
                    question_accuracy: m.question_accuracy,
                })
                .collect(),
        })
    }
}

/// Restrict an arbitrary scope search to the cards the scheduler would surface
/// now — due review/learning cards *and* new cards — so the queue re-orders the
/// studyable set. New cards have no memory state, so NTR treats them as
/// maximally weak and surfaces them first, which is the intended behaviour. An
/// empty scope means the whole collection.
fn scoped_study_search(scope: &str) -> String {
    let scope = scope.trim();
    if scope.is_empty() {
        "(is:due OR is:new)".to_string()
    } else {
        format!("({scope}) (is:due OR is:new)")
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::services::ConceptsService;
    use crate::tests::NoteAdder;

    fn taxonomy_pb() -> pb::ConceptTaxonomy {
        pb::ConceptTaxonomy {
            rules: vec![
                pb::ConceptRule {
                    id: "1A".into(),
                    topic_weight: 2.0,
                    tag_patterns: vec!["mcat::biochem*".into()],
                },
                pb::ConceptRule {
                    id: "4C".into(),
                    topic_weight: 3.0,
                    tag_patterns: vec!["mcat::physics::*".into()],
                },
            ],
        }
    }

    /// End-to-end through the service trait on a real (in-memory) collection:
    /// tags -> concepts -> mastery, with cards that have no memory state.
    #[test]
    fn mastery_end_to_end() {
        let mut col = Collection::new();
        let mut n1 = NoteAdder::basic(&mut col).note();
        n1.tags = vec!["mcat::biochem::enzymes".into()];
        col.add_note(&mut n1, crate::decks::DeckId(1)).unwrap();
        let mut n2 = NoteAdder::basic(&mut col).note();
        n2.tags = vec!["mcat::physics::optics".into()];
        col.add_note(&mut n2, crate::decks::DeckId(1)).unwrap();
        // A note with no taxonomy-matching tag.
        let mut n3 = NoteAdder::basic(&mut col).note();
        n3.tags = vec!["misc".into()];
        col.add_note(&mut n3, crate::decks::DeckId(1)).unwrap();

        let resp = col
            .concept_mastery(pb::ConceptMasteryRequest {
                taxonomy: Some(taxonomy_pb()),
                search: String::new(),
                question_stats: vec![],
            })
            .unwrap();

        let by = |id: &str| resp.entries.iter().find(|e| e.concept_id == id).unwrap();
        // One card each maps to 1A and 4C; none have a memory state yet (new
        // cards), so they are excluded from avg_recall and weakness is maximal.
        assert_eq!(by("1A").cards_total, 1);
        assert_eq!(by("1A").cards_mastered, 0);
        assert_eq!(by("1A").avg_recall, 0.0);
        assert!((by("1A").ntr - 2.0).abs() < 1e-9); // weight * weakness(1.0)
        assert_eq!(by("4C").cards_total, 1);
        assert!((by("4C").ntr - 3.0).abs() < 1e-9);
    }

    #[test]
    fn queue_returns_due_cards_only_and_does_not_mutate() {
        let mut col = Collection::new();
        let mut n1 = NoteAdder::basic(&mut col).note();
        n1.tags = vec!["mcat::physics::optics".into()];
        col.add_note(&mut n1, crate::decks::DeckId(1)).unwrap();

        let usn_before = col.usn().unwrap();
        let undo_before = col.can_undo().cloned();
        let resp = col
            .concept_aware_queue(pb::ConceptAwareQueueRequest {
                taxonomy: Some(taxonomy_pb()),
                search: String::new(),
                question_stats: vec![],
            })
            .unwrap();
        // New cards count as due; the card should appear with concept 4C.
        assert_eq!(resp.entries.len(), 1);
        assert_eq!(resp.entries[0].concepts, vec!["4C".to_string()]);
        // Read-only: usn unchanged, and the query recorded no new undoable
        // operation (the top of the undo stack is whatever it was before).
        assert_eq!(col.usn().unwrap(), usn_before);
        assert_eq!(col.can_undo().cloned(), undo_before);
    }

    /// Practice-question performance flows through the RPC and changes NTR even
    /// though the card has no memory state. The displayed card recall stays 0.
    #[test]
    fn question_stats_flow_through_mastery_rpc() {
        let mut col = Collection::new();
        let mut n1 = NoteAdder::basic(&mut col).note();
        n1.tags = vec!["mcat::biochem::enzymes".into()];
        col.add_note(&mut n1, crate::decks::DeckId(1)).unwrap();

        let resp = col
            .concept_mastery(pb::ConceptMasteryRequest {
                taxonomy: Some(taxonomy_pb()),
                search: String::new(),
                question_stats: vec![pb::ConceptQuestionStat {
                    concept_id: "1A".into(),
                    attempts: 4,
                    correct: 1,
                }],
            })
            .unwrap();
        let a = resp.entries.iter().find(|e| e.concept_id == "1A").unwrap();
        // No memory state -> card recall stays 0, but question evidence sets
        // weakness = wrong(3)/attempts(4) = 0.75; topic_weight 2.0 -> ntr 1.5
        // (below the no-evidence default of 2.0).
        assert_eq!(a.avg_recall, 0.0);
        assert_eq!(a.questions_total, 4);
        assert_eq!(a.questions_correct, 1);
        assert!((a.question_accuracy - 0.25).abs() < 1e-9);
        assert!((a.ntr - 1.5).abs() < 1e-9);
        assert!((a.topic_weight - 2.0).abs() < 1e-9);
        // A concept with no questions keeps the maximal default NTR.
        let c = resp.entries.iter().find(|e| e.concept_id == "4C").unwrap();
        assert!((c.ntr - 3.0).abs() < 1e-9);
    }
}
