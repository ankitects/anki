// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod browser_table;
mod search_node;

use std::{convert::TryInto, str::FromStr, sync::Arc};

use super::{notes::to_note_ids, Backend};
pub(super) use crate::backend_proto::search_service::Service as SearchService;
use crate::{
    backend_proto as pb,
    backend_proto::sort_order::Value as SortOrderProto,
    browser_table::Column,
    prelude::*,
    search::{concatenate_searches, replace_search_node, write_nodes, Node, SortMode},
};

impl SearchService for Backend {
    fn build_search_string(&self, input: pb::SearchNode) -> Result<pb::String> {
        let node: Node = input.try_into()?;
        Ok(write_nodes(&node.into_node_list()).into())
    }

    fn search_cards(&self, input: pb::SearchRequest) -> Result<pb::SearchResponse> {
        self.with_col(|col| {
            let order = input.order.unwrap_or_default().value.into();
            let cids = col.search_cards(&input.search, order)?;
            Ok(pb::SearchResponse {
                ids: cids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn search_notes(&self, input: pb::SearchRequest) -> Result<pb::SearchResponse> {
        self.with_col(|col| {
            let order = input.order.unwrap_or_default().value.into();
            let nids = col.search_notes(&input.search, order)?;
            Ok(pb::SearchResponse {
                ids: nids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn join_search_nodes(&self, input: pb::JoinSearchNodesRequest) -> Result<pb::String> {
        let sep = input.joiner().into();
        let existing_nodes = {
            let node: Node = input.existing_node.unwrap_or_default().try_into()?;
            node.into_node_list()
        };
        let additional_node = input.additional_node.unwrap_or_default().try_into()?;
        Ok(concatenate_searches(sep, existing_nodes, additional_node).into())
    }

    fn replace_search_node(&self, input: pb::ReplaceSearchNodeRequest) -> Result<pb::String> {
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

    fn find_and_replace(&self, input: pb::FindAndReplaceRequest) -> Result<pb::OpChangesWithCount> {
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
        self.with_col(|col| {
            if nids.is_empty() {
                nids = col.search_notes_unordered("")?
            };
            col.find_and_replace(nids, &search, &repl, field_name)
                .map(Into::into)
        })
    }

    fn all_browser_columns(&self, _input: pb::Empty) -> Result<pb::BrowserColumns> {
        self.with_col(|col| Ok(col.all_browser_columns()))
    }

    fn set_active_browser_columns(&self, input: pb::StringList) -> Result<pb::Empty> {
        self.with_col(|col| {
            col.state.active_browser_columns = Some(Arc::new(input.into()));
            Ok(())
        })
        .map(Into::into)
    }

    fn browser_row_for_id(&self, input: pb::Int64) -> Result<pb::BrowserRow> {
        self.with_col(|col| col.browser_row_for_id(input.val).map(Into::into))
    }
}

impl From<Option<SortOrderProto>> for SortMode {
    fn from(order: Option<SortOrderProto>) -> Self {
        use pb::sort_order::Value as V;
        match order.unwrap_or(V::None(pb::Empty {})) {
            V::None(_) => SortMode::NoOrder,
            V::Custom(s) => SortMode::Custom(s),
            V::Builtin(b) => SortMode::Builtin {
                column: Column::from_str(&b.column).unwrap_or_default(),
                reverse: b.reverse,
            },
        }
    }
}
