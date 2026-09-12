// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod browser_table;
mod search_node;

use std::str::FromStr;
use std::sync::Arc;

use anki_proto::generic;
use anki_proto::search::sort_order::Value as SortOrderProto;

use crate::browser_table::Column;
use crate::notes::service::to_note_ids;
use crate::prelude::*;
use crate::search::replace_search_node;
use crate::search::service::browser_table::string_list_to_browser_columns;
use crate::search::JoinSearches;
use crate::search::Node;
use crate::search::SortMode;

impl crate::services::SearchService for Collection {
    fn build_search_string(
        &mut self,
        input: anki_proto::search::SearchNode,
    ) -> Result<generic::String> {
        let node: Node = input.try_into()?;
        Ok(SearchBuilder::from_root(node).write().into())
    }

    fn search_cards(
        &mut self,
        input: anki_proto::search::SearchRequest,
    ) -> Result<anki_proto::search::SearchResponse> {
        let order = input.order.unwrap_or_default().value.into();
        let cids = self.search_cards(&input.search, order)?;
        Ok(anki_proto::search::SearchResponse {
            ids: cids.into_iter().map(|v| v.0).collect(),
        })
    }

    fn search_notes(
        &mut self,
        input: anki_proto::search::SearchRequest,
    ) -> Result<anki_proto::search::SearchResponse> {
        let order = input.order.unwrap_or_default().value.into();
        let nids = self.search_notes(&input.search, order)?;
        Ok(anki_proto::search::SearchResponse {
            ids: nids.into_iter().map(|v| v.0).collect(),
        })
    }

    fn join_search_nodes(
        &mut self,
        input: anki_proto::search::JoinSearchNodesRequest,
    ) -> Result<generic::String> {
        let existing_node: Node = input.existing_node.unwrap_or_default().try_into()?;
        let additional_node: Node = input.additional_node.unwrap_or_default().try_into()?;

        Ok(
            match anki_proto::search::search_node::group::Joiner::try_from(input.joiner)
                .unwrap_or_default()
            {
                anki_proto::search::search_node::group::Joiner::And => {
                    existing_node.and_flat(additional_node)
                }
                anki_proto::search::search_node::group::Joiner::Or => {
                    existing_node.or_flat(additional_node)
                }
            }
            .write()
            .into(),
        )
    }

    fn replace_search_node(
        &mut self,
        input: anki_proto::search::ReplaceSearchNodeRequest,
    ) -> Result<generic::String> {
        let existing = {
            let node = input.existing_node.unwrap_or_default().try_into()?;
            if let Node::Group(nodes) = node {
                nodes
            } else {
                vec![node]
            }
        };
        let replacement = input.replacement_node.unwrap_or_default().try_into()?;
        Ok(replace_search_node(existing, replacement).into())
    }

    fn find_and_replace(
        &mut self,
        input: anki_proto::search::FindAndReplaceRequest,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        let mut search = if input.regex {
            input.search
        } else {
            regex::escape(&input.search)
        };
        if !input.match_case {
            search = format!("(?i){search}");
        }
        let mut nids = to_note_ids(input.nids);
        let field_name = if input.field_name.is_empty() {
            None
        } else {
            Some(input.field_name)
        };
        let repl = if input.regex {
            input.replacement
        } else {
            // In a non-regex replacement, `$` is a literal character; escape it
            // so the regex engine doesn't treat e.g. `$1`/`$name` as a capture
            // group reference (which would drop or corrupt the text).
            input.replacement.replace('$', "$$")
        };

        if nids.is_empty() {
            nids = self.search_notes_unordered("")?
        };
        self.find_and_replace(nids, &search, &repl, field_name)
            .map(Into::into)
    }

    fn all_browser_columns(&mut self) -> Result<anki_proto::search::BrowserColumns> {
        Ok(Collection::all_browser_columns(self))
    }

    fn set_active_browser_columns(&mut self, input: generic::StringList) -> Result<()> {
        self.state.active_browser_columns = Some(Arc::new(string_list_to_browser_columns(input)));
        Ok(())
    }

    fn browser_row_for_id(
        &mut self,
        input: generic::Int64,
    ) -> Result<anki_proto::search::BrowserRow> {
        self.browser_row_for_id(input.val)
    }
}

impl From<Option<SortOrderProto>> for SortMode {
    fn from(order: Option<SortOrderProto>) -> Self {
        use anki_proto::search::sort_order::Value as V;
        match order.unwrap_or(V::None(generic::Empty {})) {
            V::None(_) => SortMode::NoOrder,
            V::Custom(s) => SortMode::Custom(s),
            V::Builtin(b) => SortMode::Builtin {
                column: Column::from_str(&b.column).unwrap_or_default(),
                reverse: b.reverse,
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use anki_proto::search::search_node::Filter;
    use anki_proto::search::SearchNode as ProtoSearchNode;

    use crate::browser_table::Column;
    use crate::collection::Collection;
    use crate::error::AnkiError;
    use crate::search::service::SortOrderProto;
    use crate::search::SortMode;
    use crate::services::SearchService;
    use crate::tests::DeckAdder;
    use crate::tests::NoteAdder;

    #[test]
    fn build_search_string_converts_tag_node_to_anki_syntax() {
        let mut col = Collection::new();
        let proto = ProtoSearchNode {
            filter: Some(Filter::Tag("mytag".to_string())),
        };
        let result = col.build_search_string(proto).unwrap();
        assert_eq!(result.val, "tag:mytag");
    }

    #[test]
    fn build_search_string_returns_invalid_input_for_empty_group() {
        let mut col = Collection::new();
        let proto = ProtoSearchNode {
            filter: Some(Filter::Group(anki_proto::search::search_node::Group {
                joiner: anki_proto::search::search_node::group::Joiner::And as i32,
                nodes: vec![],
            })),
        };

        let err = col.build_search_string(proto).unwrap_err();

        match err {
            AnkiError::InvalidInput { source } => assert_eq!(source.message(), "empty group"),
            other => panic!("expected InvalidInput, got {other:?}"),
        }
    }

    // --- Service methods requiring a DB ---

    #[test]
    fn search_cards_returns_card_ids() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);
        let input = anki_proto::search::SearchRequest {
            search: "".to_string(),
            order: None,
        };
        let result = SearchService::search_cards(&mut col, input).unwrap();
        assert!(!result.ids.is_empty(), "expected at least one card id");
    }

    #[test]
    fn search_notes_returns_note_ids() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);
        let input = anki_proto::search::SearchRequest {
            search: "".to_string(),
            order: None,
        };
        let result = SearchService::search_notes(&mut col, input).unwrap();
        assert!(!result.ids.is_empty(), "expected at least one note id");
    }

    #[test]
    fn find_and_replace_returns_changed_count() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col)
            .fields(&["hello world", "back"])
            .add(&mut col);
        let input = anki_proto::search::FindAndReplaceRequest {
            nids: vec![],
            search: "hello".to_string(),
            replacement: "hi".to_string(),
            regex: false,
            match_case: false,
            field_name: "".to_string(),
        };
        let result = SearchService::find_and_replace(&mut col, input).unwrap();
        assert_eq!(result.count, 1, "expected one note changed");
    }

    #[test]
    fn literal_replacement_with_dollar_is_inserted_verbatim() {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col)
            .fields(&["price", "back"])
            .add(&mut col);
        let input = anki_proto::search::FindAndReplaceRequest {
            nids: vec![note.id.0],
            search: "price".to_string(),
            // `$5` must be inserted literally, not expanded as capture group 5
            // (which would otherwise replace the match with an empty string).
            replacement: "$5".to_string(),
            regex: false,
            match_case: false,
            field_name: "".to_string(),
        };
        let result = SearchService::find_and_replace(&mut col, input).unwrap();
        assert_eq!(result.count, 1);
        let note = col.storage.get_note(note.id).unwrap().unwrap();
        assert_eq!(note.fields()[0].as_str(), "$5");
    }

    #[test]
    fn all_browser_columns_returns_non_empty_list() {
        let mut col = Collection::new();
        let result = SearchService::all_browser_columns(&mut col).unwrap();
        assert!(!result.columns.is_empty(), "expected browser columns");
    }

    #[test]
    fn set_active_browser_columns_persists_in_state() {
        let mut col = Collection::new();
        let input = anki_proto::generic::StringList {
            vals: vec!["noteFld".to_string(), "deck".to_string()],
        };
        SearchService::set_active_browser_columns(&mut col, input).unwrap();
        assert!(
            col.state.active_browser_columns.is_some(),
            "active_browser_columns should be set after call"
        );
    }

    #[test]
    fn browser_row_for_id_returns_requested_columns_for_existing_card() {
        let mut col = Collection::new();
        let deck = DeckAdder::new("TargetDeck").add(&mut col);
        let note = NoteAdder::basic(&mut col)
            .fields(&["front value", "back value"])
            .deck(deck.id)
            .add(&mut col);
        SearchService::set_active_browser_columns(
            &mut col,
            anki_proto::generic::StringList {
                vals: vec![
                    "noteFld".to_string(),
                    "deck".to_string(),
                    "note".to_string(),
                ],
            },
        )
        .unwrap();
        let card_id = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];
        let result = SearchService::browser_row_for_id(
            &mut col,
            anki_proto::generic::Int64 { val: card_id.0 },
        )
        .unwrap();

        let cell_texts: Vec<_> = result.cells.iter().map(|cell| cell.text.as_str()).collect();
        assert_eq!(
            cell_texts,
            ["front value", "TargetDeck", "Basic"],
            "browser row should match the requested column order"
        );
    }

    // --- Exact-match search integration tests ---

    #[test]
    fn empty_search_returns_all_cards_in_collection() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col).fields(&["b", ""]).add(&mut col);
        NoteAdder::cloze(&mut col)
            .fields(&["{{c1::x}}", ""])
            .add(&mut col);

        let total_cards = col.storage.get_all_cards().len();
        let result = SearchService::search_cards(
            &mut col,
            anki_proto::search::SearchRequest {
                search: "".to_string(),
                order: None,
            },
        )
        .unwrap();

        assert_eq!(
            result.ids.len(),
            total_cards,
            "empty search should return all {total_cards} cards"
        );
    }

    #[test]
    fn search_cards_by_notetype_returns_only_cards_of_that_type() {
        let mut col = Collection::new();
        // Basic note → 1 card
        let basic_note = NoteAdder::basic(&mut col).add(&mut col);
        // Cloze note → 1 card, different notetype — should NOT appear
        NoteAdder::cloze(&mut col)
            .fields(&["{{c1::hello}}", ""])
            .add(&mut col);

        let result = SearchService::search_cards(
            &mut col,
            anki_proto::search::SearchRequest {
                search: "note:Basic".to_string(),
                order: None,
            },
        )
        .unwrap();

        assert_eq!(
            result.ids.len(),
            1,
            "expected exactly one card for note:Basic"
        );
        let expected_cids = col.storage.card_ids_of_notes(&[basic_note.id]).unwrap();
        assert_eq!(
            result.ids[0], expected_cids[0].0,
            "card id should match the Basic note"
        );
    }

    #[test]
    fn search_cards_by_deck_returns_only_cards_in_that_deck() {
        let mut col = Collection::new();
        // create a named deck
        let deck = DeckAdder::new("TargetDeck").add(&mut col);
        // card in TargetDeck
        let in_deck = NoteAdder::basic(&mut col).deck(deck.id).add(&mut col);
        // card in default deck (id 1) — should NOT appear
        NoteAdder::basic(&mut col)
            .fields(&["other", ""])
            .add(&mut col);

        let result = SearchService::search_cards(
            &mut col,
            anki_proto::search::SearchRequest {
                search: "deck:TargetDeck".to_string(),
                order: None,
            },
        )
        .unwrap();

        assert_eq!(
            result.ids.len(),
            1,
            "expected exactly one card in TargetDeck"
        );
        let expected_cids = col.storage.card_ids_of_notes(&[in_deck.id]).unwrap();
        assert_eq!(
            result.ids[0], expected_cids[0].0,
            "card id should match the note in TargetDeck"
        );
    }

    #[test]
    fn search_cards_by_tag_returns_only_matching_cards() {
        let mut col = Collection::new();
        // note with tag "target" → 1 card
        let mut tagged = NoteAdder::basic(&mut col).note();
        tagged.tags = vec!["target".to_string()];
        col.add_note(&mut tagged, crate::prelude::DeckId(1))
            .unwrap();
        // untagged note → 1 card, should NOT appear
        NoteAdder::basic(&mut col)
            .fields(&["other", ""])
            .add(&mut col);

        let result = SearchService::search_cards(
            &mut col,
            anki_proto::search::SearchRequest {
                search: "tag:target".to_string(),
                order: None,
            },
        )
        .unwrap();

        assert_eq!(
            result.ids.len(),
            1,
            "expected exactly one card for tag:target"
        );
        let expected_cids = col.storage.card_ids_of_notes(&[tagged.id]).unwrap();
        assert_eq!(
            result.ids[0], expected_cids[0].0,
            "card id should match the tagged note"
        );
    }

    // --- From<Option<SortOrderProto>> for SortMode ---

    #[test]
    fn sort_order_none_option_produces_no_order() {
        // outer Option::None → unwrap_or picks V::None → NoOrder
        let mode = SortMode::from(None);
        assert!(matches!(mode, SortMode::NoOrder));
    }

    #[test]
    fn sort_order_v_none_produces_no_order() {
        // explicit V::None variant → NoOrder
        let mode = SortMode::from(Some(SortOrderProto::None(anki_proto::generic::Empty {})));
        assert!(matches!(mode, SortMode::NoOrder));
    }

    #[test]
    fn sort_order_custom_preserves_string() {
        let mode = SortMode::from(Some(SortOrderProto::Custom("due desc".to_string())));
        assert!(
            matches!(mode, SortMode::Custom(ref s) if s == "due desc"),
            "expected Custom(\"due desc\")"
        );
    }

    #[test]
    fn sort_order_builtin_maps_column_and_reverse() {
        use anki_proto::search::sort_order::Builtin;
        let mode = SortMode::from(Some(SortOrderProto::Builtin(Builtin {
            column: "noteFld".to_string(),
            reverse: true,
        })));
        match mode {
            SortMode::Builtin { column, reverse } => {
                assert_eq!(column, Column::SortField);
                assert!(reverse, "reverse flag should be true");
            }
            other => panic!("expected Builtin, got {other:?}"),
        }
    }

    #[test]
    fn replace_search_node_replaces_single_node() {
        // existing is a single Tag node (not a Group) → wraps in vec![node] before
        // replacing replacement is also a Tag → the old tag is swapped for the
        // new one
        let mut col = Collection::new();
        let input = anki_proto::search::ReplaceSearchNodeRequest {
            existing_node: Some(ProtoSearchNode {
                filter: Some(Filter::Tag("old".to_string())),
            }),
            replacement_node: Some(ProtoSearchNode {
                filter: Some(Filter::Tag("new".to_string())),
            }),
        };
        let result = col.replace_search_node(input).unwrap();
        assert_eq!(result.val, "tag:new");
    }

    #[test]
    fn replace_search_node_replaces_inside_group() {
        // existing is a Group (tag:a tag:b) → unwraps nodes before replacing
        // only the Tag node matching the replacement type is swapped
        let mut col = Collection::new();
        let group = ProtoSearchNode {
            filter: Some(Filter::Group(anki_proto::search::search_node::Group {
                joiner: anki_proto::search::search_node::group::Joiner::And as i32,
                nodes: vec![
                    ProtoSearchNode {
                        filter: Some(Filter::Tag("a".to_string())),
                    },
                    ProtoSearchNode {
                        filter: Some(Filter::Deck("myDeck".to_string())),
                    },
                ],
            })),
        };
        let input = anki_proto::search::ReplaceSearchNodeRequest {
            existing_node: Some(group),
            replacement_node: Some(ProtoSearchNode {
                filter: Some(Filter::Tag("replaced".to_string())),
            }),
        };
        let result = col.replace_search_node(input).unwrap();
        // Tag replaced, Deck unchanged
        assert!(
            result.val.contains("tag:replaced"),
            "tag should be replaced"
        );
        assert!(result.val.contains("deck:myDeck"), "deck should remain");
    }

    #[test]
    fn join_search_nodes_with_and_joiner() {
        use anki_proto::search::search_node::group::Joiner;
        let mut col = Collection::new();
        let input = anki_proto::search::JoinSearchNodesRequest {
            joiner: Joiner::And as i32,
            existing_node: Some(ProtoSearchNode {
                filter: Some(Filter::Tag("a".to_string())),
            }),
            additional_node: Some(ProtoSearchNode {
                filter: Some(Filter::Tag("b".to_string())),
            }),
        };
        let result = col.join_search_nodes(input).unwrap();
        assert_eq!(result.val, "tag:a tag:b");
    }

    #[test]
    fn join_search_nodes_with_or_joiner() {
        use anki_proto::search::search_node::group::Joiner;
        let mut col = Collection::new();
        let input = anki_proto::search::JoinSearchNodesRequest {
            joiner: Joiner::Or as i32,
            existing_node: Some(ProtoSearchNode {
                filter: Some(Filter::Tag("a".to_string())),
            }),
            additional_node: Some(ProtoSearchNode {
                filter: Some(Filter::Tag("b".to_string())),
            }),
        };
        let result = col.join_search_nodes(input).unwrap();
        assert_eq!(result.val, "tag:a OR tag:b");
    }
}
