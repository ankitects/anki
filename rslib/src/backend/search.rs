// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use itertools::Itertools;
use std::convert::{TryFrom, TryInto};

use crate::{
    backend_proto as pb,
    backend_proto::{
        sort_order::builtin::Kind as SortKindProto, sort_order::Value as SortOrderProto,
    },
    config::SortKind,
    prelude::*,
    search::{
        parse_search, BoolSeparator, Node, PropertyKind, RatingKind, SearchNode, SortMode,
        StateKind, TemplateKind,
    },
    text::escape_anki_wildcards,
};

impl TryFrom<pb::SearchNode> for Node {
    type Error = AnkiError;

    fn try_from(msg: pb::SearchNode) -> std::result::Result<Self, Self::Error> {
        use pb::search_node::group::Joiner;
        use pb::search_node::Filter;
        use pb::search_node::Flag;
        Ok(if let Some(filter) = msg.filter {
            match filter {
                Filter::Tag(s) => Node::Search(SearchNode::Tag(escape_anki_wildcards(&s))),
                Filter::Deck(s) => Node::Search(SearchNode::Deck(if s == "*" {
                    s
                } else {
                    escape_anki_wildcards(&s)
                })),
                Filter::Note(s) => Node::Search(SearchNode::NoteType(escape_anki_wildcards(&s))),
                Filter::Template(u) => {
                    Node::Search(SearchNode::CardTemplate(TemplateKind::Ordinal(u as u16)))
                }
                Filter::Nid(nid) => Node::Search(SearchNode::NoteIDs(nid.to_string())),
                Filter::Nids(nids) => Node::Search(SearchNode::NoteIDs(nids.into_id_string())),
                Filter::Dupe(dupe) => Node::Search(SearchNode::Duplicates {
                    note_type_id: dupe.notetype_id.into(),
                    text: dupe.first_field,
                }),
                Filter::FieldName(s) => Node::Search(SearchNode::SingleField {
                    field: escape_anki_wildcards(&s),
                    text: "*".to_string(),
                    is_re: false,
                }),
                Filter::Rated(rated) => Node::Search(SearchNode::Rated {
                    days: rated.days,
                    ease: rated.rating().into(),
                }),
                Filter::AddedInDays(u) => Node::Search(SearchNode::AddedInDays(u)),
                Filter::DueInDays(i) => Node::Search(SearchNode::Property {
                    operator: "<=".to_string(),
                    kind: PropertyKind::Due(i),
                }),
                Filter::DueOnDay(i) => Node::Search(SearchNode::Property {
                    operator: "=".to_string(),
                    kind: PropertyKind::Due(i),
                }),
                Filter::EditedInDays(u) => Node::Search(SearchNode::EditedInDays(u)),
                Filter::CardState(state) => Node::Search(SearchNode::State(
                    pb::search_node::CardState::from_i32(state)
                        .unwrap_or_default()
                        .into(),
                )),
                Filter::Flag(flag) => match Flag::from_i32(flag).unwrap_or(Flag::Any) {
                    Flag::None => Node::Search(SearchNode::Flag(0)),
                    Flag::Any => Node::Not(Box::new(Node::Search(SearchNode::Flag(0)))),
                    Flag::Red => Node::Search(SearchNode::Flag(1)),
                    Flag::Orange => Node::Search(SearchNode::Flag(2)),
                    Flag::Green => Node::Search(SearchNode::Flag(3)),
                    Flag::Blue => Node::Search(SearchNode::Flag(4)),
                },
                Filter::Negated(term) => Node::try_from(*term)?.negated(),
                Filter::Group(mut group) => {
                    match group.nodes.len() {
                        0 => return Err(AnkiError::invalid_input("empty group")),
                        // a group of 1 doesn't need to be a group
                        1 => group.nodes.pop().unwrap().try_into()?,
                        // 2+ nodes
                        _ => {
                            let joiner = match group.joiner() {
                                Joiner::And => Node::And,
                                Joiner::Or => Node::Or,
                            };
                            let parsed: Vec<_> = group
                                .nodes
                                .into_iter()
                                .map(TryFrom::try_from)
                                .collect::<Result<_>>()?;
                            let joined = parsed.into_iter().intersperse(joiner).collect();
                            Node::Group(joined)
                        }
                    }
                }
                Filter::ParsableText(text) => {
                    let mut nodes = parse_search(&text)?;
                    if nodes.len() == 1 {
                        nodes.pop().unwrap()
                    } else {
                        Node::Group(nodes)
                    }
                }
            }
        } else {
            Node::Search(SearchNode::WholeCollection)
        })
    }
}

impl From<pb::search_node::group::Joiner> for BoolSeparator {
    fn from(sep: pb::search_node::group::Joiner) -> Self {
        match sep {
            pb::search_node::group::Joiner::And => BoolSeparator::And,
            pb::search_node::group::Joiner::Or => BoolSeparator::Or,
        }
    }
}

impl From<pb::search_node::Rating> for RatingKind {
    fn from(r: pb::search_node::Rating) -> Self {
        match r {
            pb::search_node::Rating::Again => RatingKind::AnswerButton(1),
            pb::search_node::Rating::Hard => RatingKind::AnswerButton(2),
            pb::search_node::Rating::Good => RatingKind::AnswerButton(3),
            pb::search_node::Rating::Easy => RatingKind::AnswerButton(4),
            pb::search_node::Rating::Any => RatingKind::AnyAnswerButton,
            pb::search_node::Rating::ByReschedule => RatingKind::ManualReschedule,
        }
    }
}

impl From<pb::search_node::CardState> for StateKind {
    fn from(k: pb::search_node::CardState) -> Self {
        match k {
            pb::search_node::CardState::New => StateKind::New,
            pb::search_node::CardState::Learn => StateKind::Learning,
            pb::search_node::CardState::Review => StateKind::Review,
            pb::search_node::CardState::Due => StateKind::Due,
            pb::search_node::CardState::Suspended => StateKind::Suspended,
            pb::search_node::CardState::Buried => StateKind::Buried,
        }
    }
}

impl pb::search_node::IdList {
    fn into_id_string(self) -> String {
        self.ids
            .iter()
            .map(|i| i.to_string())
            .collect::<Vec<_>>()
            .join(",")
    }
}

impl From<SortKindProto> for SortKind {
    fn from(kind: SortKindProto) -> Self {
        match kind {
            SortKindProto::NoteCreation => SortKind::NoteCreation,
            SortKindProto::NoteMod => SortKind::NoteMod,
            SortKindProto::NoteField => SortKind::NoteField,
            SortKindProto::NoteTags => SortKind::NoteTags,
            SortKindProto::NoteType => SortKind::NoteType,
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
