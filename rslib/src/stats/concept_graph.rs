// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! CFA Speedrun: concept-graph view of a deck.
//!
//! Groups the searched cards into clusters by tag (one cluster per "reading"),
//! reports each cluster's mean FSRS retrievability, and connects clusters that
//! co-occur on the same note (a note tagged with two readings). Read-only; it
//! backs the knowledge-map page. Like `topic_mastery`, it runs in a single SQL
//! pass so it stays fast on large collections.

use std::collections::HashMap;

use anki_proto::stats::concept_graph_response::Edge;
use anki_proto::stats::concept_graph_response::Node;
use anki_proto::stats::ConceptGraphRequest;
use anki_proto::stats::ConceptGraphResponse;

use crate::prelude::*;
use crate::search::SearchBuilder;
use crate::search::SearchNode;
use crate::search::SortMode;

/// Tags that are bookkeeping noise rather than content clusters, dropped before
/// building the graph.
const IGNORED_TAGS: &[&str] = &["Category", "marked", "leech"];

#[derive(Default)]
struct ClusterAcc {
    card_count: u32,
    with_memory_state: u32,
    retrievability_sum: f64,
    reviewed: u32,
}

impl Collection {
    pub(crate) fn concept_graph(
        &mut self,
        req: ConceptGraphRequest,
    ) -> Result<ConceptGraphResponse> {
        // A deck id (with its children) takes precedence over the free-text
        // search, so the per-deck "Concept map" entry point scopes cleanly.
        let guard = if req.deck_id != 0 {
            let search = SearchBuilder::from(SearchNode::from_deck_id(DeckId(req.deck_id), true));
            self.search_cards_into_table(search, SortMode::NoOrder)?
        } else {
            self.search_cards_into_table(&req.search, SortMode::NoOrder)?
        };
        let timing = guard.col.timing_today()?;
        let rows = guard.col.storage.searched_cards_graph_data(timing)?;

        let considered = rows.len() as u32;
        let mut clusters: HashMap<String, ClusterAcc> = HashMap::new();
        // Unordered cluster pairs (label_a < label_b) → co-occurring note count.
        let mut pairs: HashMap<(String, String), u32> = HashMap::new();

        for (tags, retrievability, ctype) in rows {
            let mut card_tags: Vec<&str> = tags
                .split_whitespace()
                .filter(|t| !IGNORED_TAGS.contains(t))
                .collect();
            card_tags.sort_unstable();
            card_tags.dedup();

            // Review (2) or Relearn (3) means the card has graduated from
            // initial learning.
            let graduated = ctype == 2 || ctype == 3;
            for &tag in &card_tags {
                let acc = clusters.entry(tag.to_string()).or_default();
                acc.card_count += 1;
                if let Some(r) = retrievability {
                    acc.with_memory_state += 1;
                    acc.retrievability_sum += r as f64;
                }
                if graduated {
                    acc.reviewed += 1;
                }
            }
            // co-occurrence edge for every distinct pair of this card's tags
            for (i, &a) in card_tags.iter().enumerate() {
                for &b in &card_tags[i + 1..] {
                    *pairs.entry((a.to_string(), b.to_string())).or_default() += 1;
                }
            }
        }

        // Assign stable ids in sorted label order for deterministic output.
        let mut labels: Vec<String> = clusters.keys().cloned().collect();
        labels.sort();
        let id_of: HashMap<&str, u32> = labels
            .iter()
            .enumerate()
            .map(|(i, l)| (l.as_str(), i as u32))
            .collect();

        let nodes: Vec<Node> = labels
            .iter()
            .map(|label| {
                let acc = &clusters[label];
                Node {
                    id: id_of[label.as_str()],
                    label: label.clone(),
                    card_count: acc.card_count,
                    with_memory_state: acc.with_memory_state,
                    average_retrievability: if acc.with_memory_state > 0 {
                        (acc.retrievability_sum / acc.with_memory_state as f64) as f32
                    } else {
                        0.0
                    },
                    reviewed_count: acc.reviewed,
                }
            })
            .collect();

        let mut edges: Vec<Edge> = pairs
            .into_iter()
            .map(|((a, b), weight)| Edge {
                source: id_of[a.as_str()],
                target: id_of[b.as_str()],
                weight,
                kind: "co_occurrence".to_string(),
            })
            .collect();
        edges.sort_by_key(|e| (e.source, e.target));

        Ok(ConceptGraphResponse {
            nodes,
            edges,
            considered,
        })
    }
}

#[cfg(test)]
mod test {
    use anki_proto::stats::ConceptGraphRequest;

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
    fn clusters_by_tag_with_cooccurrence_edges() -> Result<()> {
        let mut col = Collection::new();
        add_note(&mut col, "1", &["Duration"]);
        add_note(&mut col, "2", &["Duration"]);
        add_note(&mut col, "3", &["Inventories"]);
        // a two-reading note bridges the two clusters
        add_note(&mut col, "4", &["Duration", "Inventories"]);
        // noise tag is dropped
        add_note(&mut col, "5", &["Category"]);

        let res = col.concept_graph(ConceptGraphRequest::default())?;

        assert_eq!(res.considered, 5);
        // Duration + Inventories clusters; Category dropped → 2 nodes
        assert_eq!(res.nodes.len(), 2);
        // ids assigned in sorted label order
        assert_eq!(res.nodes[0].label, "Duration");
        assert_eq!(res.nodes[0].id, 0);
        assert_eq!(res.nodes[0].card_count, 3); // notes 1, 2, 4
        assert_eq!(res.nodes[1].label, "Inventories");
        assert_eq!(res.nodes[1].card_count, 2); // notes 3, 4
                                                // one co-occurrence edge Duration<->Inventories, weight 1 (note 4)
        assert_eq!(res.edges.len(), 1);
        assert_eq!(res.edges[0].source, 0);
        assert_eq!(res.edges[0].target, 1);
        assert_eq!(res.edges[0].weight, 1);
        assert_eq!(res.edges[0].kind, "co_occurrence");
        // fresh new cards have no FSRS memory state and haven't graduated
        assert_eq!(res.nodes[0].with_memory_state, 0);
        assert_eq!(res.nodes[0].reviewed_count, 0);
        Ok(())
    }
}
