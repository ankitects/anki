// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use super::FilteredDeckForUpdate;
use crate::{
    backend_proto::{
        self as pb,
        custom_study_request::{cram::CramKind, Cram, Value as CustomStudyValue},
    },
    config::DeckConfigKey,
    decks::{FilteredDeck, FilteredSearchOrder, FilteredSearchTerm},
    error::{CustomStudyError, FilteredDeckError},
    prelude::*,
    search::{Negated, PropertyKind, RatingKind, SearchNode, StateKind},
};

impl Collection {
    pub fn custom_study(&mut self, input: pb::CustomStudyRequest) -> Result<OpOutput<()>> {
        self.transact(Op::CreateCustomStudy, |col| col.custom_study_inner(input))
    }

    pub fn custom_study_defaults(
        &mut self,
        deck_id: DeckId,
    ) -> Result<pb::CustomStudyDefaultsResponse> {
        // daily counts
        let deck = self.get_deck(deck_id)?.ok_or(AnkiError::NotFound)?;
        let normal = deck.normal()?;
        let extend_new = normal.extend_new;
        let extend_review = normal.extend_review;
        let subtree = self
            .deck_tree(Some(TimestampSecs::now()))?
            .get_deck(deck_id)
            .ok_or(AnkiError::NotFound)?;
        let available_new = subtree.sum(|node| node.new_uncapped);
        let available_review = subtree.sum(|node| node.review_uncapped);
        // tags
        let include_tags: HashSet<String> = self.get_config_default(
            DeckConfigKey::CustomStudyIncludeTags
                .for_deck(deck_id)
                .as_str(),
        );
        let exclude_tags: HashSet<String> = self.get_config_default(
            DeckConfigKey::CustomStudyExcludeTags
                .for_deck(deck_id)
                .as_str(),
        );
        let mut all_tags: Vec<_> = self.all_tags_in_deck(deck_id)?.into_iter().collect();
        all_tags.sort_unstable();
        let tags: Vec<pb::custom_study_defaults_response::Tag> = all_tags
            .into_iter()
            .map(|tag| {
                let tag = tag.into_inner();
                pb::custom_study_defaults_response::Tag {
                    include: include_tags.contains(&tag),
                    exclude: exclude_tags.contains(&tag),
                    name: tag,
                }
            })
            .collect();

        Ok(pb::CustomStudyDefaultsResponse {
            tags,
            extend_new,
            extend_review,
            available_new,
            available_review,
        })
    }
}

impl Collection {
    fn custom_study_inner(&mut self, input: pb::CustomStudyRequest) -> Result<()> {
        let mut deck = self
            .storage
            .get_deck(input.deck_id.into())?
            .ok_or(AnkiError::NotFound)?;

        match input
            .value
            .ok_or_else(|| AnkiError::invalid_input("missing oneof value"))?
        {
            CustomStudyValue::NewLimitDelta(delta) => {
                let today = self.current_due_day(0)?;
                self.extend_limits(today, self.usn()?, deck.id, delta, 0)?;
                if delta > 0 {
                    let original = deck.clone();
                    deck.normal_mut()?.extend_new = delta as u32;
                    self.update_deck_inner(&mut deck, original, self.usn()?)?;
                }
                Ok(())
            }
            CustomStudyValue::ReviewLimitDelta(delta) => {
                let today = self.current_due_day(0)?;
                self.extend_limits(today, self.usn()?, deck.id, 0, delta)?;
                if delta > 0 {
                    let original = deck.clone();
                    deck.normal_mut()?.extend_review = delta as u32;
                    self.update_deck_inner(&mut deck, original, self.usn()?)?;
                }
                Ok(())
            }
            CustomStudyValue::ForgotDays(days) => {
                self.create_custom_study_deck(forgot_config(deck.human_name(), days))
            }
            CustomStudyValue::ReviewAheadDays(days) => {
                self.create_custom_study_deck(ahead_config(deck.human_name(), days))
            }
            CustomStudyValue::PreviewDays(days) => {
                self.create_custom_study_deck(preview_config(deck.human_name(), days))
            }
            CustomStudyValue::Cram(cram) => {
                self.create_custom_study_deck(cram_config(deck.human_name(), &cram)?)?;
                self.set_config(
                    DeckConfigKey::CustomStudyIncludeTags
                        .for_deck(deck.id)
                        .as_str(),
                    &cram.tags_to_include,
                )?;
                self.set_config(
                    DeckConfigKey::CustomStudyExcludeTags
                        .for_deck(deck.id)
                        .as_str(),
                    &cram.tags_to_exclude,
                )?;
                Ok(())
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

fn cram_config(deck_name: String, cram: &Cram) -> Result<FilteredDeck> {
    let (reschedule, nodes, order) = match cram.kind() {
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
            &cram.tags_to_include,
            &cram.tags_to_exclude,
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

fn tags_to_nodes(tags_to_include: &[String], tags_to_exclude: &[String]) -> SearchBuilder {
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

#[cfg(test)]
mod test {
    use super::*;
    use crate::{
        backend_proto::{
            scheduler::custom_study_request::{cram::CramKind, Cram, Value},
            CustomStudyRequest,
        },
        collection::open_test_collection,
    };

    #[test]
    fn tag_remembering() -> Result<()> {
        let mut col = open_test_collection();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags
            .extend_from_slice(&["3".to_string(), "1".to_string(), "2::two".to_string()]);
        col.add_note(&mut note, DeckId(1))?;
        let mut note = nt.new_note();
        note.tags
            .extend_from_slice(&["1".to_string(), "2::two".to_string()]);
        col.add_note(&mut note, DeckId(1))?;

        fn get_defaults(col: &mut Collection) -> Result<Vec<(&'static str, bool, bool)>> {
            Ok(col
                .custom_study_defaults(DeckId(1))?
                .tags
                .into_iter()
                .map(|tag| {
                    (
                        // cheekily leak the string so we have a static ref for comparison
                        &*Box::leak(tag.name.into_boxed_str()),
                        tag.include,
                        tag.exclude,
                    )
                })
                .collect())
        }

        // nothing should be included/excluded by default
        assert_eq!(
            &get_defaults(&mut col)?,
            &[
                ("1", false, false),
                ("2::two", false, false),
                ("3", false, false)
            ]
        );

        // if filtered deck creation fails, inclusions/exclusions don't change
        let mut cram = Cram {
            kind: CramKind::All as i32,
            card_limit: 0,
            tags_to_include: vec!["2::two".to_string()],
            tags_to_exclude: vec!["3".to_string()],
        };
        assert_eq!(
            col.custom_study(CustomStudyRequest {
                deck_id: 1,
                value: Some(Value::Cram(cram.clone())),
            }),
            Err(AnkiError::CustomStudyError(
                CustomStudyError::NoMatchingCards
            ))
        );
        assert_eq!(
            &get_defaults(&mut col)?,
            &[
                ("1", false, false),
                ("2::two", false, false),
                ("3", false, false)
            ]
        );

        // a successful build should update tags
        cram.card_limit = 100;
        col.custom_study(CustomStudyRequest {
            deck_id: 1,
            value: Some(Value::Cram(cram)),
        })?;
        assert_eq!(
            &get_defaults(&mut col)?,
            &[
                ("1", false, false),
                ("2::two", true, false),
                ("3", false, true)
            ]
        );

        Ok(())
    }
}
