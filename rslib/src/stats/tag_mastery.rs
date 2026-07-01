// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Read-only per-tag mastery aggregation + an overall memory-readiness band.
//!
//! Mirrors the existing retrievability graph (`super::graphs::retrievability`):
//! a single pass over the searched cards, computing each card's *current*
//! FSRS retrievability via the engine's own helper and grouping by the first
//! `group_depth` components of its note's `::` tag hierarchy. Writes nothing.

use std::collections::HashMap;
use std::collections::HashSet;

use fsrs::FSRS;
use fsrs::FSRS5_DEFAULT_DECAY;

use crate::prelude::*;
use crate::search::SortMode;
use crate::tags::split_tags;

/// Default "mastered" cutoff on current retrievability when the request
/// leaves `mastered_threshold` at 0.
const DEFAULT_MASTERED: f64 = 0.9;
/// z value for a two-sided 90% confidence interval.
const Z_90: f64 = 1.645;
/// Give-up rule (PRD1 §5.4): abstain from showing a readiness number until the
/// collection has at least this many graded reviews spanning at least this many
/// distinct topics. Below either bar the UI shows "Not enough data yet".
const MIN_GRADED_REVIEWS: u32 = 150;
const MIN_TOPICS_WITH_REVIEWS: u32 = 5;
/// Sentinel group for cards whose note has no tags. Cannot collide with a real
/// tag (tags can't be empty or contain spaces).
const UNTAGGED: &str = "(untagged)";

#[derive(Default)]
struct GroupAcc {
    total_cards: u32,
    cards_with_state: u32,
    mastered_cards: u32,
    sum_recall: f64,
    graded_reviews: u32,
}

impl Collection {
    pub(crate) fn tag_mastery(
        &mut self,
        input: anki_proto::stats::TagMasteryRequest,
    ) -> Result<anki_proto::stats::TagMasteryResponse> {
        // Scope to the search (empty = whole collection), exactly as the graphs
        // path does. The temp table is session-local and cleared on drop.
        let guard = self.search_cards_into_table(input.search.as_str(), SortMode::NoOrder)?;
        guard
            .col
            .tag_mastery_data(input.group_depth, input.mastered_threshold)
    }

    fn tag_mastery_data(
        &mut self,
        group_depth: u32,
        mastered_threshold: f64,
    ) -> Result<anki_proto::stats::TagMasteryResponse> {
        let threshold = if mastered_threshold > 0.0 {
            mastered_threshold
        } else {
            DEFAULT_MASTERED
        };
        let timing = self.timing_today()?;
        let cards = self.storage.all_searched_cards()?;
        let note_tags = self.searched_note_tags()?;
        let graded_per_card = self.searched_graded_reviews()?;

        let fsrs = FSRS::new(None).unwrap();
        let mut groups: HashMap<String, GroupAcc> = HashMap::new();
        let mut total_graded_reviews: u32 = 0;
        // Overall readiness band, accumulated over *unique* scored cards so a
        // multi-tag card isn't double-counted in the collection-wide number.
        let mut n: u32 = 0;
        let mut sum_recall = 0.0_f64;
        let mut sum_recall_sq = 0.0_f64;

        for card in &cards {
            let recall: Option<f64> = card.memory_state.map(|state| {
                let secs = card.seconds_since_last_review(&timing).unwrap_or_default();
                let decay = card.decay.unwrap_or(FSRS5_DEFAULT_DECAY);
                fsrs.current_retrievability_seconds(state.into(), secs, decay) as f64
            });
            let graded = graded_per_card.get(&card.id().0).copied().unwrap_or(0);
            total_graded_reviews = total_graded_reviews.saturating_add(graded);

            if let Some(r) = recall {
                n += 1;
                sum_recall += r;
                sum_recall_sq += r * r;
            }

            // Deduplicate group keys per card: belonging to a group twice (e.g.
            // two tags collapsing to the same prefix) still counts the card once.
            let mut keys: HashSet<String> = HashSet::new();
            match note_tags.get(&card.note_id().0) {
                Some(tags) if !tags.trim().is_empty() => {
                    for tag in split_tags(tags) {
                        keys.insert(group_key(tag, group_depth));
                    }
                }
                _ => {}
            }
            if keys.is_empty() {
                keys.insert(UNTAGGED.to_string());
            }

            for key in keys {
                let acc = groups.entry(key).or_default();
                acc.total_cards += 1;
                acc.graded_reviews = acc.graded_reviews.saturating_add(graded);
                if let Some(r) = recall {
                    acc.cards_with_state += 1;
                    acc.sum_recall += r;
                    if r >= threshold {
                        acc.mastered_cards += 1;
                    }
                }
            }
        }

        let mut group_list: Vec<_> = groups
            .into_iter()
            .map(
                |(tag, acc)| anki_proto::stats::tag_mastery_response::Group {
                    tag,
                    total_cards: acc.total_cards,
                    cards_with_state: acc.cards_with_state,
                    mastered_cards: acc.mastered_cards,
                    average_recall: if acc.cards_with_state > 0 {
                        acc.sum_recall / acc.cards_with_state as f64
                    } else {
                        0.0
                    },
                    graded_reviews: acc.graded_reviews,
                },
            )
            .collect();
        // Deterministic output.
        group_list.sort_by(|a, b| a.tag.cmp(&b.tag));

        let topics_with_reviews = group_list.iter().filter(|g| g.graded_reviews > 0).count() as u32;
        // Give-up rule: enough evidence only when both bars are cleared.
        let enough_data = total_graded_reviews >= MIN_GRADED_REVIEWS
            && topics_with_reviews >= MIN_TOPICS_WITH_REVIEWS;

        let (overall_mean_recall, overall_ci_low, overall_ci_high) = if n > 0 {
            let mean = sum_recall / n as f64;
            // Population variance of the per-card recalls, guarded against the
            // tiny negative values floating-point can produce.
            let variance = (sum_recall_sq / n as f64 - mean * mean).max(0.0);
            let std_dev = variance.sqrt();
            let margin = Z_90 * std_dev / (n as f64).sqrt();
            (
                mean,
                (mean - margin).clamp(0.0, 1.0),
                (mean + margin).clamp(0.0, 1.0),
            )
        } else {
            (0.0, 0.0, 0.0)
        };

        Ok(anki_proto::stats::TagMasteryResponse {
            threshold_used: threshold,
            total_graded_reviews,
            topics_with_reviews,
            groups: group_list,
            overall_mean_recall,
            overall_ci_low,
            overall_ci_high,
            overall_n: n,
            enough_data,
        })
    }

    /// note_id -> raw `tags` column, for the searched cards' notes.
    fn searched_note_tags(&self) -> Result<HashMap<i64, String>> {
        let mut stmt = self.storage.db.prepare(
            "SELECT id, tags FROM notes WHERE id IN \
             (SELECT nid FROM cards WHERE id IN (SELECT cid FROM search_cids))",
        )?;
        let mut map = HashMap::new();
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            map.insert(row.get(0)?, row.get(1)?);
        }
        Ok(map)
    }

    /// card_id -> count of graded reviews (a real rating, excluding cramming),
    /// for the searched cards.
    fn searched_graded_reviews(&self) -> Result<HashMap<i64, u32>> {
        let mut stmt = self.storage.db.prepare(
            "SELECT cid, count(*) FROM revlog \
             WHERE ease > 0 AND NOT (type = 3 AND factor = 0) \
             AND cid IN (SELECT cid FROM search_cids) GROUP BY cid",
        )?;
        let mut map = HashMap::new();
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let cid: i64 = row.get(0)?;
            let count: i64 = row.get(1)?;
            map.insert(cid, count as u32);
        }
        Ok(map)
    }
}

/// The first `group_depth` `::` components of `tag` (whole tag if depth is 0).
fn group_key(tag: &str, group_depth: u32) -> String {
    if group_depth == 0 {
        tag.to_string()
    } else {
        tag.split("::")
            .take(group_depth as usize)
            .collect::<Vec<_>>()
            .join("::")
    }
}

#[cfg(test)]
mod test {
    use anki_proto::stats::tag_mastery_response::Group;
    use anki_proto::stats::TagMasteryRequest;
    use anki_proto::stats::TagMasteryResponse;

    use super::*;
    use crate::card::CardType;
    use crate::card::FsrsMemoryState;
    use crate::tests::NoteAdder;

    fn req(group_depth: u32) -> TagMasteryRequest {
        TagMasteryRequest {
            group_depth,
            mastered_threshold: 0.0,
            search: String::new(),
        }
    }

    fn group<'a>(resp: &'a TagMasteryResponse, tag: &str) -> &'a Group {
        resp.groups
            .iter()
            .find(|g| g.tag == tag)
            .unwrap_or_else(|| panic!("group {tag} not found in {:?}", resp.groups))
    }

    /// Adds a basic (single-card) note with the given tags; returns its card
    /// id.
    fn add_note_with_tags(col: &mut Collection, front: &str, tags: &[&str]) -> CardId {
        let mut note = NoteAdder::basic(col).fields(&[front, "a"]).note();
        note.tags = tags.iter().map(|t| (*t).to_string()).collect();
        col.add_note(&mut note, DeckId(1)).unwrap();
        col.storage.card_ids_of_notes(&[note.id]).unwrap()[0]
    }

    /// Gives a card FSRS memory state with a known stability and elapsed time.
    fn set_memory(col: &mut Collection, cid: CardId, stability: f32, elapsed_secs: i64) {
        let now = col.timing_today().unwrap().now;
        let mut card = col.storage.get_card(cid).unwrap().unwrap();
        card.ctype = CardType::Review;
        card.interval = 100;
        card.memory_state = Some(FsrsMemoryState {
            stability,
            difficulty: 5.0,
        });
        card.last_review_time = Some(TimestampSecs(now.0 - elapsed_secs));
        col.storage.update_card(&card).unwrap();
    }

    /// Same recall the implementation computes, for hand-checking averages.
    fn expected_recall(stability: f32, elapsed_secs: u32) -> f64 {
        FSRS::new(None).unwrap().current_retrievability_seconds(
            FsrsMemoryState {
                stability,
                difficulty: 5.0,
            }
            .into(),
            elapsed_secs,
            FSRS5_DEFAULT_DECAY,
        ) as f64
    }

    /// Inserts `n` graded (real-rating) review-log rows for a card, so the
    /// give-up rule sees them. `id_base` keeps ids unique across cards.
    fn add_graded_reviews(col: &mut Collection, cid: CardId, n: u32, id_base: i64) {
        for i in 0..n {
            let entry = crate::revlog::RevlogEntry {
                id: RevlogId(id_base + i as i64),
                cid,
                usn: Usn(0),
                button_chosen: 3,
                interval: 1,
                last_interval: 1,
                ease_factor: 2500,
                taken_millis: 1000,
                review_kind: crate::revlog::RevlogReviewKind::Review,
            };
            col.storage.add_revlog_entry(&entry, true).unwrap();
        }
    }

    /// Adds `topics` distinct top-level tags, each with `per_topic` graded
    /// reviews on its own card.
    fn seed_reviews(col: &mut Collection, topics: u32, per_topic: u32) {
        for t in 0..topics {
            let cid = add_note_with_tags(col, &format!("f{t}"), &[&format!("topic{t}")]);
            add_graded_reviews(col, cid, per_topic, 1_000_000_000 + (t as i64) * 1_000_000);
        }
    }

    const DAY: i64 = 86_400;

    /// New cards (no FSRS state) are counted in totals but contribute no
    /// recall.
    #[test]
    fn no_state_is_honest() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "a", &["bio::cell"]);
        add_note_with_tags(&mut col, "b", &["bio::cell"]);

        let resp = col.tag_mastery(req(1))?;
        let bio = group(&resp, "bio");
        assert_eq!(bio.total_cards, 2);
        assert_eq!(bio.cards_with_state, 0);
        assert_eq!(bio.mastered_cards, 0);
        assert_eq!(bio.average_recall, 0.0);
        // Overall band abstains.
        assert_eq!(resp.overall_n, 0);
        assert_eq!(resp.overall_mean_recall, 0.0);
        assert_eq!(resp.overall_ci_low, 0.0);
        assert_eq!(resp.overall_ci_high, 0.0);
        assert_eq!(resp.threshold_used, DEFAULT_MASTERED);
        Ok(())
    }

    /// Exact counts + averages across two top-level tags with known states.
    #[test]
    fn aggregation_matches_hand_computed() -> Result<()> {
        let mut col = Collection::new();
        // bio: one freshly-reviewed (recall ~1.0 -> mastered) and one long
        // overdue (recall well below threshold -> not mastered).
        let a = add_note_with_tags(&mut col, "a", &["bio::cell"]);
        let b = add_note_with_tags(&mut col, "b", &["bio::cell"]);
        set_memory(&mut col, a, 1000.0, 0);
        set_memory(&mut col, b, 10.0, 200 * DAY);
        // chem: a new card with no state.
        add_note_with_tags(&mut col, "c", &["chem::acid"]);

        let resp = col.tag_mastery(req(1))?;

        let bio = group(&resp, "bio");
        assert_eq!(bio.total_cards, 2);
        assert_eq!(bio.cards_with_state, 2);
        assert_eq!(bio.mastered_cards, 1);
        let expected_bio =
            (expected_recall(1000.0, 0) + expected_recall(10.0, (200 * DAY) as u32)) / 2.0;
        assert!(
            (bio.average_recall - expected_bio).abs() < 1e-3,
            "got {} want {}",
            bio.average_recall,
            expected_bio
        );

        let chem = group(&resp, "chem");
        assert_eq!(chem.total_cards, 1);
        assert_eq!(chem.cards_with_state, 0);
        assert_eq!(chem.average_recall, 0.0);

        // Overall band covers only the two scored cards.
        assert_eq!(resp.overall_n, 2);
        assert!((resp.overall_mean_recall - expected_bio).abs() < 1e-3);
        assert!(resp.overall_ci_low <= resp.overall_mean_recall);
        assert!(resp.overall_ci_high >= resp.overall_mean_recall);
        assert!(resp.overall_ci_low >= 0.0 && resp.overall_ci_high <= 1.0);
        Ok(())
    }

    /// Multi-tag cards land in every group; untagged cards get the sentinel;
    /// group_depth collapses the hierarchy to a prefix.
    #[test]
    fn tag_handling() -> Result<()> {
        let mut col = Collection::new();
        // Two tags -> counted under both top-level groups.
        add_note_with_tags(&mut col, "a", &["bio::cell", "chem::acid"]);
        // Deeper hierarchy collapses to its first component at depth 1.
        add_note_with_tags(&mut col, "b", &["bio::cell::membrane"]);
        // No tags -> "(untagged)".
        add_note_with_tags(&mut col, "c", &[]);

        let resp = col.tag_mastery(req(1))?;
        assert_eq!(group(&resp, "bio").total_cards, 2);
        assert_eq!(group(&resp, "chem").total_cards, 1);
        assert_eq!(group(&resp, UNTAGGED).total_cards, 1);

        // Whole-tag grouping (depth 0) keeps the full hierarchy.
        let resp0 = col.tag_mastery(req(0))?;
        assert_eq!(group(&resp0, "bio::cell").total_cards, 1);
        assert_eq!(group(&resp0, "bio::cell::membrane").total_cards, 1);
        assert_eq!(group(&resp0, "chem::acid").total_cards, 1);
        Ok(())
    }

    /// Give-up rule: enough_data flips on only when BOTH the graded-review and
    /// distinct-topic bars are cleared.
    #[test]
    fn give_up_rule_boundary() -> Result<()> {
        // Exactly at both bars: 5 topics x 30 reviews = 150 graded reviews.
        let mut col = Collection::new();
        seed_reviews(&mut col, 5, 30);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.total_graded_reviews, 150);
        assert_eq!(resp.topics_with_reviews, 5);
        assert!(resp.enough_data);

        // One review short (149 across 5 topics) -> abstain.
        let mut col = Collection::new();
        seed_reviews(&mut col, 4, 30);
        seed_reviews_offset(&mut col, 29);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.total_graded_reviews, 149);
        assert_eq!(resp.topics_with_reviews, 5);
        assert!(!resp.enough_data);

        // Plenty of reviews but only 4 topics -> abstain.
        let mut col = Collection::new();
        seed_reviews(&mut col, 4, 50);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.total_graded_reviews, 200);
        assert_eq!(resp.topics_with_reviews, 4);
        assert!(!resp.enough_data);
        Ok(())
    }

    /// A 5th topic carrying just `per_topic` reviews, id-disjoint from
    /// `seed_reviews(_, 4, _)`.
    fn seed_reviews_offset(col: &mut Collection, per_topic: u32) {
        let cid = add_note_with_tags(col, "f4", &["topic4"]);
        add_graded_reviews(col, cid, per_topic, 1_000_000_000 + 4 * 1_000_000);
    }
}
