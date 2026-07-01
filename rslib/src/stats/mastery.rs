// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! CFA Speedrun: per-topic mastery / coverage over a tag taxonomy.
//!
//! Groups the searched cards by a topic tag prefix (default `cfa::topic::`)
//! and, in a single SQL pass, reports per topic the number of cards, how many
//! are "mastered" (FSRS retrievability at or above a threshold) and the mean
//! retrievability. This is the engine backing the Memory gauge and the coverage
//! map; the give-up / honest-range logic that decides whether to *display* a
//! number lives in the dashboard layer on top of this data.

use std::collections::HashMap;

use anki_proto::stats::topic_mastery_response::Topic;
use anki_proto::stats::TopicMasteryRequest;
use anki_proto::stats::TopicMasteryResponse;

use crate::prelude::*;
use crate::search::SortMode;

/// Tag prefix grouping cards into CFA topic areas when the request leaves
/// `topic_prefix` empty.
const DEFAULT_TOPIC_PREFIX: &str = "cfa::topic::";
/// FSRS retrievability at or above which a card counts as "mastered", when the
/// request leaves `mastered_threshold` unset.
const DEFAULT_MASTERED_THRESHOLD: f32 = 0.9;

#[derive(Default)]
struct TopicAcc {
    total: u32,
    mastered: u32,
    with_memory_state: u32,
    retrievability_sum: f64,
}

impl Collection {
    pub(crate) fn topic_mastery(
        &mut self,
        req: TopicMasteryRequest,
    ) -> Result<TopicMasteryResponse> {
        let prefix = non_empty_or(&req.topic_prefix, DEFAULT_TOPIC_PREFIX);
        let threshold = if req.mastered_threshold > 0.0 {
            req.mastered_threshold
        } else {
            DEFAULT_MASTERED_THRESHOLD
        };

        let guard = self.search_cards_into_table(&req.search, SortMode::NoOrder)?;
        let timing = guard.col.timing_today()?;
        let rows = guard
            .col
            .storage
            .searched_cards_retrievability_and_tags(timing)?;

        let considered = rows.len() as u32;
        let mut topics: HashMap<String, TopicAcc> = HashMap::new();
        let mut untagged: u32 = 0;
        for (tags, retrievability) in rows {
            let mut matched = false;
            for tag in tags.split_whitespace() {
                if tag.len() > prefix.len() && tag.starts_with(&prefix) {
                    matched = true;
                    let acc = topics.entry(tag.to_string()).or_default();
                    acc.total += 1;
                    if let Some(r) = retrievability {
                        acc.with_memory_state += 1;
                        acc.retrievability_sum += r as f64;
                        if r >= threshold {
                            acc.mastered += 1;
                        }
                    }
                }
            }
            if !matched {
                untagged += 1;
            }
        }

        let mut topics: Vec<Topic> = topics
            .into_iter()
            .map(|(tag, acc)| Topic {
                topic: tag.strip_prefix(&prefix).unwrap_or(&tag).to_string(),
                tag,
                total: acc.total,
                mastered: acc.mastered,
                with_memory_state: acc.with_memory_state,
                average_retrievability: if acc.with_memory_state > 0 {
                    (acc.retrievability_sum / acc.with_memory_state as f64) as f32
                } else {
                    0.0
                },
            })
            .collect();
        // deterministic ordering for stable display/tests
        topics.sort_by(|a, b| a.tag.cmp(&b.tag));

        Ok(TopicMasteryResponse {
            topics,
            untagged,
            considered,
        })
    }
}

fn non_empty_or(value: &str, default: &str) -> String {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        default.to_string()
    } else {
        trimmed.to_string()
    }
}

#[cfg(test)]
mod test {
    use anki_proto::stats::TopicMasteryRequest;

    use crate::collection::Collection;
    use crate::prelude::*;

    fn add_note(col: &mut Collection, field: &str, tags: &[&str]) {
        let nt = col.get_notetype_by_name("Basic").unwrap().unwrap();
        let mut note = nt.new_note();
        note.set_field(0, field).unwrap();
        note.tags = tags.iter().map(ToString::to_string).collect();
        col.add_note(&mut note, DeckId(1)).unwrap();
    }

    #[test]
    fn groups_cards_by_topic_prefix() -> Result<()> {
        let mut col = Collection::new();
        add_note(&mut col, "1", &["cfa::topic::ethics"]);
        add_note(&mut col, "2", &["cfa::topic::ethics"]);
        add_note(&mut col, "3", &["cfa::topic::fixed_income"]);
        add_note(&mut col, "4", &["misc"]);
        add_note(&mut col, "5", &[]);

        let res = col.topic_mastery(TopicMasteryRequest {
            topic_prefix: "cfa::topic::".into(),
            mastered_threshold: 0.0,
            search: String::new(),
        })?;

        assert_eq!(res.considered, 5);
        // "misc" and the untagged note are both untagged for this prefix
        assert_eq!(res.untagged, 2);
        assert_eq!(res.topics.len(), 2);
        // deterministic, prefix stripped
        assert_eq!(res.topics[0].tag, "cfa::topic::ethics");
        assert_eq!(res.topics[0].topic, "ethics");
        assert_eq!(res.topics[0].total, 2);
        assert_eq!(res.topics[1].topic, "fixed_income");
        assert_eq!(res.topics[1].total, 1);
        // fresh new cards have no FSRS memory state yet
        assert_eq!(res.topics[0].with_memory_state, 0);
        assert_eq!(res.topics[0].mastered, 0);
        Ok(())
    }

    #[test]
    fn empty_prefix_defaults_and_search_filters() -> Result<()> {
        let mut col = Collection::new();
        add_note(&mut col, "kept", &["cfa::topic::quant"]);
        add_note(&mut col, "filtered", &["cfa::topic::quant"]);

        // empty prefix falls back to the default cfa::topic:: prefix
        let res = col.topic_mastery(TopicMasteryRequest::default())?;
        assert_eq!(res.topics.len(), 1);
        assert_eq!(res.topics[0].topic, "quant");
        assert_eq!(res.topics[0].total, 2);

        // search restricts the considered cards
        let res = col.topic_mastery(TopicMasteryRequest {
            search: "front:kept".into(),
            ..Default::default()
        })?;
        assert_eq!(res.considered, 1);
        assert_eq!(res.topics[0].total, 1);
        Ok(())
    }
}
