// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Per-topic mastery, aggregated in the backend so that every client shows the
//! same number.
//!
//! A "topic" is a tag, optionally restricted to those under a prefix. Cards are
//! counted once per topic they carry, so a note tagged with two content areas
//! contributes to both — deliberately, since exam content outlines overlap.
//!
//! The give-up rule lives here rather than in the UI: a topic with too few
//! graded reviews reports `average_recall: None` instead of a number derived
//! from three answers.

use std::collections::HashMap;

use anki_proto::stats::topic_mastery_response::Topic;
use anki_proto::stats::TopicMasteryResponse;
use fsrs::FSRS;
use fsrs::FSRS5_DEFAULT_DECAY;

use crate::prelude::*;
use crate::search::SortMode;

#[derive(Default)]
struct TopicAccumulator {
    card_count: u32,
    reviewed_card_count: u32,
    review_count: u32,
    passed_review_count: u32,
    retrievability_sum: f32,
    stability_sum: f32,
    memory_state_count: u32,
}

impl TopicAccumulator {
    fn into_proto(self, name: String, min_reviews_for_estimate: u32) -> Topic {
        let mean = |sum: f32| {
            (self.memory_state_count > 0).then(|| sum / self.memory_state_count as f32)
        };
        // The engine refuses to produce a recall estimate it cannot support.
        let average_recall = (self.review_count >= min_reviews_for_estimate.max(1))
            .then(|| self.passed_review_count as f32 / self.review_count as f32);
        Topic {
            name,
            card_count: self.card_count,
            reviewed_card_count: self.reviewed_card_count,
            review_count: self.review_count,
            passed_review_count: self.passed_review_count,
            mean_retrievability: mean(self.retrievability_sum),
            mean_stability_days: mean(self.stability_sum),
            average_recall,
        }
    }
}

/// Splits Anki's space-delimited tag column, keeping only tags under `prefix`.
fn topics_in<'a>(tags: &'a str, prefix: &str) -> impl Iterator<Item = &'a str> {
    let prefix = prefix.to_string();
    tags.split_whitespace()
        .filter(move |tag| prefix.is_empty() || tag.starts_with(&prefix))
}

impl Collection {
    pub(crate) fn topic_mastery(
        &mut self,
        input: anki_proto::stats::TopicMasteryRequest,
    ) -> Result<TopicMasteryResponse> {
        let guard = self.search_cards_into_table(&input.search, SortMode::NoOrder)?;
        guard
            .col
            .topic_mastery_for_searched_cards(&input.topic_prefix, input.min_reviews_for_estimate)
    }

    fn topic_mastery_for_searched_cards(
        &mut self,
        topic_prefix: &str,
        min_reviews_for_estimate: u32,
    ) -> Result<TopicMasteryResponse> {
        let timing = self.timing_today()?;
        let cards = self.storage.all_searched_cards()?;
        let tags_by_note: HashMap<NoteId, String> =
            self.storage.tags_for_searched_cards()?.into_iter().collect();
        let revlog = self
            .storage
            .get_revlog_entries_for_searched_cards_after_stamp(TimestampSecs(0))?;

        // card -> (graded reviews, passed reviews)
        let mut reviews_by_card: HashMap<CardId, (u32, u32)> = HashMap::new();
        for entry in revlog.iter().filter(|e| e.has_rating_and_affects_scheduling()) {
            let counts = reviews_by_card.entry(entry.cid).or_default();
            counts.0 += 1;
            // Anki treats button 1 ("Again") as the only failing answer.
            if entry.button_chosen > 1 {
                counts.1 += 1;
            }
        }

        let fsrs = FSRS::new(None)?;
        let mut topics: HashMap<String, TopicAccumulator> = HashMap::new();
        let mut untagged_card_count = 0;
        let total_card_count = cards.len() as u32;

        for card in &cards {
            let tags = tags_by_note
                .get(&card.note_id)
                .map(String::as_str)
                .unwrap_or_default();
            let mut matched_any = false;

            let retrievability_and_stability = card.memory_state.map(|state| {
                let elapsed = card.seconds_since_last_review(&timing).unwrap_or_default();
                let decay = card.decay.unwrap_or(FSRS5_DEFAULT_DECAY);
                let r = fsrs.current_retrievability_seconds(state.into(), elapsed, decay);
                (r, state.stability)
            });
            let reviews = reviews_by_card.get(&card.id).copied();

            for topic in topics_in(tags, topic_prefix) {
                matched_any = true;
                let acc = topics.entry(topic.to_string()).or_default();
                acc.card_count += 1;
                if let Some((graded, passed)) = reviews {
                    acc.reviewed_card_count += 1;
                    acc.review_count += graded;
                    acc.passed_review_count += passed;
                }
                if let Some((r, stability)) = retrievability_and_stability {
                    acc.retrievability_sum += r;
                    acc.stability_sum += stability;
                    acc.memory_state_count += 1;
                }
            }

            if !matched_any {
                untagged_card_count += 1;
            }
        }

        let mut topics: Vec<Topic> = topics
            .into_iter()
            .map(|(name, acc)| acc.into_proto(name, min_reviews_for_estimate))
            .collect();
        // Stable ordering so clients and tests don't depend on hash iteration.
        topics.sort_by(|a, b| a.name.cmp(&b.name));

        Ok(TopicMasteryResponse {
            topics,
            untagged_card_count,
            total_card_count,
        })
    }
}

#[cfg(test)]
mod test {
    use anki_proto::stats::TopicMasteryRequest;

    use super::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;

    fn add_note_with_tags(col: &mut Collection, front: &str, tags: &[&str]) -> Note {
        let nt = col.basic_notetype();
        let mut note = nt.new_note();
        note.fields_mut()[0] = front.into();
        note.tags = tags.iter().map(ToString::to_string).collect();
        col.add_note(&mut note, DeckId(1)).unwrap();
        note
    }

    fn log_review(col: &mut Collection, cid: CardId, button_chosen: u8, offset_millis: i64) {
        let entry = RevlogEntry {
            id: RevlogId(TimestampMillis::now().0 + offset_millis),
            cid,
            usn: Usn(-1),
            button_chosen,
            interval: 1,
            last_interval: 1,
            ease_factor: 2500,
            taken_millis: 1000,
            review_kind: RevlogReviewKind::Review,
        };
        col.storage.add_revlog_entry(&entry, true).unwrap();
    }

    fn mastery(col: &mut Collection, prefix: &str, min_reviews: u32) -> TopicMasteryResponse {
        col.topic_mastery(TopicMasteryRequest {
            search: String::new(),
            topic_prefix: prefix.into(),
            min_reviews_for_estimate: min_reviews,
        })
        .unwrap()
    }

    /// A card tagged with two content areas counts toward both, because exam
    /// outlines overlap and hiding that would understate coverage.
    #[test]
    fn card_contributes_to_every_tagged_topic() {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "one", &["MCAT::Bio", "MCAT::Chem"]);
        add_note_with_tags(&mut col, "two", &["MCAT::Bio"]);

        let resp = mastery(&mut col, "MCAT::", 1);

        assert_eq!(resp.total_card_count, 2);
        assert_eq!(resp.untagged_card_count, 0);
        let names: Vec<&str> = resp.topics.iter().map(|t| t.name.as_str()).collect();
        assert_eq!(names, vec!["MCAT::Bio", "MCAT::Chem"]);
        assert_eq!(resp.topics[0].card_count, 2, "Bio holds both cards");
        assert_eq!(resp.topics[1].card_count, 1, "Chem holds only the first");
    }

    /// Cards outside the prefix must be reported as untagged rather than
    /// silently dropped, so coverage can never be overstated.
    #[test]
    fn cards_outside_the_prefix_are_reported_as_untagged() {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "in", &["MCAT::Bio"]);
        add_note_with_tags(&mut col, "out", &["leech"]);
        add_note_with_tags(&mut col, "none", &[]);

        let resp = mastery(&mut col, "MCAT::", 1);

        assert_eq!(resp.total_card_count, 3);
        assert_eq!(resp.topics.len(), 1);
        assert_eq!(resp.topics[0].name, "MCAT::Bio");
        assert_eq!(
            resp.untagged_card_count, 2,
            "the 'leech' card and the untagged card both count as untagged"
        );
    }

    /// The give-up rule: below the review threshold the engine reports no
    /// recall estimate at all, rather than a number backed by two answers.
    #[test]
    fn recall_is_withheld_until_enough_reviews_exist() {
        let mut col = Collection::new();
        let note = add_note_with_tags(&mut col, "one", &["MCAT::Bio"]);
        let cid = col.storage.existing_cards_for_note(note.id).unwrap()[0].id;

        log_review(&mut col, cid, 3, 0);
        log_review(&mut col, cid, 1, 1);
        log_review(&mut col, cid, 3, 2);

        // Threshold above the number of reviews we logged: no estimate.
        let withheld = mastery(&mut col, "MCAT::", 10);
        assert_eq!(withheld.topics[0].review_count, 3);
        assert_eq!(
            withheld.topics[0].average_recall, None,
            "3 reviews must not support an estimate when 10 are required"
        );

        // Threshold met: 2 of 3 reviews passed.
        let reported = mastery(&mut col, "MCAT::", 3);
        assert_eq!(reported.topics[0].reviewed_card_count, 1);
        assert_eq!(reported.topics[0].passed_review_count, 2);
        assert_eq!(reported.topics[0].average_recall, Some(2.0 / 3.0));
    }

    /// The query must be read-only.
    ///
    /// The undo queue is a sensitive witness: if the query opened a write
    /// transaction or recorded any change, the pending undo step or its
    /// counter would move. Adding a note leaves a real undoable operation to
    /// watch, and the database check confirms nothing was corrupted.
    #[test]
    fn query_is_read_only_and_leaves_undo_intact() {
        use crate::dbcheck::CheckDatabaseOutput;

        let mut col = Collection::new();
        add_note_with_tags(&mut col, "one", &["MCAT::Bio"]);

        let before = col.undo_status();
        assert!(before.undo.is_some(), "adding a note should be undoable");

        // Run it repeatedly; a leak would compound.
        for _ in 0..3 {
            let _ = mastery(&mut col, "MCAT::", 1);
        }

        let after = col.undo_status();
        assert_eq!(
            before.last_step, after.last_step,
            "the query moved the undo counter, so it wrote something"
        );
        assert_eq!(
            before.undo, after.undo,
            "the query displaced the pending undo step"
        );

        // Undo still works afterwards, and the query reflects the undone state.
        col.undo().unwrap();
        assert_eq!(mastery(&mut col, "MCAT::", 1).total_card_count, 0);

        assert_eq!(
            col.check_database().unwrap(),
            CheckDatabaseOutput::default(),
            "database check reported problems after running the query"
        );
    }
}
