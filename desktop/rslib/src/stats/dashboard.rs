// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! CFA Speedrun: per-reading study metrics powering the dashboard's Memory /
//! Performance / Readiness gauges.
//!
//! Read-only; one SQL pass (mirrors the concept-graph / topic-mastery data
//! path). The engine returns generic per-tag numbers only — the CFA topic
//! mapping, exam weights, transfer factors, and gauge math all live in the
//! frontend, so the engine stays topic-agnostic.

use std::collections::HashMap;

use anki_proto::stats::dashboard_response::Tag;
use anki_proto::stats::DashboardRequest;
use anki_proto::stats::DashboardResponse;

use crate::prelude::*;
use crate::search::SearchBuilder;
use crate::search::SearchNode;
use crate::search::SortMode;

/// Bookkeeping tags that are noise rather than content readings.
const IGNORED_TAGS: &[&str] = &["Category", "marked", "leech"];

#[derive(Default)]
struct TagAcc {
    total: u32,
    studied: u32,
    retrievability_sum: f64,
    reviewed: u32,
    seen: u32,
    graded: u32,
    again: u32,
    hard: u32,
}

impl Collection {
    pub(crate) fn dashboard(&mut self, req: DashboardRequest) -> Result<DashboardResponse> {
        // A deck id (with its children) takes precedence over the free-text
        // search; an empty search considers the whole collection (the exam).
        let guard = if req.deck_id != 0 {
            let search = SearchBuilder::from(SearchNode::from_deck_id(DeckId(req.deck_id), true));
            self.search_cards_into_table(search, SortMode::NoOrder)?
        } else {
            self.search_cards_into_table(&req.search, SortMode::NoOrder)?
        };
        let timing = guard.col.timing_today()?;
        let rows = guard.col.storage.searched_cards_graph_data(timing)?;
        let considered = rows.len() as u32;
        let graded_reviews = guard.col.storage.searched_cards_graded_review_count()?;

        let mut by_tag: HashMap<String, TagAcc> = HashMap::new();
        for (tags, retrievability, ctype) in rows {
            // Review (2) or Relearn (3) means the card has graduated from
            // initial learning; any non-New (type != 0) card has been seen.
            let graduated = ctype == 2 || ctype == 3;
            let seen = ctype != 0;
            for tag in tags
                .split_whitespace()
                .filter(|t| !IGNORED_TAGS.contains(t))
            {
                let acc = by_tag.entry(tag.to_string()).or_default();
                acc.total += 1;
                if let Some(r) = retrievability {
                    acc.studied += 1;
                    acc.retrievability_sum += r as f64;
                }
                if graduated {
                    acc.reviewed += 1;
                }
                if seen {
                    acc.seen += 1;
                }
            }
        }

        // Fold in per-tag answer accuracy from the review log so the frontend can
        // discount Memory on tags the student keeps getting wrong or finding
        // hard (a card counts toward each of its tags, as above).
        for (tags, ease, count) in guard.col.storage.searched_cards_revlog_grades_by_tag()? {
            for tag in tags
                .split_whitespace()
                .filter(|t| !IGNORED_TAGS.contains(t))
            {
                let acc = by_tag.entry(tag.to_string()).or_default();
                acc.graded += count;
                match ease {
                    1 => acc.again += count,
                    2 => acc.hard += count,
                    _ => {}
                }
            }
        }

        let mut tags: Vec<Tag> = by_tag
            .into_iter()
            .map(|(tag, acc)| Tag {
                tag,
                total: acc.total,
                studied: acc.studied,
                mean_retrievability: if acc.studied > 0 {
                    (acc.retrievability_sum / acc.studied as f64) as f32
                } else {
                    0.0
                },
                reviewed: acc.reviewed,
                seen: acc.seen,
                graded_reviews: acc.graded,
                again_reviews: acc.again,
                hard_reviews: acc.hard,
            })
            .collect();
        // deterministic ordering for stable display/tests
        tags.sort_by(|a, b| a.tag.cmp(&b.tag));

        Ok(DashboardResponse {
            tags,
            graded_reviews,
            considered,
        })
    }
}

#[cfg(test)]
mod test {
    use anki_proto::stats::DashboardRequest;

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
    fn aggregates_per_tag() -> Result<()> {
        let mut col = Collection::new();
        add_note(&mut col, "1", &["Inventories"]);
        add_note(&mut col, "2", &["Inventories"]);
        add_note(&mut col, "3", &["Cost_of_Capital", "Category"]);

        let res = col.dashboard(DashboardRequest::default())?;

        assert_eq!(res.considered, 3);
        // the noise tag "Category" is dropped -> 2 content tags
        assert_eq!(res.tags.len(), 2);
        assert_eq!(res.tags[0].tag, "Cost_of_Capital");
        assert_eq!(res.tags[0].total, 1);
        assert_eq!(res.tags[1].tag, "Inventories");
        assert_eq!(res.tags[1].total, 2);
        // fresh cards: nothing studied/seen under FSRS, no graded reviews
        assert_eq!(res.tags[1].studied, 0);
        assert_eq!(res.tags[1].seen, 0);
        assert_eq!(res.graded_reviews, 0);
        // and no answers yet -> no accuracy signal
        assert_eq!(res.tags[1].graded_reviews, 0);
        assert_eq!(res.tags[1].again_reviews, 0);
        assert_eq!(res.tags[1].hard_reviews, 0);
        Ok(())
    }

    #[test]
    fn records_per_tag_answer_accuracy() -> Result<()> {
        let mut col = Collection::new();
        add_note(&mut col, "1", &["Inventories"]);
        // Answering the card "Again" logs a failed (button 1) graded review that
        // the frontend uses to discount this tag's Memory.
        col.answer_again();

        let res = col.dashboard(DashboardRequest::default())?;
        let tag = res.tags.iter().find(|t| t.tag == "Inventories").unwrap();
        assert_eq!(tag.graded_reviews, 1);
        assert_eq!(tag.again_reviews, 1);
        assert_eq!(tag.hard_reviews, 0);
        // top-level give-up counter agrees
        assert_eq!(res.graded_reviews, 1);
        Ok(())
    }
}
