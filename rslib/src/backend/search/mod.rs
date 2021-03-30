// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod browser_table;
mod search_node;

use std::convert::TryInto;

use super::Backend;
use crate::{
    backend_proto as pb,
    backend_proto::{
        sort_order::builtin::Kind as SortKindProto, sort_order::Value as SortOrderProto,
    },
    config::SortKind,
    prelude::*,
    search::{concatenate_searches, replace_search_node, write_nodes, Node, SortMode},
};
pub(super) use pb::search_service::Service as SearchService;

impl SearchService for Backend {
    fn build_search_string(&self, input: pb::SearchNode) -> Result<pb::String> {
        let node: Node = input.try_into()?;
        Ok(write_nodes(&node.into_node_list()).into())
    }

    fn search_cards(&self, input: pb::SearchIn) -> Result<pb::SearchOut> {
        self.with_col(|col| {
            let order = input.order.unwrap_or_default().value.into();
            let cids = col.search::<CardId>(&input.search, order)?;
            Ok(pb::SearchOut {
                ids: cids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn search_notes(&self, input: pb::SearchIn) -> Result<pb::SearchOut> {
        self.with_col(|col| {
            let order = input.order.unwrap_or_default().value.into();
            let nids = col.search::<NoteId>(&input.search, order)?;
            Ok(pb::SearchOut {
                ids: nids.into_iter().map(|v| v.0).collect(),
            })
        })
    }

    fn join_search_nodes(&self, input: pb::JoinSearchNodesIn) -> Result<pb::String> {
        let sep = input.joiner().into();
        let existing_nodes = {
            let node: Node = input.existing_node.unwrap_or_default().try_into()?;
            node.into_node_list()
        };
        let additional_node = input.additional_node.unwrap_or_default().try_into()?;
        Ok(concatenate_searches(sep, existing_nodes, additional_node).into())
    }

    fn replace_search_node(&self, input: pb::ReplaceSearchNodeIn) -> Result<pb::String> {
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

    fn find_and_replace(&self, input: pb::FindAndReplaceIn) -> Result<pb::OpChangesWithCount> {
        let mut search = if input.regex {
            input.search
        } else {
            regex::escape(&input.search)
        };
        if !input.match_case {
            search = format!("(?i){}", search);
        }
        let nids = input.nids.into_iter().map(NoteId).collect();
        let field_name = if input.field_name.is_empty() {
            None
        } else {
            Some(input.field_name)
        };
        let repl = input.replacement;
        self.with_col(|col| {
            col.find_and_replace(nids, &search, &repl, field_name)
                .map(Into::into)
        })
    }

    fn browser_row_for_id(&self, input: pb::Int64) -> Result<pb::BrowserRow> {
        self.with_col(|col| col.browser_row_for_id(input.val).map(Into::into))
    }

    fn set_desktop_browser_card_columns(&self, input: pb::StringList) -> Result<pb::Empty> {
        self.with_col(|col| col.set_desktop_browser_card_columns(input.into()))?;
        Ok(().into())
    }

    fn set_desktop_browser_note_columns(&self, input: pb::StringList) -> Result<pb::Empty> {
        self.with_col(|col| col.set_desktop_browser_note_columns(input.into()))?;
        Ok(().into())
    }
}

impl From<SortKindProto> for SortKind {
    fn from(kind: SortKindProto) -> Self {
        match kind {
            SortKindProto::NoteCards => SortKind::NoteCards,
            SortKindProto::NoteCreation => SortKind::NoteCreation,
            SortKindProto::NoteEase => SortKind::NoteEase,
            SortKindProto::NoteLapses => SortKind::NoteLapses,
            SortKindProto::NoteMod => SortKind::NoteMod,
            SortKindProto::NoteField => SortKind::NoteField,
            SortKindProto::NoteReps => SortKind::NoteReps,
            SortKindProto::NoteTags => SortKind::NoteTags,
            SortKindProto::Notetype => SortKind::Notetype,
            SortKindProto::CardMod => SortKind::CardMod,
            SortKindProto::CardReps => SortKind::CardReps,
            SortKindProto::CardDue => SortKind::CardDue,
            SortKindProto::CardEase => SortKind::CardEase,
            SortKindProto::CardLapses => SortKind::CardLapses,
            SortKindProto::CardInterval => SortKind::CardInterval,
            SortKindProto::CardDeck => SortKind::CardDeck,
            SortKindProto::CardTemplate => SortKind::CardTemplate,
        }
    }
}

impl From<Option<SortOrderProto>> for SortMode {
    fn from(order: Option<SortOrderProto>) -> Self {
        use pb::sort_order::Value as V;
        match order.unwrap_or(V::FromConfig(pb::Empty {})) {
            V::None(_) => SortMode::NoOrder,
            V::Custom(s) => SortMode::Custom(s),
            V::FromConfig(_) => SortMode::FromConfig,
            V::Builtin(b) => SortMode::Builtin {
                kind: b.kind().into(),
                reverse: b.reverse,
            },
        }
    }
}
