// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::search::search_node::IdList;
use itertools::Itertools;

use crate::prelude::*;
use crate::search::parse_search;
use crate::search::FieldSearchMode;
use crate::search::Negated;
use crate::search::Node;
use crate::search::PropertyKind;
use crate::search::RatingKind;
use crate::search::SearchNode;
use crate::search::StateKind;
use crate::search::TemplateKind;
use crate::text::escape_anki_wildcards;
use crate::text::escape_anki_wildcards_for_search_node;

impl TryFrom<anki_proto::search::SearchNode> for Node {
    type Error = AnkiError;

    fn try_from(msg: anki_proto::search::SearchNode) -> std::result::Result<Self, Self::Error> {
        use anki_proto::search::search_node::group::Joiner;
        use anki_proto::search::search_node::Filter;
        use anki_proto::search::search_node::Flag;
        Ok(if let Some(filter) = msg.filter {
            match filter {
                Filter::Tag(s) => SearchNode::from_tag_name(&s).into(),
                Filter::Deck(s) => SearchNode::from_deck_name(&s).into(),
                Filter::Note(s) => SearchNode::from_notetype_name(&s).into(),
                Filter::Template(u) => {
                    Node::Search(SearchNode::CardTemplate(TemplateKind::Ordinal(u as u16)))
                }
                Filter::Nid(nid) => Node::Search(SearchNode::NoteIds(nid.to_string())),
                Filter::Nids(nids) => Node::Search(SearchNode::NoteIds(id_list_to_string(nids))),
                Filter::Dupe(dupe) => Node::Search(SearchNode::Duplicates {
                    notetype_id: dupe.notetype_id.into(),
                    text: dupe.first_field,
                }),
                Filter::FieldName(s) => Node::Search(SearchNode::SingleField {
                    field: escape_anki_wildcards_for_search_node(&s),
                    text: "_*".to_string(),
                    mode: FieldSearchMode::Normal,
                }),
                Filter::Rated(rated) => Node::Search(SearchNode::Rated {
                    days: rated.days,
                    ease: rated.rating().into(),
                }),
                Filter::AddedInDays(u) => Node::Search(SearchNode::AddedInDays(u)),
                Filter::IntroducedInDays(u) => Node::Search(SearchNode::IntroducedInDays(u)),
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
                    anki_proto::search::search_node::CardState::try_from(state)
                        .unwrap_or_default()
                        .into(),
                )),
                Filter::Flag(flag) => match Flag::try_from(flag).unwrap_or(Flag::Any) {
                    Flag::None => Node::Search(SearchNode::Flag(0)),
                    Flag::Any => Node::Not(Box::new(Node::Search(SearchNode::Flag(0)))),
                    Flag::Red => Node::Search(SearchNode::Flag(1)),
                    Flag::Orange => Node::Search(SearchNode::Flag(2)),
                    Flag::Green => Node::Search(SearchNode::Flag(3)),
                    Flag::Blue => Node::Search(SearchNode::Flag(4)),
                    Flag::Pink => Node::Search(SearchNode::Flag(5)),
                    Flag::Turquoise => Node::Search(SearchNode::Flag(6)),
                    Flag::Purple => Node::Search(SearchNode::Flag(7)),
                },
                Filter::Negated(term) => Node::try_from(*term)?.negated(),
                Filter::Group(mut group) => {
                    match group.nodes.len() {
                        0 => invalid_input!("empty group"),
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
                            let joined =
                                Itertools::intersperse(parsed.into_iter(), joiner).collect();
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
                Filter::Field(field) => Node::Search(SearchNode::SingleField {
                    field: escape_anki_wildcards(&field.field_name),
                    text: escape_anki_wildcards(&field.text),
                    mode: field.mode().into(),
                }),
                Filter::LiteralText(text) => {
                    let text = escape_anki_wildcards(&text);
                    Node::Search(SearchNode::UnqualifiedText(text))
                }
            }
        } else {
            Node::Search(SearchNode::WholeCollection)
        })
    }
}

impl From<anki_proto::search::search_node::Rating> for RatingKind {
    fn from(r: anki_proto::search::search_node::Rating) -> Self {
        match r {
            anki_proto::search::search_node::Rating::Again => RatingKind::AnswerButton(1),
            anki_proto::search::search_node::Rating::Hard => RatingKind::AnswerButton(2),
            anki_proto::search::search_node::Rating::Good => RatingKind::AnswerButton(3),
            anki_proto::search::search_node::Rating::Easy => RatingKind::AnswerButton(4),
            anki_proto::search::search_node::Rating::Any => RatingKind::AnyAnswerButton,
            anki_proto::search::search_node::Rating::ByReschedule => RatingKind::ManualReschedule,
        }
    }
}

impl From<anki_proto::search::search_node::CardState> for StateKind {
    fn from(k: anki_proto::search::search_node::CardState) -> Self {
        match k {
            anki_proto::search::search_node::CardState::New => StateKind::New,
            anki_proto::search::search_node::CardState::Learn => StateKind::Learning,
            anki_proto::search::search_node::CardState::Review => StateKind::Review,
            anki_proto::search::search_node::CardState::Due => StateKind::Due,
            anki_proto::search::search_node::CardState::Suspended => StateKind::Suspended,
            anki_proto::search::search_node::CardState::Buried => StateKind::Buried,
        }
    }
}

fn id_list_to_string(list: IdList) -> String {
    list.ids
        .iter()
        .map(|i| i.to_string())
        .collect::<Vec<_>>()
        .join(",")
}

#[cfg(test)]
mod tests {
    use anki_proto::search::search_node::Filter;
    use anki_proto::search::SearchNode as ProtoSearchNode;

    use super::*;

    #[test]
    fn whole_collection_when_filter_is_none() {
        let proto = ProtoSearchNode { filter: None };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::WholeCollection)),
            "expected WholeCollection for empty filter"
        );
    }

    #[test]
    fn tag_filter_converts_to_tag_node() {
        let proto = ProtoSearchNode {
            filter: Some(Filter::Tag("mytag".to_string())),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::Tag { .. })),
            "expected Tag search node"
        );
    }

    #[test]
    fn deck_filter_converts_to_deck_node() {
        let proto = ProtoSearchNode {
            filter: Some(Filter::Deck("MyDeck".to_string())),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::Deck(_))),
            "expected Deck search node"
        );
    }

    #[test]
    fn note_filter_converts_to_notetype_node() {
        let proto = ProtoSearchNode {
            filter: Some(Filter::Note("Basic".to_string())),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::Notetype(_))),
            "expected Notetype search node"
        );
    }

    #[test]
    fn nid_filter_converts_to_note_ids_string() {
        let proto = ProtoSearchNode {
            filter: Some(Filter::Nid(12345)),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::NoteIds(ref s)) if s == "12345"),
            "expected NoteIds with id as string"
        );
    }

    #[test]
    fn nids_filter_joins_multiple_ids_with_comma() {
        let proto = ProtoSearchNode {
            filter: Some(Filter::Nids(anki_proto::search::search_node::IdList {
                ids: vec![1, 2, 3],
            })),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::NoteIds(ref s)) if s == "1,2,3"),
            "expected NoteIds with comma-separated ids"
        );
    }

    #[test]
    fn flag_none_becomes_flag_zero() {
        use anki_proto::search::search_node::Flag;
        let proto = ProtoSearchNode {
            filter: Some(Filter::Flag(Flag::None as i32)),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::Flag(0))),
            "expected Flag(0) for Flag::None"
        );
    }

    #[test]
    fn flag_any_becomes_negated_flag_zero() {
        use anki_proto::search::search_node::Flag;
        let proto = ProtoSearchNode {
            filter: Some(Filter::Flag(Flag::Any as i32)),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Not(ref inner) if matches!(inner.as_ref(), Node::Search(SearchNode::Flag(0)))),
            "expected Not(Flag(0)) for Flag::Any"
        );
    }

    #[test]
    fn negated_filter_wraps_inner_node_in_not() {
        let inner = ProtoSearchNode {
            filter: Some(Filter::Tag("foo".to_string())),
        };
        let proto = ProtoSearchNode {
            filter: Some(Filter::Negated(Box::new(inner))),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Not(ref inner) if matches!(inner.as_ref(), Node::Search(SearchNode::Tag { .. }))),
            "expected Not(Tag) for Negated(Tag)"
        );
    }

    #[test]
    fn group_of_one_unwraps_without_group_wrapper() {
        let inner = ProtoSearchNode {
            filter: Some(Filter::Tag("foo".to_string())),
        };
        let proto = ProtoSearchNode {
            filter: Some(Filter::Group(anki_proto::search::search_node::Group {
                joiner: anki_proto::search::search_node::group::Joiner::And as i32,
                nodes: vec![inner],
            })),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::Tag { .. })),
            "expected single Tag node, not a Group wrapper"
        );
    }

    #[test]
    fn group_of_two_uses_and_joiner() {
        let proto = ProtoSearchNode {
            filter: Some(Filter::Group(anki_proto::search::search_node::Group {
                joiner: anki_proto::search::search_node::group::Joiner::And as i32,
                nodes: vec![
                    ProtoSearchNode {
                        filter: Some(Filter::Tag("a".to_string())),
                    },
                    ProtoSearchNode {
                        filter: Some(Filter::Tag("b".to_string())),
                    },
                ],
            })),
        };
        let node: Node = proto.try_into().unwrap();
        // Group of 2 with AND → [Tag, And, Tag]
        assert!(
            matches!(&node, Node::Group(nodes) if nodes.len() == 3 && matches!(nodes[1], Node::And)),
            "expected Group([Tag, And, Tag])"
        );
    }

    #[test]
    fn field_filter_converts_to_single_field_node() {
        use anki_proto::search::search_node::FieldSearchMode;
        let proto = ProtoSearchNode {
            filter: Some(Filter::Field(anki_proto::search::search_node::Field {
                field_name: "Front".to_string(),
                text: "hello".to_string(),
                mode: FieldSearchMode::Normal as i32,
            })),
        };
        let node: Node = proto.try_into().unwrap();
        match node {
            Node::Search(SearchNode::SingleField { field, text, .. }) => {
                assert_eq!(field, "Front");
                assert_eq!(text, "hello");
            }
            other => panic!("expected SingleField, got {other:?}"),
        }
    }

    #[test]
    fn literal_text_escapes_wildcards_and_produces_unqualified_text() {
        // "*" is an Anki wildcard; LiteralText must escape it so it is treated as a literal
        let proto = ProtoSearchNode {
            filter: Some(Filter::LiteralText("hello*world".to_string())),
        };
        let node: Node = proto.try_into().unwrap();
        match node {
            Node::Search(SearchNode::UnqualifiedText(text)) => {
                assert!(
                    !text.contains('*') || text.contains("\\*"),
                    "wildcard should be escaped in literal text, got: {text}"
                );
            }
            other => panic!("expected UnqualifiedText, got {other:?}"),
        }
    }

    #[test]
    fn parsable_text_single_node_unwraps_directly() {
        // "tag:foo" parses to exactly one Node::Search(Tag), so no Group wrapper
        let proto = ProtoSearchNode {
            filter: Some(Filter::ParsableText("tag:foo".to_string())),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Search(SearchNode::Tag { .. })),
            "expected single Tag node from ParsableText"
        );
    }

    #[test]
    fn parsable_text_multiple_nodes_wraps_in_group() {
        // "tag:a tag:b" parses to two nodes, so the result is a Group
        let proto = ProtoSearchNode {
            filter: Some(Filter::ParsableText("tag:a tag:b".to_string())),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(node, Node::Group(_)),
            "expected Group for multi-node ParsableText"
        );
    }

    #[test]
    fn flag_colored_variants_convert_to_numbered_flags() {
        use anki_proto::search::search_node::Flag;
        let cases = [
            (Flag::Red, 1u8),
            (Flag::Orange, 2),
            (Flag::Green, 3),
            (Flag::Blue, 4),
            (Flag::Pink, 5),
            (Flag::Turquoise, 6),
            (Flag::Purple, 7),
        ];
        for (flag, expected_n) in cases {
            let proto = ProtoSearchNode {
                filter: Some(Filter::Flag(flag as i32)),
            };
            let node: Node = proto.try_into().unwrap();
            assert!(
                matches!(node, Node::Search(SearchNode::Flag(n)) if n == expected_n),
                "expected Flag({expected_n}) for {flag:?}"
            );
        }
    }

    #[test]
    fn template_filter_converts_to_card_template_ordinal() {
        let proto = ProtoSearchNode {
            filter: Some(Filter::Template(2)),
        };
        let node: Node = proto.try_into().unwrap();
        assert!(
            matches!(
                node,
                Node::Search(SearchNode::CardTemplate(TemplateKind::Ordinal(2)))
            ),
            "expected CardTemplate(Ordinal(2))"
        );
    }

    #[test]
    fn empty_group_returns_error() {
        let proto = ProtoSearchNode {
            filter: Some(Filter::Group(anki_proto::search::search_node::Group {
                joiner: anki_proto::search::search_node::group::Joiner::And as i32,
                nodes: vec![],
            })),
        };
        let result = Node::try_from(proto);
        assert!(result.is_err(), "expected error for empty group");
    }
}
