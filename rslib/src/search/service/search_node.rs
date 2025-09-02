// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::search::search_node::IdList;
use itertools::Itertools;

use crate::prelude::*;
use crate::search::parse_search;
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
                    is_re: false,
                    is_nc: false,
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
                    is_re: field.is_re,
                    is_nc: false,
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
