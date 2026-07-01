// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! CFA Speedrun (SPOV 1 platform + SPOV 3): contrast scheduling.
//!
//! Baseline Anki schedules every card independently and only relates cards
//! through sibling-burying. This pass treats the deck's existing tags as an
//! edge graph: cards whose notes share a cluster tag are confusable and are
//! interleaved in small groups so the interference becomes the lesson — without
//! a broad cluster turning into one giant block the learner must slog through.
//!
//! The cluster key comes from `contrast_tag_prefix`:
//! - non-empty (e.g. `cluster::`) → the first tag under that prefix, so you can
//!   curate explicit clusters;
//! - empty (the default) → the note's first content tag, so decks whose tags
//!   are plain reading/topic names (like the imported CFA deck) get contrast
//!   grouping with no retagging.
//!
//! Phase 1 keeps this cheap and safe: it is *pure reordering* of the already
//! gathered new/review piles. No cards are added, held back, or buried, so deck
//! limits and counts are untouched.

use std::collections::HashMap;
use std::collections::VecDeque;

use super::QueueBuilder;
use crate::prelude::*;

/// Bookkeeping tags that are noise rather than content clusters; skipped when
/// clustering by the note's first tag (empty-prefix mode).
const IGNORED_CLUSTER_TAGS: &[&str] = &["marked", "leech", "Category"];

/// Resolved confusable clusters for the gathered cards.
///
/// Maps a note id to its cluster key (the matched tag) for every gathered note
/// that carries a tag under the configured prefix. Notes without such a tag are
/// absent and are treated as singletons (left in place) by the contrast pass.
#[derive(Debug)]
pub(super) struct ContrastClusters {
    by_note: HashMap<NoteId, String>,
}

impl ContrastClusters {
    fn cluster_of(&self, note_id: NoteId) -> Option<String> {
        self.by_note.get(&note_id).cloned()
    }
}

impl QueueBuilder {
    /// When contrast scheduling is enabled, batch-load the tags of all gathered
    /// notes in a single query and record which confusable cluster each belongs
    /// to. Stored for use by [`QueueBuilder::apply_contrast`] during build().
    pub(super) fn load_contrast_clusters(&mut self, col: &mut Collection) -> Result<()> {
        if !self.context.sort_options.contrast_scheduling {
            return Ok(());
        }
        let prefix = self.contrast_prefix();

        let mut note_ids: Vec<NoteId> = self
            .new
            .iter()
            .map(|card| card.note_id)
            .chain(self.review.iter().map(|card| card.note_id))
            .collect();
        note_ids.sort_unstable();
        note_ids.dedup();
        if note_ids.is_empty() {
            return Ok(());
        }

        let by_note = col
            .storage
            .note_tags_by_id(&note_ids)?
            .into_iter()
            .filter_map(|(note_id, tags)| {
                cluster_for_tags(&tags, &prefix).map(|cluster| (note_id, cluster))
            })
            .collect();
        self.contrast = Some(ContrastClusters { by_note });
        Ok(())
    }

    /// Reorder the gathered new and review piles so cards in the same
    /// confusable cluster are surfaced together — interleaved in small
    /// groups (at most `CONTRAST_CHUNK` in a row) rather than one big
    /// block, so a broad cluster doesn't force the learner through hundreds
    /// of same-tag cards at once. No cards are added or removed, so deck
    /// limits and counts are unaffected.
    pub(super) fn apply_contrast(&mut self) {
        let Some(contrast) = self.contrast.take() else {
            return;
        };
        let new = std::mem::take(&mut self.new);
        self.new = interleave_clusters(new, |card| contrast.cluster_of(card.note_id));
        let review = std::mem::take(&mut self.review);
        self.review = interleave_clusters(review, |card| contrast.cluster_of(card.note_id));
    }

    fn contrast_prefix(&self) -> String {
        self.context
            .sort_options
            .contrast_tag_prefix
            .trim()
            .to_string()
    }
}

/// Return the cluster key for a note's space-separated tag string.
///
/// - With a non-empty `prefix`, the key is the first tag (in stored order) that
///   begins with `prefix`; the bare prefix with nothing after it is ignored.
/// - With an empty `prefix`, the key is the note's first content tag (skipping
///   bookkeeping tags like `marked`/`leech`), so decks whose tags are plain
///   reading/topic names still cluster.
///
/// Returns None when the note has no eligible tag.
fn cluster_for_tags(tags: &str, prefix: &str) -> Option<String> {
    if prefix.is_empty() {
        tags.split_whitespace()
            .find(|tag| !IGNORED_CLUSTER_TAGS.contains(tag))
            .map(ToString::to_string)
    } else {
        tags.split_whitespace()
            .find(|tag| tag.len() > prefix.len() && tag.starts_with(prefix))
            .map(ToString::to_string)
    }
}

/// Maximum number of same-cluster cards shown in a row before rotating to the
/// next cluster. Small enough to keep confusables adjacent for contrast, but it
/// stops a broad cluster (e.g. a whole reading) from becoming one long block.
const CONTRAST_CHUNK: usize = 4;

/// Interleave clusters: items sharing a cluster key are emitted in runs of at
/// most [`CONTRAST_CHUNK`], round-robin across clusters in first-appearance
/// order, so confusable cards stay near each other without one broad cluster
/// forming a single huge block. Items without a key (None) are treated as
/// singleton clusters and keep their relative order. Nothing is added or
/// removed.
fn interleave_clusters<T, F>(items: Vec<T>, key_of: F) -> Vec<T>
where
    F: Fn(&T) -> Option<String>,
{
    let count = items.len();
    let mut groups: Vec<VecDeque<T>> = Vec::new();
    let mut index_of_cluster: HashMap<String, usize> = HashMap::new();
    for item in items {
        match key_of(&item) {
            Some(key) => {
                if let Some(&idx) = index_of_cluster.get(&key) {
                    groups[idx].push_back(item);
                } else {
                    index_of_cluster.insert(key, groups.len());
                    groups.push(VecDeque::from([item]));
                }
            }
            None => groups.push(VecDeque::from([item])),
        }
    }

    let mut out = Vec::with_capacity(count);
    let mut any = true;
    while any {
        any = false;
        for group in groups.iter_mut() {
            for _ in 0..CONTRAST_CHUNK {
                match group.pop_front() {
                    Some(item) => out.push(item),
                    None => break,
                }
            }
            if !group.is_empty() {
                any = true;
            }
        }
    }
    out
}

#[cfg(test)]
mod test {
    use super::*;

    fn keys(items: &[(i64, Option<&str>)]) -> Vec<i64> {
        interleave_clusters(items.to_vec(), |(_, key)| key.map(ToString::to_string))
            .into_iter()
            .map(|(id, _)| id)
            .collect()
    }

    #[test]
    fn groups_clusters_by_first_appearance() {
        // a/b interleaved; unclustered items keep their slots.
        let items = vec![
            (1, Some("a")),
            (2, None),
            (3, Some("b")),
            (4, Some("a")),
            (5, Some("b")),
            (6, None),
        ];
        assert_eq!(keys(&items), vec![1, 4, 2, 3, 5, 6]);
    }

    #[test]
    fn order_preserved_when_no_clusters() {
        let items = vec![(1, None), (2, None), (3, None)];
        assert_eq!(keys(&items), vec![1, 2, 3]);
    }

    #[test]
    fn big_clusters_are_chunked_not_one_block() {
        // two clusters larger than CONTRAST_CHUNK (4) must interleave in chunks,
        // never one giant run of the same cluster.
        let mut items: Vec<(i64, Option<&str>)> = Vec::new();
        for i in 0i64..6 {
            items.push((i, Some("a")));
        }
        for i in 6i64..12 {
            items.push((i, Some("b")));
        }
        assert_eq!(keys(&items), vec![0, 1, 2, 3, 6, 7, 8, 9, 4, 5, 10, 11]);
    }

    #[test]
    fn cluster_prefix_matching() {
        assert_eq!(
            cluster_for_tags(" cfa::topic::fi cluster::fi::duration ", "cluster::"),
            Some("cluster::fi::duration".to_string())
        );
        // bare prefix with nothing after it is ignored
        assert_eq!(cluster_for_tags(" cluster:: ", "cluster::"), None);
        assert_eq!(cluster_for_tags(" marked leech ", "cluster::"), None);
    }

    #[test]
    fn cluster_by_first_tag_when_no_prefix() {
        // flat reading tags (no cluster:: prefix): cluster by the first tag
        assert_eq!(
            cluster_for_tags("Inventories Long-Lived_Assets", ""),
            Some("Inventories".to_string())
        );
        // bookkeeping tags are skipped so the reading wins
        assert_eq!(
            cluster_for_tags("marked Inventories", ""),
            Some("Inventories".to_string())
        );
        // only bookkeeping / no tags -> no cluster
        assert_eq!(cluster_for_tags("marked leech Category", ""), None);
        assert_eq!(cluster_for_tags("", ""), None);
    }
}
