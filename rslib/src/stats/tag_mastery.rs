// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Read-only per-tag mastery aggregation + an overall memory-readiness band.
//! Also home to `card_topics`, a batched card->depth-2-topic lookup.
//!
//! Mirrors the existing retrievability graph (`super::graphs::retrievability`):
//! a single pass over the searched cards, computing each card's *current*
//! FSRS retrievability via the engine's own helper and grouping by the first
//! `group_depth` components of its note's `::` tag hierarchy. Writes nothing.

use std::collections::HashMap;
use std::collections::HashSet;

use fsrs::FSRS;
use fsrs::FSRS5_DEFAULT_DECAY;
use rusqlite::params_from_iter;

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
        let (graded_per_card, last_graded_revlog_id) = self.searched_graded_reviews()?;

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

        let topics_total = group_list.len() as u32;
        let topics_covered = group_list
            .iter()
            .filter(|g| g.cards_with_state >= 1)
            .count() as u32;

        // Confidence in the overall band; evaluated independently of
        // `enough_data` (the give-up rule is a UI concern, not part of this
        // derivation).
        let half_width = (overall_ci_high - overall_ci_low) / 2.0;
        let how_sure = if n == 0 {
            anki_proto::stats::tag_mastery_response::HowSure::Insufficient
        } else if half_width <= 0.02 && n >= 100 {
            anki_proto::stats::tag_mastery_response::HowSure::High
        } else if half_width <= 0.05 && n >= 30 {
            anki_proto::stats::tag_mastery_response::HowSure::Medium
        } else {
            anki_proto::stats::tag_mastery_response::HowSure::Low
        };

        // `revlog.id` is milliseconds; NULL (no graded rows) -> 0.
        let last_updated_secs = last_graded_revlog_id.map(|id| id / 1000).unwrap_or(0);

        // Lowest-average_recall covered group; if none covered, the largest
        // (by total_cards) group; if no groups at all, "". `group_list` is
        // already tag-sorted ascending, so iterating in order with strict
        // </> comparisons naturally tie-breaks to the first alphabetically.
        let next_topic = {
            let mut covered = group_list.iter().filter(|g| g.cards_with_state >= 1);
            if let Some(first) = covered.next() {
                let best = covered.fold(first, |best, g| {
                    if g.average_recall < best.average_recall {
                        g
                    } else {
                        best
                    }
                });
                best.tag.clone()
            } else if let Some(first) = group_list.first() {
                let best = group_list.iter().skip(1).fold(first, |best, g| {
                    if g.total_cards > best.total_cards {
                        g
                    } else {
                        best
                    }
                });
                best.tag.clone()
            } else {
                String::new()
            }
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
            topics_total,
            topics_covered,
            how_sure: how_sure as i32,
            last_updated_secs,
            next_topic,
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

    /// Batched card -> depth-2 (by default) topic lookup for an explicit id
    /// list, e.g. a reviewer's ~60-card window. Read-only: a single indexed
    /// query, no search temp table, no writes.
    pub(crate) fn card_topics(
        &mut self,
        input: anki_proto::stats::CardTopicsRequest,
    ) -> Result<anki_proto::stats::CardTopicsResponse> {
        if input.card_ids.is_empty() {
            return Ok(anki_proto::stats::CardTopicsResponse {
                entries: Vec::new(),
                untagged_sentinel: UNTAGGED.to_string(),
            });
        }

        let card_note_tags = self.card_ids_note_tags(&input.card_ids)?;
        let entries = input
            .card_ids
            .iter()
            .filter_map(|cid| {
                card_note_tags.get(cid).map(|tags| {
                    let topic = split_tags(tags)
                        .map(|tag| group_key(tag, input.group_depth))
                        .find(|key| key.contains("::"))
                        .unwrap_or_else(|| UNTAGGED.to_string());
                    anki_proto::stats::card_topics_response::Entry {
                        card_id: *cid,
                        topic,
                    }
                })
            })
            .collect();

        Ok(anki_proto::stats::CardTopicsResponse {
            entries,
            untagged_sentinel: UNTAGGED.to_string(),
        })
    }

    /// card_id -> its note's raw `tags` column, for the exact given id list
    /// (only ids whose card and note both still exist are present).
    fn card_ids_note_tags(&self, card_ids: &[i64]) -> Result<HashMap<i64, String>> {
        let placeholders = std::iter::repeat("?")
            .take(card_ids.len())
            .collect::<Vec<_>>()
            .join(",");
        let mut stmt = self.storage.db.prepare(&format!(
            "SELECT c.id, n.tags FROM cards c JOIN notes n ON c.nid = n.id \
             WHERE c.id IN ({placeholders})"
        ))?;
        let mut map = HashMap::new();
        let mut rows = stmt.query(params_from_iter(card_ids.iter()))?;
        while let Some(row) = rows.next()? {
            map.insert(row.get(0)?, row.get(1)?);
        }
        Ok(map)
    }

    /// card_id -> count of graded reviews (a real rating, excluding cramming),
    /// for the searched cards, plus the overall MAX(id) across those same
    /// rows (None if there are none). `max(max(id)) OVER ()` folds the global
    /// aggregate into the same GROUP BY scan without a second query: the inner
    /// `max(id)` is each group's true max (a real aggregate, not a bare
    /// column), and the window then takes the max across groups.
    fn searched_graded_reviews(&self) -> Result<(HashMap<i64, u32>, Option<i64>)> {
        let mut stmt = self.storage.db.prepare(
            "SELECT cid, count(*), max(max(id)) OVER () FROM revlog \
             WHERE ease > 0 AND NOT (type = 3 AND factor = 0) \
             AND cid IN (SELECT cid FROM search_cids) GROUP BY cid",
        )?;
        let mut map = HashMap::new();
        let mut last_id: Option<i64> = None;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let cid: i64 = row.get(0)?;
            let count: i64 = row.get(1)?;
            let max_id: Option<i64> = row.get(2)?;
            map.insert(cid, count as u32);
            last_id = max_id;
        }
        Ok((map, last_id))
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

    // ----------------------------------------------------------------------
    // AC4: topics_total / topics_covered counts
    // ----------------------------------------------------------------------

    /// `topics_total` counts every group found; `topics_covered` counts only
    /// groups with >=1 scored (has FSRS state) card, independent of the
    /// give-up rule.
    #[test]
    fn topics_total_and_covered_counts() -> Result<()> {
        let mut col = Collection::new();
        // bio: covered (one card gets memory state).
        let bio_a = add_note_with_tags(&mut col, "a", &["bio::cell"]);
        add_note_with_tags(&mut col, "a2", &["bio::cell"]); // no state
        set_memory(&mut col, bio_a, 50.0, DAY);
        // chem: covered (one card gets memory state).
        let chem_a = add_note_with_tags(&mut col, "b", &["chem::acid"]);
        set_memory(&mut col, chem_a, 50.0, DAY);
        // phys: not covered (new cards only).
        add_note_with_tags(&mut col, "c", &["phys::motion"]);
        // math: not covered (new cards only).
        add_note_with_tags(&mut col, "d", &["math::algebra"]);
        // untagged sentinel group: not covered.
        add_note_with_tags(&mut col, "e", &[]);

        let resp = col.tag_mastery(req(1))?;
        // 5 groups total: bio, chem, phys, math, (untagged).
        assert_eq!(resp.topics_total, 5);
        // Exactly 2 groups (bio, chem) have >=1 scored card.
        assert_eq!(resp.topics_covered, 2);
        assert!(resp.topics_covered <= resp.topics_total);
        Ok(())
    }

    /// Empty collection: zero groups, no divide-by-zero, no panic.
    #[test]
    fn topics_total_zero_on_empty_collection() -> Result<()> {
        let mut col = Collection::new();
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.topics_total, 0);
        assert_eq!(resp.topics_covered, 0);
        assert_eq!(resp.overall_n, 0);
        Ok(())
    }

    // ----------------------------------------------------------------------
    // AC4: how_sure boundaries
    // ----------------------------------------------------------------------

    /// Builds `n` cards under one tag, each with identical FSRS state
    /// (stability=10, elapsed=10 days), which the FSRS engine maps to a
    /// recall of exactly 0.9 per card (confirmed empirically against
    /// `expected_recall(10.0, 10*DAY)`). Because every card has the *same*
    /// recall, the per-card variance is exactly zero:
    ///   variance = sum_recall_sq/n - mean^2 == mean^2 - mean^2 == 0
    /// so `half_width = Z_90 * sqrt(0) / sqrt(n) == 0` for *every* n >= 1.
    /// With half_width pinned at 0 (<= 0.02 and <= 0.05 both trivially hold),
    /// the how_sure bucket becomes a pure function of `n` via the `n >= ...`
    /// thresholds in the contract, letting us hit every boundary in the edge
    /// matrix (contracts/data-model.md §1) deterministically:
    ///   n=1  -> LOW    (half_width<=0.05 but n<30)
    ///   n=29 -> LOW    (n<30)
    ///   n=30 -> MEDIUM (half_width<=0.05 and n>=30)
    ///   n=99 -> MEDIUM (half_width<=0.02 but n<100, falls through to MEDIUM)
    ///   n=100-> HIGH   (half_width<=0.02 and n>=100)
    fn build_zero_variance_collection(n: u32) -> Collection {
        let mut col = Collection::new();
        for i in 0..n {
            let cid = add_note_with_tags(&mut col, &format!("c{i}"), &["t"]);
            set_memory(&mut col, cid, 10.0, 10 * DAY);
        }
        col
    }

    #[test]
    fn how_sure_insufficient_when_n_is_zero_empty_collection() -> Result<()> {
        let mut col = Collection::new();
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.overall_n, 0);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::Insufficient as i32
        );
        Ok(())
    }

    #[test]
    fn how_sure_insufficient_when_all_cards_unscored() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "a", &["bio::cell"]);
        add_note_with_tags(&mut col, "b", &["bio::cell"]);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.overall_n, 0);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::Insufficient as i32
        );
        Ok(())
    }

    #[test]
    fn how_sure_low_below_medium_threshold() -> Result<()> {
        // n=1: half_width==0 (<=0.05) but n<30 -> LOW, not MEDIUM.
        let mut col = build_zero_variance_collection(1);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.overall_n, 1);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::Low as i32
        );

        // n=29: one short of the MEDIUM n-threshold -> still LOW.
        let mut col = build_zero_variance_collection(29);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.overall_n, 29);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::Low as i32
        );
        Ok(())
    }

    #[test]
    fn how_sure_medium_at_and_above_threshold() -> Result<()> {
        // n=30: half_width==0 (<=0.05) and n>=30 -> MEDIUM.
        let mut col = build_zero_variance_collection(30);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.overall_n, 30);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::Medium as i32
        );

        // n=99: below the HIGH n-threshold (100) -> falls through to MEDIUM
        // (half_width<=0.05 and n>=30 still holds).
        let mut col = build_zero_variance_collection(99);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.overall_n, 99);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::Medium as i32
        );
        Ok(())
    }

    #[test]
    fn how_sure_high_at_threshold() -> Result<()> {
        // n=100: half_width==0 (<=0.02) and n>=100 -> HIGH.
        let mut col = build_zero_variance_collection(100);
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.overall_n, 100);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::High as i32
        );
        Ok(())
    }

    // ----------------------------------------------------------------------
    // AC4: next_topic selection + tie-break + fallback
    // ----------------------------------------------------------------------

    /// (a) Two covered topics with distinct average_recall -> the lower one
    /// wins, regardless of alphabetical order.
    #[test]
    fn next_topic_lower_recall_covered_wins() -> Result<()> {
        let mut col = Collection::new();
        // "zzz" has the lower recall (long overdue) but sorts last
        // alphabetically -- proves selection is by recall, not tag order.
        let low = add_note_with_tags(&mut col, "a", &["zzz"]);
        set_memory(&mut col, low, 10.0, 200 * DAY);
        let high = add_note_with_tags(&mut col, "b", &["aaa"]);
        set_memory(&mut col, high, 1000.0, 0);

        let resp = col.tag_mastery(req(1))?;
        assert!(group(&resp, "zzz").average_recall < group(&resp, "aaa").average_recall);
        assert_eq!(resp.next_topic, "zzz");
        Ok(())
    }

    /// (b) Two covered topics tied on average_recall -> the lexicographically
    /// first tag wins (group_list is tag-sorted; strict `<` comparison keeps
    /// the first-seen winner on a tie).
    #[test]
    fn next_topic_tie_break_lexicographic_when_covered() -> Result<()> {
        let mut col = Collection::new();
        // Identical stability/elapsed on comparable single-card groups ->
        // identical average_recall.
        let a = add_note_with_tags(&mut col, "a", &["bbb"]);
        set_memory(&mut col, a, 50.0, 10 * DAY);
        let b = add_note_with_tags(&mut col, "b", &["aaa"]);
        set_memory(&mut col, b, 50.0, 10 * DAY);

        let resp = col.tag_mastery(req(1))?;
        assert_eq!(
            group(&resp, "aaa").average_recall,
            group(&resp, "bbb").average_recall
        );
        assert_eq!(resp.next_topic, "aaa");
        Ok(())
    }

    /// (c) No covered topics (no scored cards) but >=1 group exists -> the
    /// group with the largest total_cards wins; ties break lexicographically.
    #[test]
    fn next_topic_fallback_to_largest_untested_topic() -> Result<()> {
        let mut col = Collection::new();
        // "zzz" has more cards than "aaa" -- proves selection is by size, not
        // tag order.
        add_note_with_tags(&mut col, "a", &["zzz"]);
        add_note_with_tags(&mut col, "b", &["zzz"]);
        add_note_with_tags(&mut col, "c", &["zzz"]);
        add_note_with_tags(&mut col, "d", &["aaa"]);

        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.topics_covered, 0);
        assert_eq!(group(&resp, "zzz").total_cards, 3);
        assert_eq!(group(&resp, "aaa").total_cards, 1);
        assert_eq!(resp.next_topic, "zzz");
        Ok(())
    }

    /// (c, tie) No covered topics, equal total_cards -> lexicographically
    /// first tag wins.
    #[test]
    fn next_topic_fallback_tie_break_lexicographic() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "a", &["bbb"]);
        add_note_with_tags(&mut col, "b", &["aaa"]);

        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.topics_covered, 0);
        assert_eq!(
            group(&resp, "aaa").total_cards,
            group(&resp, "bbb").total_cards
        );
        assert_eq!(resp.next_topic, "aaa");
        Ok(())
    }

    /// (d) Empty collection (0 groups) -> next_topic == "".
    #[test]
    fn next_topic_empty_string_when_no_groups() -> Result<()> {
        let mut col = Collection::new();
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.topics_total, 0);
        assert_eq!(resp.next_topic, "");
        Ok(())
    }

    // ----------------------------------------------------------------------
    // AC4 / AC2: abstain path still returns the new fields honestly
    // ----------------------------------------------------------------------

    /// When the give-up rule is unmet (enough_data == false), the new fields
    /// must still be computed honestly rather than being zeroed/defaulted:
    /// how_sure reflects the real overall_n, next_topic is still the real
    /// selection-rule result, and topics_total/topics_covered are unaffected
    /// by the give-up rule (that rule only gates `enough_data`).
    #[test]
    fn abstain_path_still_returns_honest_new_fields() -> Result<()> {
        // Only 4 distinct topics with reviews (need 5) -> give-up rule unmet,
        // even though there are plenty of graded reviews and real FSRS state.
        let mut col = Collection::new();
        for t in 0..4u32 {
            let cid = add_note_with_tags(&mut col, &format!("f{t}"), &[&format!("topic{t}")]);
            add_graded_reviews(&mut col, cid, 50, 1_000_000_000 + (t as i64) * 1_000_000);
            if t == 0 {
                // Give exactly one of the seeded cards real FSRS memory state
                // so overall_n>0 and next_topic has a covered candidate,
                // proving the abstain branch doesn't suppress those honest
                // values either.
                set_memory(&mut col, cid, 10.0, 10 * DAY);
            }
        }

        let resp = col.tag_mastery(req(1))?;
        assert!(
            !resp.enough_data,
            "give-up rule should still be unmet (4 topics < 5)"
        );
        // Honest counts: 4 topic groups, 1 of them covered (has FSRS state).
        assert_eq!(resp.topics_total, 4);
        assert_eq!(resp.topics_covered, 1);
        // how_sure computed from the real overall_n (1 scored card) -> LOW,
        // never forced to INSUFFICIENT just because enough_data is false.
        assert_eq!(resp.overall_n, 1);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::Low as i32
        );
        // next_topic still computable: the one covered group wins.
        assert_eq!(resp.next_topic, "topic0");
        Ok(())
    }

    /// Abstain-with-fields when there is no FSRS state at all (n==0): the
    /// give-up rule is unmet AND how_sure must honestly report INSUFFICIENT,
    /// while next_topic still falls back to the largest untested topic.
    #[test]
    fn abstain_path_with_zero_state_reports_insufficient() -> Result<()> {
        let mut col = Collection::new();
        seed_reviews(&mut col, 4, 50);

        let resp = col.tag_mastery(req(1))?;
        assert!(!resp.enough_data);
        assert_eq!(resp.overall_n, 0);
        assert_eq!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::Insufficient as i32
        );
        assert_eq!(resp.topics_total, 4);
        assert_eq!(resp.topics_covered, 0);
        // No covered topics -> fallback to largest total_cards group; all
        // four topics have exactly 1 card each (seed_reviews adds one card
        // per topic) -> lexicographic tie-break picks "topic0".
        assert_eq!(resp.next_topic, "topic0");
        Ok(())
    }

    // ----------------------------------------------------------------------
    // AC6: tag_mastery is fully read-only
    // ----------------------------------------------------------------------

    /// Proves `col.tag_mastery(...)` leaves the collection completely
    /// unmodified: undo/redo state, the last-step counter, and the
    /// mod/scm/ls timestamps are identical before and after the call. The
    /// timestamp/undo assertions run *before* `check_database()`, since
    /// dbcheck itself mutates timestamps -- calling it first would
    /// contaminate the very thing we're proving `tag_mastery` doesn't touch.
    #[test]
    fn tag_mastery_is_read_only() -> Result<()> {
        let mut col = Collection::new();
        // Perform a real undoable op first so can_undo() is Some(_) and we
        // can prove tag_mastery doesn't add/remove/alter undo steps.
        let a = add_note_with_tags(&mut col, "a", &["bio::cell"]);
        add_note_with_tags(&mut col, "b", &["chem::acid"]);
        set_memory(&mut col, a, 50.0, DAY);

        let undo_before = col.can_undo().cloned();
        let redo_before = col.can_redo().cloned();
        let last_step_before = col.undo_status().last_step;
        let ts_before = col.storage.get_collection_timestamps()?;

        assert!(undo_before.is_some(), "expected a prior undoable op");

        // The call under test. Exercise more than the default request shape
        // (non-zero group_depth, a search filter, a non-default threshold)
        // to make sure no code path along the way writes anything.
        let _ = col.tag_mastery(TagMasteryRequest {
            group_depth: 1,
            mastered_threshold: 0.75,
            search: String::new(),
        })?;

        // Undo/redo state and step counter must be byte-for-byte unchanged.
        assert_eq!(col.can_undo().cloned(), undo_before, "can_undo changed");
        assert_eq!(col.can_redo().cloned(), redo_before, "can_redo changed");
        assert_eq!(
            col.undo_status().last_step,
            last_step_before,
            "undo step counter changed"
        );

        // Collection timestamps (mod/scm/ls) must be unchanged. Compared
        // before check_database(), which itself mutates timestamps.
        let ts_after = col.storage.get_collection_timestamps()?;
        assert_eq!(
            ts_after.collection_change.0, ts_before.collection_change.0,
            "mod (collection_change) timestamp changed"
        );
        assert_eq!(
            ts_after.schema_change.0, ts_before.schema_change.0,
            "scm (schema_change) timestamp changed"
        );
        assert_eq!(
            ts_after.last_sync.0, ts_before.last_sync.0,
            "ls (last_sync) timestamp changed"
        );

        // Only now: prove the DB is left in a clean, consistent state.
        assert_eq!(col.check_database()?, Default::default());
        Ok(())
    }

    /// last_updated_secs = the MAX graded revlog id (not min, not per-card),
    /// converted ms -> s. Two cards, the older one added first, prove the
    /// global max wins across cards.
    #[test]
    fn last_updated_secs_is_global_max_in_seconds() -> Result<()> {
        let mut col = Collection::new();
        let a = add_note_with_tags(&mut col, "a", &["bio::cell"]);
        let b = add_note_with_tags(&mut col, "b", &["bio::cell"]);
        // b's reviews are OLDER (smaller ids); a's are NEWER (larger ids).
        add_graded_reviews(&mut col, b, 3, 1_000_000_000_000);
        add_graded_reviews(&mut col, a, 3, 2_000_000_000_000);
        let resp = col.tag_mastery(req(1))?;
        // Global MAX id = 2_000_000_000_002 ms -> integer-divided by 1000.
        assert_eq!(resp.last_updated_secs, 2_000_000_000_002 / 1000);
        Ok(())
    }

    /// The new fields honor the request's `search` scope, not the whole
    /// collection.
    #[test]
    fn new_fields_respect_search_scope() -> Result<()> {
        let mut col = Collection::new();
        let a = add_note_with_tags(&mut col, "a", &["alpha"]);
        add_note_with_tags(&mut col, "b", &["beta"]);
        set_memory(&mut col, a, 1000.0, 0);
        let resp = col.tag_mastery(TagMasteryRequest {
            group_depth: 1,
            mastered_threshold: 0.0,
            search: "tag:alpha".to_string(),
        })?;
        assert_eq!(resp.topics_total, 1);
        assert!(resp.groups.iter().any(|g| g.tag == "alpha"));
        assert!(!resp.groups.iter().any(|g| g.tag == "beta"));
        Ok(())
    }

    /// how_sure reflects the CI-width leg, not only n: high per-card variance
    /// keeps it below HIGH even with n >= 100. (The zero-variance fixtures pin
    /// half-width to 0 and can't exercise this dimension.)
    #[test]
    fn how_sure_reflects_ci_width_not_just_n() -> Result<()> {
        let mut col = Collection::new();
        for i in 0..50 {
            let c = add_note_with_tags(&mut col, &format!("hi{i}"), &["bio"]);
            set_memory(&mut col, c, 100_000.0, 0); // recall ~1.0
        }
        for i in 0..50 {
            let c = add_note_with_tags(&mut col, &format!("lo{i}"), &["bio"]);
            set_memory(&mut col, c, 5.0, 100 * DAY); // much lower recall
        }
        let resp = col.tag_mastery(req(1))?;
        assert_eq!(resp.overall_n, 100);
        let half_width = (resp.overall_ci_high - resp.overall_ci_low) / 2.0;
        assert!(
            half_width > 0.02,
            "half_width={half_width} should exceed the HIGH threshold"
        );
        assert_ne!(
            resp.how_sure,
            anki_proto::stats::tag_mastery_response::HowSure::High as i32,
            "high variance must prevent HIGH even with n>=100"
        );
        Ok(())
    }

    /// A multi-tag scored card is counted under each of its topics, but the
    /// collection-wide band (overall_n) counts it once.
    #[test]
    fn multi_tag_card_dedups_overall_but_counts_each_topic() -> Result<()> {
        let mut col = Collection::new();
        let c = add_note_with_tags(&mut col, "a", &["bio::cell", "chem::acid"]);
        set_memory(&mut col, c, 1000.0, 0);
        let resp = col.tag_mastery(req(1))?; // depth 1 -> "bio", "chem"
        assert_eq!(resp.topics_covered, 2);
        assert_eq!(group(&resp, "bio").cards_with_state, 1);
        assert_eq!(group(&resp, "chem").cards_with_state, 1);
        assert_eq!(resp.overall_n, 1); // deduped
                                       // Both covered topics tie on recall (same card) -> lexicographic first.
        assert_eq!(resp.next_topic, "bio");
        Ok(())
    }
}
