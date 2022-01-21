// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::FilteredDeckForUpdate;
use crate::{
    backend_proto::{
        self as pb,
        custom_study_request::{cram::CramKind, Cram, Value as CustomStudyValue},
    },
    decks::{FilteredDeck, FilteredSearchOrder, FilteredSearchTerm},
    error::{CustomStudyError, FilteredDeckError},
    prelude::*,
    search::{Negated, PropertyKind, RatingKind, SearchNode, StateKind},
};

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

        let deck = FilteredDeckForUpdate {
            id,
            human_name,
            config,
        };

        self.add_or_update_filtered_deck_inner(deck)
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
    reschedule: bool,
    search: String,
    order: FilteredSearchOrder,
    limit: Option<u32>,
) -> FilteredDeck {
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
    let search = SearchBuilder::from(SearchNode::Rated {
        days,
        ease: RatingKind::AnswerButton(1),
    })
    .and(SearchNode::from_deck_name(&deck_name))
    .write();
    custom_study_config(false, search, FilteredSearchOrder::Random, None)
}

fn ahead_config(deck_name: String, days: u32) -> FilteredDeck {
    let search = SearchBuilder::from(SearchNode::Property {
        operator: "<=".to_string(),
        kind: PropertyKind::Due(days as i32),
    })
    .and(SearchNode::from_deck_name(&deck_name))
    .write();
    custom_study_config(true, search, FilteredSearchOrder::Due, None)
}

fn preview_config(deck_name: String, days: u32) -> FilteredDeck {
    let search = SearchBuilder::from(StateKind::New)
        .and(SearchNode::AddedInDays(days))
        .and(SearchNode::from_deck_name(&deck_name))
        .write();
    custom_study_config(
        false,
        search,
        FilteredSearchOrder::OldestReviewedFirst,
        None,
    )
}

fn cram_config(deck_name: String, cram: Cram) -> Result<FilteredDeck> {
    let (reschedule, nodes, order) = match CramKind::from_i32(cram.kind).unwrap_or_default() {
        CramKind::New => (
            true,
            SearchBuilder::from(StateKind::New),
            FilteredSearchOrder::Added,
        ),
        CramKind::Due => (
            true,
            SearchBuilder::from(StateKind::Due),
            FilteredSearchOrder::Due,
        ),
        CramKind::Review => (
            true,
            SearchBuilder::from(StateKind::New.negated()),
            FilteredSearchOrder::Random,
        ),
        CramKind::All => (false, SearchBuilder::new(), FilteredSearchOrder::Random),
    };

    let search = nodes
        .and_join(&mut tags_to_nodes(
            cram.tags_to_include,
            cram.tags_to_exclude,
        ))
        .and(SearchNode::from_deck_name(&deck_name))
        .write();

    Ok(custom_study_config(
        reschedule,
        search,
        order,
        Some(cram.card_limit),
    ))
}

fn tags_to_nodes(tags_to_include: Vec<String>, tags_to_exclude: Vec<String>) -> SearchBuilder {
    let include_nodes = SearchBuilder::any(
        tags_to_include
            .iter()
            .map(|tag| SearchNode::from_tag_name(tag)),
    );
    let mut exclude_nodes = SearchBuilder::all(
        tags_to_exclude
            .iter()
            .map(|tag| SearchNode::from_tag_name(tag).negated()),
    );

    include_nodes.group().and_join(&mut exclude_nodes)
}
