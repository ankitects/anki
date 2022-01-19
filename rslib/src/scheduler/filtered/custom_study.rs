// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::cmp::Ordering;

use super::FilteredDeckForUpdate;
use crate::{
    backend_proto::{
        self as pb,
        custom_study_request::{cram::CramKind, Cram, Value as CustomStudyValue},
    },
    decks::{FilteredDeck, FilteredSearchOrder, FilteredSearchTerm},
    error::{CustomStudyError, FilteredDeckError},
    prelude::*,
    search::{
        concatenate_searches, BoolSeparator, Node, PropertyKind, RatingKind, SearchNode, StateKind,
    },
    text::escape_anki_wildcards_for_search_node,
};
use itertools::Itertools;

impl Collection {
    pub fn custom_study(&mut self, input: pb::CustomStudyRequest) -> Result<OpOutput<()>> {
        self.transact(Op::CreateCustomStudy, |col| col.custom_study_inner(input))
    }
}

impl Collection {
    fn custom_study_inner(&mut self, input: pb::CustomStudyRequest) -> Result<()> {
        let current_deck = self.get_current_deck()?;

        match input
            .value
            .ok_or_else(|| AnkiError::invalid_input("missing oneof value"))?
        {
            CustomStudyValue::NewLimitDelta(delta) => {
                let today = self.current_due_day(0)?;
                self.extend_limits(today, self.usn()?, current_deck.id, delta, 0)
            }
            CustomStudyValue::ReviewLimitDelta(delta) => {
                let today = self.current_due_day(0)?;
                self.extend_limits(today, self.usn()?, current_deck.id, 0, delta)
            }
            CustomStudyValue::ForgotDays(days) => {
                self.create_custom_study_deck(forgot_config(current_deck.human_name(), days))
            }
            CustomStudyValue::ReviewAheadDays(days) => {
                self.create_custom_study_deck(ahead_config(current_deck.human_name(), days))
            }
            CustomStudyValue::PreviewDays(days) => {
                self.create_custom_study_deck(preview_config(current_deck.human_name(), days))
            }
            CustomStudyValue::Cram(cram) => {
                self.create_custom_study_deck(cram_config(current_deck.human_name(), cram)?)
            }
        }
    }

    /// Reuse existing one or create new one if missing.
    /// Guaranteed to be a filtered deck.
    fn create_custom_study_deck(&mut self, config: FilteredDeck) -> Result<()> {
        let mut id = DeckId(0);
        let human_name = self.tr.custom_study_custom_study_session().to_string();

        if let Some(did) = self.get_deck_id(&human_name)? {
            if !self
                .get_deck(did)?
                .ok_or(AnkiError::NotFound)?
                .is_filtered()
            {
                return Err(CustomStudyError::ExistingDeck.into());
            }
            id = did;
        }

        self.add_or_update_filtered_deck_inner(FilteredDeckForUpdate {
            id,
            human_name,
            config,
        })
        .map(|_| ())
        .map_err(|err| {
            if err == AnkiError::FilteredDeckError(FilteredDeckError::SearchReturnedNoCards) {
                CustomStudyError::NoMatchingCards.into()
            } else {
                err
            }
        })
    }
}

fn custom_study_config(
    deck_name: String,
    reschedule: bool,
    nodes: Vec<Node>,
    order: FilteredSearchOrder,
    limit: Option<u32>,
) -> FilteredDeck {
    let search = concatenate_searches(
        BoolSeparator::And,
        nodes,
        Node::Search(SearchNode::Deck(deck_name)),
    );
    FilteredDeck {
        reschedule,
        search_terms: vec![FilteredSearchTerm {
            search,
            limit: limit.unwrap_or(99_999),
            order: order as i32,
        }],
        delays: vec![],
        preview_delay: 10,
    }
}

fn forgot_config(deck_name: String, days: u32) -> FilteredDeck {
    let nodes = vec![Node::Search(SearchNode::Rated {
        days,
        ease: RatingKind::AnswerButton(1),
    })];
    custom_study_config(deck_name, false, nodes, FilteredSearchOrder::Random, None)
}

fn ahead_config(deck_name: String, days: u32) -> FilteredDeck {
    let nodes = vec![Node::Search(SearchNode::Property {
        operator: "<=".to_string(),
        kind: PropertyKind::Due(days as i32),
    })];
    custom_study_config(deck_name, true, nodes, FilteredSearchOrder::Due, None)
}

fn preview_config(deck_name: String, days: u32) -> FilteredDeck {
    let nodes = vec![
        Node::Search(SearchNode::State(StateKind::New)),
        Node::And,
        Node::Search(SearchNode::AddedInDays(days)),
    ];
    custom_study_config(
        deck_name,
        false,
        nodes,
        FilteredSearchOrder::OldestReviewedFirst,
        None,
    )
}

macro_rules! state_node {
    (not $state_kind:expr) => {
        Node::Not(Box::new(Node::Search(SearchNode::State($state_kind))))
    };
    ($state_kind:expr) => {
        Node::Search(SearchNode::State($state_kind))
    };
}

fn cram_config(deck_name: String, cram: Cram) -> Result<FilteredDeck> {
    let (reschedule, node, order) = match CramKind::from_i32(cram.kind).unwrap_or_default() {
        CramKind::New => (
            true,
            Some(state_node!(StateKind::New)),
            FilteredSearchOrder::Added,
        ),
        CramKind::Due => (
            true,
            Some(state_node!(StateKind::Due)),
            FilteredSearchOrder::Due,
        ),
        CramKind::Review => (
            true,
            Some(state_node!(not StateKind::New)),
            FilteredSearchOrder::Random,
        ),
        CramKind::All => (false, None, FilteredSearchOrder::Random),
    };

    let mut nodes = vec![];
    let mut tags = tags_to_nodes(cram.tags_to_include, cram.tags_to_exclude);
    if let Some(node) = node {
        nodes.push(node);
        if !tags.is_empty() {
            nodes.push(Node::And);
        }
    }
    nodes.append(&mut tags);

    Ok(custom_study_config(
        deck_name,
        reschedule,
        nodes,
        order,
        Some(cram.card_limit),
    ))
}

fn tags_to_nodes(tags_to_include: Vec<String>, tags_to_exclude: Vec<String>) -> Vec<Node> {
    let mut nodes = vec![];
    let mut include_nodes: Vec<Node> = Itertools::intersperse(
        tags_to_include
            .iter()
            .map(|tag| Node::Search(SearchNode::Tag(escape_anki_wildcards_for_search_node(tag)))),
        Node::Or,
    )
    .collect();
    let mut exclude_nodes: Vec<Node> = Itertools::intersperse(
        tags_to_exclude.iter().map(|tag| {
            Node::Not(Box::new(Node::Search(SearchNode::Tag(
                escape_anki_wildcards_for_search_node(tag),
            ))))
        }),
        Node::And,
    )
    .collect();

    match include_nodes.len().cmp(&1) {
        Ordering::Less => (),
        Ordering::Equal => nodes.append(&mut include_nodes),
        Ordering::Greater => nodes.push(Node::Group(include_nodes)),
    }

    if !(nodes.is_empty() || exclude_nodes.is_empty()) {
        nodes.push(Node::And);
    }
    nodes.append(&mut exclude_nodes);

    nodes
}
