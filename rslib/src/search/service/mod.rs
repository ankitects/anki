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
            search = format!("(?i){}", search);
        }
        let mut nids = to_note_ids(input.nids);
        let field_name = if input.field_name.is_empty() {
            None
        } else {
            Some(input.field_name)
        };
        let repl = input.replacement;

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
