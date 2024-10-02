// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use anki_proto::scheduler::custom_study_request::cram::CramKind;
use anki_proto::scheduler::custom_study_request::Cram;
use anki_proto::scheduler::custom_study_request::Value as CustomStudyValue;

use super::FilteredDeckForUpdate;
use crate::config::DeckConfigKey;
use crate::decks::tree::get_deck_in_tree;
use crate::decks::tree::sum_deck_tree_node;
use crate::decks::FilteredDeck;
use crate::decks::FilteredSearchOrder;
use crate::decks::FilteredSearchTerm;
use crate::error::CustomStudyError;
use crate::error::FilteredDeckError;
use crate::prelude::*;
use crate::search::JoinSearches;
use crate::search::Negated;
use crate::search::PropertyKind;
use crate::search::RatingKind;
use crate::search::SearchNode;
use crate::search::StateKind;

impl Collection {
    pub fn custom_study(
        &mut self,
        input: anki_proto::scheduler::CustomStudyRequest,
    ) -> Result<OpOutput<()>> {
        self.transact(Op::CreateCustomStudy, |col| col.custom_study_inner(input))
    }

    pub fn custom_study_defaults(
        &mut self,
        deck_id: DeckId,
    ) -> Result<anki_proto::scheduler::CustomStudyDefaultsResponse> {
        // daily counts
        let deck = self.get_deck(deck_id)?.or_not_found(deck_id)?;
        let normal = deck.normal()?;
        let extend_new = normal.extend_new;
        let extend_review = normal.extend_review;

        let subtree = get_deck_in_tree(self.deck_tree(Some(TimestampSecs::now()))?, deck_id)
            .or_not_found(deck_id)?;
        let available_new_including_children =
            sum_deck_tree_node(&subtree, |node| node.new_uncapped);
        let available_review_including_children =
            sum_deck_tree_node(&subtree, |node| node.review_uncapped);
        let (
            available_new,
            available_new_in_children,
            available_review,
            available_review_in_children,
        ) = (
            subtree.new_uncapped,
            available_new_including_children - subtree.new_uncapped,
            subtree.review_uncapped,
            available_review_including_children - subtree.review_uncapped,
        );
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
        let tags: Vec<anki_proto::scheduler::custom_study_defaults_response::Tag> = all_tags
            .into_iter()
            .map(|tag| {
                let tag = tag.into_inner();
                anki_proto::scheduler::custom_study_defaults_response::Tag {
                    include: include_tags.contains(&tag),
                    exclude: exclude_tags.contains(&tag),
                    name: tag,
                }
            })
            .collect();

        Ok(anki_proto::scheduler::CustomStudyDefaultsResponse {
            tags,
            extend_new,
            extend_review,
            available_new,
            available_review,
            available_new_in_children,
            available_review_in_children,
        })
    }
}

impl Collection {
    fn custom_study_inner(
        &mut self,
        input: anki_proto::scheduler::CustomStudyRequest,
    ) -> Result<()> {
        let mut deck = self
            .storage
            .get_deck(input.deck_id.into())?
            .or_not_found(input.deck_id)?;

        match input.value.or_invalid("missing oneof value")? {
            CustomStudyValue::NewLimitDelta(delta) => {
                let today = self.current_due_day(0)?;
                self.extend_limits(today, self.usn()?, deck.id, delta, 0)?;
                if delta > 0 {
                    deck = self.storage.get_deck(deck.id)?.or_not_found(deck.id)?;
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
                    deck = self.storage.get_deck(deck.id)?.or_not_found(deck.id)?;
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
            if !self.get_deck(did)?.or_not_found(did)?.is_filtered() {
                return Err(CustomStudyError::ExistingDeck.into());
            }
            id = did;
        }

        let deck = FilteredDeckForUpdate {
            id,
            human_name,
            config,
            allow_empty: false,
        };

        self.add_or_update_filtered_deck_inner(deck)
            .map(|_| ())
            .map_err(|err| {
                if matches!(
                    err,
                    AnkiError::FilteredDeckError {
                        source: FilteredDeckError::SearchReturnedNoCards
                    }
                ) {
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
        preview_again_secs: 60,
        preview_hard_secs: 600,
        preview_good_secs: 0,
    }
}

fn forgot_config(deck_name: String, days: u32) -> FilteredDeck {
    let search = SearchNode::Rated {
        days,
        ease: RatingKind::AnswerButton(1),
    }
    .and(SearchNode::from_deck_name(&deck_name))
    .write();
    custom_study_config(false, search, FilteredSearchOrder::Random, None)
}

fn ahead_config(deck_name: String, days: u32) -> FilteredDeck {
    let search = SearchNode::Property {
        operator: "<=".to_string(),
        kind: PropertyKind::Due(days as i32),
    }
    .and(SearchNode::from_deck_name(&deck_name))
    .write();
    custom_study_config(true, search, FilteredSearchOrder::Due, None)
}

fn preview_config(deck_name: String, days: u32) -> FilteredDeck {
    let search = StateKind::New
        .and_flat(SearchNode::AddedInDays(days))
        .and_flat(SearchNode::from_deck_name(&deck_name))
        .write();
    custom_study_config(false, search, FilteredSearchOrder::Added, None)
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
        .and(SearchNode::from_deck_name(&deck_name))
        .and_flat(tags_to_nodes(&cram.tags_to_include, &cram.tags_to_exclude))
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
    let exclude_nodes = SearchBuilder::all(
        tags_to_exclude
            .iter()
            .map(|tag| SearchNode::from_tag_name(tag).negated()),
    );

    include_nodes.and(exclude_nodes)
}

#[cfg(test)]
mod test {
    use anki_proto::scheduler::custom_study_request::cram::CramKind;
    use anki_proto::scheduler::custom_study_request::Cram;
    use anki_proto::scheduler::custom_study_request::Value;
    use anki_proto::scheduler::CustomStudyRequest;

    use super::*;

    #[test]
    fn tag_remembering() -> Result<()> {
        let mut col = Collection::new();

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
            Err(AnkiError::CustomStudyError {
                source: CustomStudyError::NoMatchingCards
            })
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

    #[test]
    fn sql_grouping() -> Result<()> {
        let mut deck = preview_config("d".into(), 1);
        assert_eq!(&deck.search_terms[0].search, "is:new added:1 deck:d");

        let cram = Cram {
            tags_to_include: vec!["1".into(), "2".into()],
            tags_to_exclude: vec!["3".into(), "4".into()],
            ..Default::default()
        };
        deck = cram_config("d".into(), &cram)?;
        assert_eq!(
            &deck.search_terms[0].search,
            "is:due deck:d (tag:1 OR tag:2) (-tag:3 -tag:4)"
        );

        Ok(())
    }
}
