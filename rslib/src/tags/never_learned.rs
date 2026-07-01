// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! "I never learned this" — topic-level flagging that bulk-tags and suspends
//! every card in a topic, plus a read-only listing of everything currently
//! flagged. Mirrors `crate::stats::tag_mastery`'s scoping/grouping shape but
//! returns per-topic card lists instead of aggregate stats, and adds a
//! TOPIC-level mutation with a per-card suspend-ownership marker.

use std::collections::HashMap;
use std::collections::HashSet;

use serde_json::Map;
use serde_json::Value;

use super::matcher::TagMatcher;
use super::split_tags;
use crate::card::CardQueue;
use crate::config::SchedulerVersion;
use crate::prelude::*;
use crate::search::SortMode;

/// Single source of truth for the never-learned tag literal. The Python
/// constant in `pylib/anki/tags.py` must use the identical literal.
pub(crate) const NEVER_LEARNED_TAG: &str = "NeverLearned";

/// Sentinel group for cards whose note has no qualifying `::` topic tag.
/// Distinct from tag_mastery's private `(untagged)` sentinel.
const OTHER: &str = "Other";

/// custom_data key marking a card as suspended by the never-learned flow.
const MARKER_KEY: &str = "nl_susp";

impl Collection {
    // ------------------------------------------------------------------
    // Read: NeverLearnedList
    // ------------------------------------------------------------------

    /// Read-only: lists every card tagged `NeverLearned` (further scoped by
    /// `input.search`), grouped by topic at `input.group_depth`. Opens no
    /// transaction, bumps no mtime, records no undo step.
    pub(crate) fn never_learned_list(
        &mut self,
        input: anki_proto::tags::NeverLearnedListRequest,
    ) -> Result<anki_proto::tags::NeverLearnedListResponse> {
        let scope = format!("{} tag:{}", input.search, NEVER_LEARNED_TAG);
        let guard = self.search_cards_into_table(scope.as_str(), SortMode::NoOrder)?;
        guard.col.never_learned_list_data(input.group_depth)
    }

    fn never_learned_list_data(
        &mut self,
        group_depth: u32,
    ) -> Result<anki_proto::tags::NeverLearnedListResponse> {
        let cards = self.storage.all_searched_cards()?;
        let notes = self.searched_note_tags_and_labels()?;

        let mut groups: HashMap<String, Vec<anki_proto::tags::never_learned_list_response::Card>> =
            HashMap::new();
        let mut total_card_ids: HashSet<i64> = HashSet::new();

        for card in &cards {
            let Some((tags, label)) = notes.get(&card.note_id().0) else {
                continue;
            };
            let keys = topic_keys(tags, group_depth);
            let card_entry = anki_proto::tags::never_learned_list_response::Card {
                card_id: card.id().0,
                label: label.clone(),
            };
            total_card_ids.insert(card.id().0);
            if keys.is_empty() {
                groups
                    .entry(OTHER.to_string())
                    .or_default()
                    .push(card_entry);
            } else {
                for key in keys {
                    groups.entry(key).or_default().push(card_entry.clone());
                }
            }
        }

        let mut group_list: Vec<_> = groups
            .into_iter()
            .map(|(tag, mut cards)| {
                cards.sort_by_key(|c| c.card_id);
                anki_proto::tags::never_learned_list_response::Group { tag, cards }
            })
            .collect();
        group_list.sort_by(|a, b| a.tag.cmp(&b.tag));

        Ok(anki_proto::tags::NeverLearnedListResponse {
            groups: group_list,
            total_cards: total_card_ids.len() as u32,
        })
    }

    /// note_id -> (raw tags string, first-field label), for the searched
    /// cards' notes. Label = note first field, split on `\x1f`, index 0, no
    /// strip/truncate.
    fn searched_note_tags_and_labels(&self) -> Result<HashMap<i64, (String, String)>> {
        let mut stmt = self.storage.db.prepare(
            "SELECT id, tags, flds FROM notes WHERE id IN \
             (SELECT nid FROM cards WHERE id IN (SELECT cid FROM search_cids))",
        )?;
        let mut map = HashMap::new();
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let id: i64 = row.get(0)?;
            let tags: String = row.get(1)?;
            let flds: String = row.get(2)?;
            let label = flds.split('\u{1f}').next().unwrap_or_default().to_string();
            map.insert(id, (tags, label));
        }
        Ok(map)
    }

    // ------------------------------------------------------------------
    // Mutation: SetNeverLearned
    // ------------------------------------------------------------------

    /// Marks/unmarks the whole topic (derived from `card_id`'s note tags at
    /// `group_depth`) as never-learned: bulk tag add/remove plus a per-card
    /// suspend/unsuspend with an ownership marker (data-model §4). One
    /// `transact` call wraps the entire bulk in a single undo step.
    pub fn set_never_learned(
        &mut self,
        card_id: CardId,
        group_depth: u32,
        enabled: bool,
    ) -> Result<OpOutput<usize>> {
        self.transact(Op::SetNeverLearned, |col| {
            col.set_never_learned_inner(card_id, group_depth, enabled)
        })
    }

    fn set_never_learned_inner(
        &mut self,
        card_id: CardId,
        group_depth: u32,
        enabled: bool,
    ) -> Result<usize> {
        let target_card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
        let note = self
            .storage
            .get_note(target_card.note_id())?
            .or_not_found(target_card.note_id())?;
        let tags_string = crate::tags::join_tags(&note.tags);
        let keys = topic_keys(&tags_string, group_depth);

        let (cards, nids): (Vec<Card>, Vec<NoteId>) = if keys.is_empty() {
            // No category exists for this card; single-card fallback.
            (vec![target_card.clone()], vec![target_card.note_id()])
        } else {
            let search = keys
                .iter()
                .map(|k| format!("(tag:{k} OR tag:{k}::*)"))
                .collect::<Vec<_>>()
                .join(" OR ");
            let cards = self.all_cards_for_search(search.as_str())?;
            let mut nids: Vec<NoteId> = cards.iter().map(|c| c.note_id()).collect();
            nids.sort_unstable();
            nids.dedup();
            (cards, nids)
        };

        // Suspending requires scheduler v2; reuse the same guard as
        // bury_or_suspend_cards_inner. Unmarking only restores queues that
        // were previously suspended by this flow, which could only have
        // happened under v2 in the first place, so no guard is needed there.
        if enabled && self.scheduler_version() == SchedulerVersion::V1 {
            return Err(AnkiError::SchedulerUpgradeRequired);
        }

        if enabled {
            self.add_tags_to_notes_inner(&nids, NEVER_LEARNED_TAG)?;
        } else {
            self.remove_never_learned_tag_from_notes(&nids)?;
        }

        let usn = self.usn()?;
        let mut count = 0;
        for original in cards {
            let mut card = original.clone();
            let mut changed = false;

            if enabled {
                if card.queue != CardQueue::Suspended {
                    card.queue = CardQueue::Suspended;
                    set_marker(&mut card)?;
                    changed = true;
                    count += 1;
                }
                // Already suspended (user's or ours): tag only, don't touch
                // custom_data or count it.
            } else if card.queue == CardQueue::Suspended {
                if has_marker(&card)? {
                    card.restore_queue_after_bury_or_suspend();
                    clear_marker(&mut card)?;
                    changed = true;
                    count += 1;
                }
                // Suspended without our marker (user's): leave queue as-is;
                // no stale marker possible in this branch.
            } else if has_marker(&card)? {
                // Not suspended but a stale marker is present: clear it,
                // don't count.
                clear_marker(&mut card)?;
                changed = true;
            }

            if changed {
                self.update_card_inner(&mut card, original, usn)?;
            }
        }

        Ok(count)
    }

    /// Removes `NeverLearned` from the given notes. Mirrors
    /// `remove_tags_from_notes_inner` (tags/remove.rs), duplicated locally
    /// since that helper is module-private there.
    fn remove_never_learned_tag_from_notes(&mut self, nids: &[NoteId]) -> Result<usize> {
        let usn = self.usn()?;
        let mut re = TagMatcher::new(NEVER_LEARNED_TAG)?;
        let mut match_count = 0;
        let notes = self.storage.get_note_tags_by_id_list(nids)?;

        for mut note in notes {
            if !re.is_match(&note.tags) {
                continue;
            }
            match_count += 1;
            let original = note.clone();
            note.tags = re.remove(&note.tags);
            note.set_modified(usn);
            self.update_note_tags_undoable(&note, original)?;
        }

        Ok(match_count)
    }
}

/// Topic keys for grouping/derivation: `group_key(tag, group_depth)` for
/// every tag in `tags` that contains `::` and is not (case-insensitively)
/// `NeverLearned`. Deduplicated.
fn topic_keys(tags: &str, group_depth: u32) -> HashSet<String> {
    let mut keys = HashSet::new();
    for tag in split_tags(tags) {
        if tag.contains("::") && !tag.eq_ignore_ascii_case(NEVER_LEARNED_TAG) {
            keys.insert(group_key(tag, group_depth));
        }
    }
    keys
}

/// The first `group_depth` `::` components of `tag` (whole tag if depth is
/// 0). Copied from `crate::stats::tag_mastery::group_key` — duplicated
/// rather than making that module's helper pub, to keep the two features
/// decoupled.
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

/// Parses `card.custom_data` (empty string means `{}`) into a JSON object
/// map, never clobbering unrelated keys.
fn custom_data_map(card: &Card) -> Result<Map<String, Value>> {
    if card.custom_data.trim().is_empty() {
        return Ok(Map::new());
    }
    match serde_json::from_str::<Value>(&card.custom_data) {
        Ok(Value::Object(map)) => Ok(map),
        _ => Ok(Map::new()),
    }
}

fn has_marker(card: &Card) -> Result<bool> {
    Ok(custom_data_map(card)?.contains_key(MARKER_KEY))
}

/// Merges `{"nl_susp":1}` into the card's custom_data.
fn set_marker(card: &mut Card) -> Result<()> {
    let mut map = custom_data_map(card)?;
    map.insert(MARKER_KEY.to_string(), Value::from(1));
    card.custom_data = serde_json::to_string(&Value::Object(map))?;
    Ok(())
}

/// Removes the marker key from the card's custom_data, if present.
fn clear_marker(card: &mut Card) -> Result<()> {
    let mut map = custom_data_map(card)?;
    map.remove(MARKER_KEY);
    card.custom_data = if map.is_empty() {
        String::new()
    } else {
        serde_json::to_string(&Value::Object(map))?
    };
    Ok(())
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::tests::NoteAdder;

    fn add_note_with_tags(col: &mut Collection, front: &str, tags: &[&str]) -> CardId {
        let mut note = NoteAdder::basic(col).fields(&[front, "a"]).note();
        note.tags = tags.iter().map(|t| (*t).to_string()).collect();
        col.add_note(&mut note, DeckId(1)).unwrap();
        col.storage.card_ids_of_notes(&[note.id]).unwrap()[0]
    }

    fn req(group_depth: u32, search: &str) -> anki_proto::tags::NeverLearnedListRequest {
        anki_proto::tags::NeverLearnedListRequest {
            group_depth,
            search: search.to_string(),
        }
    }

    #[test]
    fn empty_result_is_not_an_error() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "a", &["bio::cell"]);
        let resp = col.never_learned_list(req(2, ""))?;
        assert_eq!(resp.groups, vec![]);
        assert_eq!(resp.total_cards, 0);
        Ok(())
    }

    #[test]
    fn topic_grouping_and_other_bucket() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "a", &["bio::cell::wall", NEVER_LEARNED_TAG]);
        add_note_with_tags(&mut col, "b", &["flat", NEVER_LEARNED_TAG]);
        // Not tagged NeverLearned -> excluded entirely.
        add_note_with_tags(&mut col, "c", &["bio::cell::wall"]);

        let resp = col.never_learned_list(req(2, ""))?;
        assert_eq!(resp.total_cards, 2);
        let bio = resp.groups.iter().find(|g| g.tag == "bio::cell").unwrap();
        assert_eq!(bio.cards.len(), 1);
        let other = resp.groups.iter().find(|g| g.tag == OTHER).unwrap();
        assert_eq!(other.cards.len(), 1);
        Ok(())
    }

    #[test]
    fn read_is_read_only() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "a", &["bio::cell", NEVER_LEARNED_TAG]);
        let undo_before = col.can_undo().cloned();
        let last_step_before = col.undo_status().last_step;
        let _ = col.never_learned_list(req(2, ""))?;
        assert_eq!(col.can_undo().cloned(), undo_before);
        assert_eq!(col.undo_status().last_step, last_step_before);
        Ok(())
    }

    #[test]
    fn mark_topic_suspends_all_cards_one_undo_step() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["bio::cell"]);
        let b = add_note_with_tags(&mut col, "b", &["bio::cell::wall"]);
        let undo_before = col.undo_status().last_step;

        let out = col.set_never_learned(a, 2, true)?;
        assert_eq!(out.output, 2);
        assert_ne!(col.undo_status().last_step, undo_before);

        for cid in [a, b] {
            let card = col.storage.get_card(cid)?.unwrap();
            assert_eq!(card.queue, CardQueue::Suspended);
            assert!(has_marker(&card)?);
            let note = col.storage.get_note(card.note_id())?.unwrap();
            assert!(note
                .tags
                .iter()
                .any(|t| t.eq_ignore_ascii_case(NEVER_LEARNED_TAG)));
        }

        col.undo()?;
        for cid in [a, b] {
            let card = col.storage.get_card(cid)?.unwrap();
            assert_ne!(card.queue, CardQueue::Suspended);
        }
        Ok(())
    }

    #[test]
    fn unmark_respects_suspend_ownership() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["bio::cell"]);
        let b = add_note_with_tags(&mut col, "b", &["bio::cell::wall"]);

        // User manually suspends b BEFORE marking the topic.
        {
            let mut card = col.storage.get_card(b)?.unwrap();
            let original = card.clone();
            card.queue = CardQueue::Suspended;
            let usn = col.usn()?;
            col.update_card_inner(&mut card, original, usn)?;
        }

        col.set_never_learned(a, 2, true)?;
        // a got suspended+marked by us; b was already suspended by the user
        // (no marker written for it).
        assert!(has_marker(&col.storage.get_card(a)?.unwrap())?);
        assert!(!has_marker(&col.storage.get_card(b)?.unwrap())?);

        let out = col.set_never_learned(a, 2, false)?;
        // Only a (our marker) gets unsuspended; b stays suspended (user's).
        assert_eq!(out.output, 1);
        assert_ne!(
            col.storage.get_card(a)?.unwrap().queue,
            CardQueue::Suspended
        );
        assert_eq!(
            col.storage.get_card(b)?.unwrap().queue,
            CardQueue::Suspended
        );
        // Tag removed from both notes regardless.
        for cid in [a, b] {
            let card = col.storage.get_card(cid)?.unwrap();
            let note = col.storage.get_note(card.note_id())?.unwrap();
            assert!(!note
                .tags
                .iter()
                .any(|t| t.eq_ignore_ascii_case(NEVER_LEARNED_TAG)));
        }
        Ok(())
    }

    #[test]
    fn no_topic_tag_falls_back_to_single_card() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["flat"]);
        let out = col.set_never_learned(a, 2, true)?;
        assert_eq!(out.output, 1);
        assert_eq!(
            col.storage.get_card(a)?.unwrap().queue,
            CardQueue::Suspended
        );
        Ok(())
    }

    #[test]
    fn not_found_card_errors() -> Result<()> {
        let mut col = Collection::new();
        let err = col.set_never_learned(CardId(123456), 2, true);
        assert!(err.is_err());
        Ok(())
    }

    // ------------------------------------------------------------------
    // Read query gaps
    // ------------------------------------------------------------------

    #[test]
    fn multi_tag_card_fans_out_across_groups() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(
            &mut col,
            "a",
            &["bio::cell", "chem::acid", NEVER_LEARNED_TAG],
        );

        let resp = col.never_learned_list(req(2, ""))?;
        let bio = resp.groups.iter().find(|g| g.tag == "bio::cell").unwrap();
        let chem = resp.groups.iter().find(|g| g.tag == "chem::acid").unwrap();
        assert_eq!(bio.cards.len(), 1);
        assert_eq!(chem.cards.len(), 1);
        assert_eq!(bio.cards[0].card_id, chem.cards[0].card_id);
        Ok(())
    }

    #[test]
    fn dedup_counts_fanned_out_card_once() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(
            &mut col,
            "a",
            &["bio::cell", "chem::acid", NEVER_LEARNED_TAG],
        );

        let resp = col.never_learned_list(req(2, ""))?;
        // Card appears in 2 groups, but total_cards counts it once.
        assert_eq!(resp.total_cards, 1);
        Ok(())
    }

    #[test]
    fn identical_labels_on_different_cards_both_appear() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, "same", &["bio::cell", NEVER_LEARNED_TAG]);
        add_note_with_tags(&mut col, "same", &["bio::cell", NEVER_LEARNED_TAG]);

        let resp = col.never_learned_list(req(2, ""))?;
        assert_eq!(resp.total_cards, 2);
        let bio = resp.groups.iter().find(|g| g.tag == "bio::cell").unwrap();
        // Dedup is by card_id, never by label: both entries present despite
        // identical labels.
        assert_eq!(bio.cards.len(), 2);
        assert_eq!(bio.cards[0].label, "same");
        assert_eq!(bio.cards[1].label, "same");
        assert_ne!(bio.cards[0].card_id, bio.cards[1].card_id);
        Ok(())
    }

    #[test]
    fn label_is_exact_first_field_no_strip_or_truncate() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(
            &mut col,
            "  padded front with spaces  ",
            &["bio::cell", NEVER_LEARNED_TAG],
        );

        let resp = col.never_learned_list(req(2, ""))?;
        let bio = resp.groups.iter().find(|g| g.tag == "bio::cell").unwrap();
        assert_eq!(bio.cards.len(), 1);
        assert_eq!(bio.cards[0].label, "  padded front with spaces  ");
        Ok(())
    }

    // ------------------------------------------------------------------
    // Mutation gaps — full data-model §4 state table
    // ------------------------------------------------------------------

    #[test]
    fn mark_idempotent_remark_counts_zero_and_keeps_marker() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["bio::cell"]);

        let first = col.set_never_learned(a, 2, true)?;
        assert_eq!(first.output, 1);

        // Row "Mark, Suspended (we, re-mark), marker present": re-marking an
        // already-our-suspended card must count 0 and keep queue/marker.
        let second = col.set_never_learned(a, 2, true)?;
        assert_eq!(second.output, 0);

        let card = col.storage.get_card(a)?.unwrap();
        assert_eq!(card.queue, CardQueue::Suspended);
        assert!(has_marker(&card)?);
        Ok(())
    }

    #[test]
    fn mark_user_suspended_card_gets_tag_only_no_marker_not_counted() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["bio::cell"]);

        // User manually suspends BEFORE marking.
        {
            let mut card = col.storage.get_card(a)?.unwrap();
            let original = card.clone();
            card.queue = CardQueue::Suspended;
            let usn = col.usn()?;
            col.update_card_inner(&mut card, original, usn)?;
        }

        let out = col.set_never_learned(a, 2, true)?;
        // Row "Mark, Suspended (user), marker absent": tag only, not
        // counted, marker stays absent.
        assert_eq!(out.output, 0);
        let card = col.storage.get_card(a)?.unwrap();
        assert_eq!(card.queue, CardQueue::Suspended);
        assert!(!has_marker(&card)?);
        let note = col.storage.get_note(card.note_id())?.unwrap();
        assert!(note
            .tags
            .iter()
            .any(|t| t.eq_ignore_ascii_case(NEVER_LEARNED_TAG)));
        Ok(())
    }

    #[test]
    fn unmark_not_suspended_stale_marker_is_cleared_but_not_counted() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["bio::cell", NEVER_LEARNED_TAG]);

        // Card is NOT suspended, but carries a stale marker (manual mutation,
        // same pattern as unmark_respects_suspend_ownership).
        {
            let mut card = col.storage.get_card(a)?.unwrap();
            let original = card.clone();
            set_marker(&mut card)?;
            let usn = col.usn()?;
            col.update_card_inner(&mut card, original, usn)?;
        }
        assert_ne!(
            col.storage.get_card(a)?.unwrap().queue,
            CardQueue::Suspended
        );
        assert!(has_marker(&col.storage.get_card(a)?.unwrap())?);

        let out = col.set_never_learned(a, 2, false)?;
        // Row "Unmark, not Suspended, marker present (stale)": untag + clear
        // stale marker, queue unchanged, not counted.
        assert_eq!(out.output, 0);
        let card = col.storage.get_card(a)?.unwrap();
        assert_ne!(card.queue, CardQueue::Suspended);
        assert!(!has_marker(&card)?);
        let note = col.storage.get_note(card.note_id())?.unwrap();
        assert!(!note
            .tags
            .iter()
            .any(|t| t.eq_ignore_ascii_case(NEVER_LEARNED_TAG)));
        Ok(())
    }

    #[test]
    fn unmark_not_suspended_no_marker_is_a_noop_on_queue() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["bio::cell", NEVER_LEARNED_TAG]);

        // Plain not-suspended, not-marked-by-us card in the topic.
        assert_ne!(
            col.storage.get_card(a)?.unwrap().queue,
            CardQueue::Suspended
        );
        assert!(!has_marker(&col.storage.get_card(a)?.unwrap())?);

        let out = col.set_never_learned(a, 2, false)?;
        // Row "Unmark, not Suspended, marker absent": untag only, no-op on
        // queue/marker, not counted.
        assert_eq!(out.output, 0);
        let card = col.storage.get_card(a)?.unwrap();
        assert_ne!(card.queue, CardQueue::Suspended);
        assert!(!has_marker(&card)?);
        let note = col.storage.get_note(card.note_id())?.unwrap();
        assert!(!note
            .tags
            .iter()
            .any(|t| t.eq_ignore_ascii_case(NEVER_LEARNED_TAG)));
        Ok(())
    }

    #[test]
    fn multi_topic_card_unions_cards_from_both_topics() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        // Note tagged with two qualifying depth-2 topics.
        let shared = add_note_with_tags(&mut col, "shared", &["bio::cell", "chem::acid"]);
        let bio_sibling = add_note_with_tags(&mut col, "bio-sibling", &["bio::cell::wall"]);
        let chem_sibling = add_note_with_tags(&mut col, "chem-sibling", &["chem::acid::base"]);

        // Mark via the card in the shared multi-topic note.
        let out = col.set_never_learned(shared, 2, true)?;
        // 3 cards total transition to suspended: shared + bio_sibling +
        // chem_sibling.
        assert_eq!(out.output, 3);

        for cid in [shared, bio_sibling, chem_sibling] {
            let card = col.storage.get_card(cid)?.unwrap();
            assert_eq!(card.queue, CardQueue::Suspended);
            assert!(has_marker(&card)?);
        }
        Ok(())
    }

    #[test]
    fn coexists_with_marked_tag() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["bio::cell", "marked"]);

        col.set_never_learned(a, 2, true)?;

        let card = col.storage.get_card(a)?.unwrap();
        let note = col.storage.get_note(card.note_id())?.unwrap();
        // Both tags remain: "marked" untouched, NeverLearned added.
        assert!(note.tags.iter().any(|t| t.eq_ignore_ascii_case("marked")));
        assert!(note
            .tags
            .iter()
            .any(|t| t.eq_ignore_ascii_case(NEVER_LEARNED_TAG)));
        Ok(())
    }

    #[test]
    fn undo_redo_round_trip_fully_restores_bulk_mark() -> Result<()> {
        let mut col = Collection::new();
        col.set_scheduler_version_config_key(SchedulerVersion::V2)?;
        let a = add_note_with_tags(&mut col, "a", &["bio::cell"]);
        let b = add_note_with_tags(&mut col, "b", &["bio::cell::wall"]);

        col.set_never_learned(a, 2, true)?;

        // Capture post-mark state.
        let post_mark: Vec<(CardId, CardQueue, bool)> = [a, b]
            .iter()
            .map(|&cid| {
                let card = col.storage.get_card(cid).unwrap().unwrap();
                let marker = has_marker(&card).unwrap();
                (cid, card.queue, marker)
            })
            .collect();
        let post_mark_tags: Vec<bool> = [a, b]
            .iter()
            .map(|&cid| {
                let card = col.storage.get_card(cid).unwrap().unwrap();
                let note = col.storage.get_note(card.note_id()).unwrap().unwrap();
                note.tags
                    .iter()
                    .any(|t| t.eq_ignore_ascii_case(NEVER_LEARNED_TAG))
            })
            .collect();
        assert!(post_mark_tags.iter().all(|&t| t));
        assert!(post_mark
            .iter()
            .all(|(_, q, m)| *q == CardQueue::Suspended && *m));

        col.undo()?;
        for cid in [a, b] {
            let card = col.storage.get_card(cid)?.unwrap();
            assert_ne!(card.queue, CardQueue::Suspended);
            assert!(!has_marker(&card)?);
        }

        col.redo()?;
        // Redo is NOT a no-op: it must fully restore the bulk mark, matching
        // the captured post-mark state exactly.
        for (cid, expected_queue, expected_marker) in post_mark {
            let card = col.storage.get_card(cid)?.unwrap();
            assert_eq!(card.queue, expected_queue);
            assert_eq!(has_marker(&card)?, expected_marker);
        }
        for cid in [a, b] {
            let card = col.storage.get_card(cid)?.unwrap();
            let note = col.storage.get_note(card.note_id())?.unwrap();
            assert!(note
                .tags
                .iter()
                .any(|t| t.eq_ignore_ascii_case(NEVER_LEARNED_TAG)));
        }
        Ok(())
    }
}
