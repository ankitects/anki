// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Ascent fork: the three separated scores.
//!
//! **Memory** is what the scheduler already knows — aggregated FSRS
//! retrievability, gated on coverage. **Performance** is novel-question
//! ability from probe outcomes, gated on a real observation count *and* an
//! interval width. **Readiness** — the projected 472–528 exam score — is
//! withheld, because no outcome-calibrated map from ability to that scale
//! exists and one of the four sections cannot be measured from flashcards at
//! all.
//!
//! The design is specified in `mcat-taxonomy/report.md`; [scores] implements
//! it and is where the statistics live. This module only supplies the data:
//! it maps cards to AAMC topics, aggregates, and reports what it could not
//! place.
//!
//! Nothing here writes to the collection, and nothing here feeds back into
//! FSRS or the scheduler.

pub(crate) mod beta;
mod scores;
mod taxonomy;

use std::collections::HashMap;

use anki_proto::stats::AscentScoresResponse;
use scores::*;
use taxonomy::ContentCategory;
use taxonomy::FoundationalConcept;
use taxonomy::CONTENT_CATEGORIES;
use taxonomy::FOUNDATIONAL_CONCEPTS;

use crate::config::BoolKey;
use crate::deckconfig::DeckConfigId;
use crate::prelude::*;
use crate::revlog::RevlogData;
use crate::timestamp::TimestampMillis;

// -------------------------------------------------------------------------
// Card → topic mapping (report.md §2.3)
// -------------------------------------------------------------------------

/// Where a card landed in the AAMC outline.
///
/// Tier A is a tag naming a content category. Tier B is the recommended
/// fallback: a tag naming only a foundational concept still places the card at
/// the finest grain AAMC actually publishes a weight for, so it stays fully
/// usable rather than being forced into a category it did not earn.
///
/// ponytail: the embedding and LLM tiers of §2.3 are not implemented. They
/// need a local model and a fitted margin threshold, and §2.3 is explicit that
/// a mapper whose accuracy is unmeasured must not ship. Everything they would
/// have caught lands in [Mapping::Unmapped], which is displayed rather than
/// hidden — so adding them later moves cards out of a visible bucket instead
/// of correcting a silent lie.
#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub(crate) enum Mapping {
    Category(&'static ContentCategory),
    Concept(&'static FoundationalConcept),
    Unmapped,
}

/// Parse an AAMC placement out of a note's tags. Content category wins over
/// foundational concept; ties within a level go to the first tag seen.
pub(crate) fn map_tags(tags: &str) -> Mapping {
    let mut concept = None;
    for tag in tags.split_whitespace() {
        for part in tag.split("::") {
            if let Some(cat) = parse_category(part) {
                return Mapping::Category(cat);
            }
            if concept.is_none() {
                concept = parse_concept(part);
            }
        }
    }
    concept.map(Mapping::Concept).unwrap_or(Mapping::Unmapped)
}

/// `4A`, `4A-Motion`, `1B_Enzymes` → that content category.
fn parse_category(part: &str) -> Option<&'static ContentCategory> {
    let head = part
        .split(['-', '_', '.'])
        .next()
        .filter(|h| !h.is_empty())?
        .to_ascii_uppercase();
    CONTENT_CATEGORIES.iter().find(|c| c.id == head)
}

/// `Foundational_Concept_4`, `foundational-concept-4`, `FC4` → that concept.
fn parse_concept(part: &str) -> Option<&'static FoundationalConcept> {
    let lower = part.to_ascii_lowercase();
    let digits = if let Some(rest) = lower.strip_prefix("fc") {
        rest.trim_start_matches(['-', '_'])
    } else {
        let normalised = lower.replace(['-', ' '], "_");
        if !normalised.starts_with("foundational_concept") {
            return None;
        }
        // work on the original casing-insensitive copy so the index lines up
        let rest = normalised["foundational_concept".len()..].to_string();
        return FOUNDATIONAL_CONCEPTS
            .iter()
            .find(|f| f.id == rest.trim_start_matches('_'));
    };
    FOUNDATIONAL_CONCEPTS.iter().find(|f| f.id == digits)
}

// -------------------------------------------------------------------------
// Aggregation
// -------------------------------------------------------------------------

#[derive(Default)]
struct Bucket {
    cards: usize,
    retrievabilities: Vec<f64>,
    probes: ProbeOutcomes,
}

impl Collection {
    pub fn ascent_scores(&mut self) -> Result<AscentScoresResponse> {
        let (calibration_sd, calibration_note) = self.measure_fsrs_calibration()?;

        // topic key -> bucket; card id -> topic key, so probe outcomes can be
        // routed to the same topic their parent card landed in.
        let mut buckets: HashMap<String, Bucket> = HashMap::new();
        let mut card_topic: HashMap<CardId, String> = HashMap::new();
        let mut total_cards = 0usize;
        let mut unmapped_cards = 0usize;

        let timing = self.timing_today()?;
        let mut stmt = self.storage.db.prepare(&format!(
            "select c.id, n.tags, extract_fsrs_retrievability(c.data, \
             case when c.odue != 0 then c.odue else c.due end, c.ivl, {}, {}, {}) \
             from cards c inner join notes n on n.id = c.nid",
            timing.days_elapsed, timing.next_day_at.0, timing.now.0
        ))?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            total_cards += 1;
            let card_id: CardId = row.get(0)?;
            let tags: String = row.get(1)?;
            // NULL means "no memory state", i.e. never studied. Never imputed.
            let retrievability: Option<f64> = row.get(2)?;
            let key = match map_tags(&tags) {
                Mapping::Category(c) => c.id.to_string(),
                Mapping::Concept(f) => format!("FC{}", f.id),
                Mapping::Unmapped => {
                    unmapped_cards += 1;
                    continue;
                }
            };
            let bucket = buckets.entry(key.clone()).or_default();
            bucket.cards += 1;
            if let Some(r) = retrievability {
                bucket.retrievabilities.push(r);
            }
            card_topic.insert(card_id, key);
        }
        drop(rows);
        drop(stmt);

        self.collect_probe_outcomes(&card_topic, &mut buckets)?;

        // The Performance prior is estimated across the user's *other* topics
        // and never from this topic's Memory score -- see [performance_score].
        let records: Vec<(f64, f64)> = buckets
            .values()
            .map(|b| {
                let n: f64 = b
                    .probes
                    .iter()
                    .map(|(_, age)| 0.5f64.powf(age / PROBE_HALF_LIFE_DAYS))
                    .sum();
                let y: f64 = b
                    .probes
                    .iter()
                    .filter(|(correct, _)| *correct)
                    .map(|(_, age)| 0.5f64.powf(age / PROBE_HALF_LIFE_DAYS))
                    .sum();
                (y, n)
            })
            .collect();
        let (prior_a, prior_b, prior_description) = empirical_bayes_prior(&records);

        let readiness = readiness(&ReadinessInputs {
            // No store of graded readiness forecasts exists, so this is a real
            // zero rather than an assumed one.
            calibration_outcomes: 0,
            // CARS has no content categories, so no card can ever map to it.
            cars_probes: 0,
        });
        let readiness_width = readiness_normalised_width(&readiness, None);

        let mut topics = Vec::new();
        let mut compounding_violations = Vec::new();
        for (key, title, section, grain) in topic_order() {
            let Some(bucket) = buckets.get(&key) else {
                continue;
            };
            let memory = match calibration_sd {
                Some(sd) => memory_score(&bucket.retrievabilities, bucket.cards, sd),
                None => Estimate {
                    point: None,
                    lower: 0.0,
                    upper: 1.0,
                    reportable: false,
                    reason: calibration_note.clone(),
                    unlock: String::new(),
                },
            };
            let (performance, ..) = performance_score(&bucket.probes, (prior_a, prior_b));
            compounding_violations.extend(
                check_compounding(&memory, &performance, readiness_width)
                    .into_iter()
                    .map(|v| format!("{key}: {v}")),
            );
            topics.push(anki_proto::stats::ascent_scores_response::Topic {
                id: key,
                title: title.to_string(),
                section: section.to_string(),
                grain: grain.to_string(),
                cards: bucket.cards as u32,
                studied_cards: bucket.retrievabilities.len() as u32,
                coverage: if bucket.cards > 0 {
                    bucket.retrievabilities.len() as f64 / bucket.cards as f64
                } else {
                    0.0
                },
                probes: bucket.probes.len() as u32,
                memory: Some(memory.into()),
                performance: Some(performance.into()),
            });
        }

        Ok(AscentScoresResponse {
            total_cards: total_cards as u32,
            mapped_cards: (total_cards - unmapped_cards) as u32,
            unmapped_cards: unmapped_cards as u32,
            topics,
            readiness: Some(anki_proto::stats::ascent_scores_response::Readiness {
                reportable: readiness.reportable,
                reasons: readiness.reasons,
                unlock: readiness.unlock,
            }),
            prior_description,
            calibration_sd,
            calibration_note,
            compounding_violations,
            interval_percent: (LEVEL * 100.0) as u32,
        })
    }

    /// Probe outcomes ride the revlog's `data` column; a review with a variant
    /// id recorded was answered against a probe rather than the original card.
    fn collect_probe_outcomes(
        &self,
        card_topic: &HashMap<CardId, String>,
        buckets: &mut HashMap<String, Bucket>,
    ) -> Result<()> {
        let now = TimestampMillis::now().0 as f64;
        let mut stmt = self
            .storage
            .db
            .prepare("select cid, id, ease, data from revlog where data != ''")?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let card_id: CardId = row.get(0)?;
            let revlog_id: i64 = row.get(1)?;
            let button_chosen: u8 = row.get(2)?;
            let data: String = row.get(3)?;
            if RevlogData::from_str(&data).variant_id.is_none() {
                continue;
            }
            // 0 is a manual reschedule, not an answer.
            if button_chosen == 0 {
                continue;
            }
            let Some(key) = card_topic.get(&card_id) else {
                continue;
            };
            let age_days = ((now - revlog_id as f64) / 86_400_000.0).max(0.0);
            buckets
                .entry(key.clone())
                .or_default()
                .probes
                .push((button_chosen > 1, age_days));
        }
        Ok(())
    }

    /// The Memory interval's calibration term: FSRS's own prediction error,
    /// measured against this collection's review log by bucketing past reviews
    /// by predicted retrievability and comparing with the observed pass rate
    /// (report.md §3.1). Not a constant, and not available on a thin log.
    ///
    /// ponytail: recomputed on every load. §3.1 wants it weekly; cache it
    /// alongside the other FSRS metrics if this screen ever gets slow.
    fn measure_fsrs_calibration(&mut self) -> Result<(Option<f64>, String)> {
        if !self.get_config_bool(BoolKey::Fsrs) {
            return Ok((
                None,
                "FSRS is turned off, so there is no per-card retrievability to aggregate. \
                 Memory needs it; turn FSRS on and study normally for a while."
                    .into(),
            ));
        }
        let params = self
            .get_deck_config(DeckConfigId(1), true)?
            .map(|conf| conf.fsrs_params().clone())
            .unwrap_or_default();
        match self.evaluate_params_legacy(&params, "", TimestampMillis(0)) {
            Ok(eval) => Ok((
                Some(eval.rmse_bins as f64),
                format!(
                    "FSRS's predictions are off by {:.3} on average against your own review \
                     log; every Memory range below includes that error.",
                    eval.rmse_bins
                ),
            )),
            Err(_) => Ok((
                None,
                "There is not enough review history yet to measure how well FSRS predicts \
                 your recall, so no Memory range can be honest about its own error."
                    .into(),
            )),
        }
    }
}

/// Display order: foundational concept by foundational concept, each with its
/// content categories first and its own FC-level bucket last. Returns
/// `(key, title, section, grain)`.
fn topic_order() -> Vec<(String, &'static str, &'static str, &'static str)> {
    let mut out = Vec::new();
    for fc in FOUNDATIONAL_CONCEPTS {
        for cat in CONTENT_CATEGORIES.iter().filter(|c| c.concept == fc.id) {
            out.push((
                cat.id.to_string(),
                cat.title,
                cat.section,
                "content-category",
            ));
        }
        out.push((
            format!("FC{}", fc.id),
            fc.title,
            fc.section,
            "foundational-concept",
        ));
    }
    out
}

impl From<Estimate> for anki_proto::stats::AscentEstimate {
    fn from(e: Estimate) -> Self {
        Self {
            point: e.point,
            lower: e.lower,
            upper: e.upper,
            reportable: e.reportable,
            reason: e.reason,
            unlock: e.unlock,
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn tags_map_to_the_finest_grain_they_earn() {
        // the AnKing-style AAMC branch documented in report.md §2.1
        assert_eq!(
            map_tags("#AK_MCAT_v2::#AAMC::Concepts::C/P::Foundational_Concept_4::4A-Motion"),
            Mapping::Category(CONTENT_CATEGORIES.iter().find(|c| c.id == "4A").unwrap())
        );
        // concept but no category: placed at FC level, which is the finest
        // grain AAMC publishes a weight for, rather than guessed at 4A..4E
        assert!(matches!(
            map_tags("#AK_MCAT_v2::#AAMC::Concepts::C/P::Foundational_Concept_4"),
            Mapping::Concept(f) if f.id == "4"
        ));
        assert!(matches!(map_tags("FC10"), Mapping::Concept(f) if f.id == "10"));
        // bare code, and lowercase
        assert!(matches!(map_tags("10a"), Mapping::Category(c) if c.id == "10A"));
    }

    /// The decks actually surveyed in report.md §2.1 carry chapter tags, one
    /// bare subject tag, or nothing at all. None of that is an AAMC code, and
    /// guessing from it is the worst corruption profile in the table there.
    #[test]
    fn real_world_deck_tags_are_left_unmapped() {
        for tags in [
            "",
            "biology",
            "TPR3 TPR8",
            "#AK_MCAT_v2::#Kaplan::Chapter_3",
            "4F",
            "99Z",
        ] {
            assert_eq!(map_tags(tags), Mapping::Unmapped, "{tags:?}");
        }
    }

    #[test]
    fn a_card_lands_in_exactly_one_topic() {
        // two AAMC codes on one note: first wins, and it is not double-counted
        assert!(matches!(
            map_tags("1A-Amino_Acids 3B-Respiratory"),
            Mapping::Category(c) if c.id == "1A"
        ));
    }

    #[test]
    fn taxonomy_matches_the_published_outline() {
        assert_eq!(CONTENT_CATEGORIES.len(), 31);
        assert_eq!(FOUNDATIONAL_CONCEPTS.len(), 10);
        for section in ["C/P", "B/B", "P/S"] {
            let total: u32 = FOUNDATIONAL_CONCEPTS
                .iter()
                .filter(|f| f.section == section)
                .map(|f| f.weight_pct)
                .sum();
            assert_eq!(total, 100, "{section} FC weights must sum to 100%");
        }
        // CARS has no foundational concepts and no content categories, so no
        // card can map into it -- which is exactly why readiness is withheld.
        assert!(!CONTENT_CATEGORIES.iter().any(|c| c.section == "CARS"));
        // every category sits under a known concept
        for cat in CONTENT_CATEGORIES {
            assert!(FOUNDATIONAL_CONCEPTS.iter().any(|f| f.id == cat.concept));
        }
    }

    #[test]
    fn topic_order_covers_every_topic_once() {
        let order = topic_order();
        assert_eq!(order.len(), 41);
        let mut keys: Vec<_> = order.iter().map(|(k, ..)| k.clone()).collect();
        keys.sort();
        let before = keys.len();
        keys.dedup();
        assert_eq!(keys.len(), before);
    }

    /// End to end against a real collection: untagged cards land in the
    /// unmapped bucket and are excluded from every score, and readiness is
    /// withheld with real reasons.
    #[test]
    fn scores_over_a_collection_report_what_could_not_be_placed() {
        use crate::tests::CardAdder;
        let mut col = Collection::new();
        CardAdder::new().siblings(3).add(&mut col);

        let out = col.ascent_scores().unwrap();
        assert_eq!(out.total_cards, 3);
        assert_eq!(out.unmapped_cards, 3, "untagged cards must be visible");
        assert_eq!(out.mapped_cards, 0);
        assert!(out.topics.is_empty());

        let readiness = out.readiness.unwrap();
        assert!(!readiness.reportable);
        assert_eq!(readiness.reasons.len(), 2);
        assert!(out.compounding_violations.is_empty());
        assert_eq!(out.interval_percent, 80);
    }

    /// Probe outcomes ride the revlog's data column and must land in the same
    /// topic their parent card did. Five correct answers out of five is the
    /// give-up rule's headline case: it is withheld, and the unlock is a
    /// computed count rather than a slogan.
    #[test]
    fn probe_outcomes_reach_the_topic_their_card_belongs_to() {
        use crate::revlog::RevlogEntry;
        use crate::tests::CardAdder;

        let mut col = Collection::new();
        let card_id = CardAdder::new().add(&mut col)[0].id;
        let mut note = col.get_all_notes()[0].clone();
        note.tags = vec!["#AAMC::Foundational_Concept_1::1A-Amino_Acids".into()];
        col.update_note(&mut note).unwrap();

        let probe = crate::probe::test::add_test_probe(&mut col, card_id, "a");
        let data = crate::revlog::RevlogData {
            variant_id: Some(probe.id),
        };
        for i in 0..5 {
            col.storage
                .add_revlog_entry(
                    &RevlogEntry {
                        id: RevlogId(TimestampMillis::now().0 + i),
                        cid: card_id,
                        button_chosen: 3,
                        data: data.to_data_string(),
                        ..Default::default()
                    },
                    false,
                )
                .unwrap();
        }

        let out = col.ascent_scores().unwrap();
        assert_eq!(out.unmapped_cards, 0);
        assert_eq!(out.mapped_cards, 1);
        let topic = out.topics.iter().find(|t| t.id == "1A").unwrap();
        assert_eq!(topic.section, "B/B");
        assert_eq!(topic.grain, "content-category");
        assert_eq!(topic.cards, 1);
        assert_eq!(topic.probes, 5);

        let performance = topic.performance.as_ref().unwrap();
        assert!(!performance.reportable, "5 probes is under the floor");
        assert!(performance.point.unwrap() < 0.85, "5/5 is not mastery");
        assert!(performance.upper < 1.0);
        assert!(
            performance.unlock.contains("more probes"),
            "{performance:?}"
        );

        // one card is nowhere near a topic-level Memory claim
        let memory = topic.memory.as_ref().unwrap();
        assert!(!memory.reportable);
    }
}
