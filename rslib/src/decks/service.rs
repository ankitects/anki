// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::decks::deck::kind_container::Kind as DeckKind;
use anki_proto::generic;

use crate::collection::Collection;
use crate::decks::filtered::search_order_labels;
use crate::decks::Deck;
use crate::decks::DeckId;
use crate::decks::DeckSchema11;
use crate::decks::NativeDeckName;
use crate::error;
use crate::error::AnkiError;
use crate::error::OrInvalid;
use crate::error::OrNotFound;
use crate::prelude::TimestampSecs;
use crate::prelude::Usn;
use crate::scheduler::filtered::FilteredDeckForUpdate;

impl crate::services::DecksService for Collection {
    fn new_deck(&mut self) -> error::Result<anki_proto::decks::Deck> {
        Ok(Deck::new_normal().into())
    }

    fn add_deck(
        &mut self,
        deck: anki_proto::decks::Deck,
    ) -> error::Result<anki_proto::collection::OpChangesWithId> {
        let mut deck: Deck = deck.try_into()?;
        Ok(self.add_deck(&mut deck)?.map(|_| deck.id.0).into())
    }

    fn add_deck_legacy(
        &mut self,
        input: generic::Json,
    ) -> error::Result<anki_proto::collection::OpChangesWithId> {
        let schema11: DeckSchema11 = serde_json::from_slice(&input.json)?;
        let mut deck: Deck = schema11.into();

        let output = self.add_deck(&mut deck)?;
        Ok(output.map(|_| deck.id.0).into())
    }

    fn add_or_update_deck_legacy(
        &mut self,
        input: anki_proto::decks::AddOrUpdateDeckLegacyRequest,
    ) -> error::Result<anki_proto::decks::DeckId> {
        let schema11: DeckSchema11 = serde_json::from_slice(&input.deck)?;
        let mut deck: Deck = schema11.into();
        if input.preserve_usn_and_mtime {
            self.transact_no_undo(|col| {
                let usn = col.usn()?;
                col.add_or_update_single_deck_with_existing_id(&mut deck, usn)
            })?;
        } else {
            self.add_or_update_deck(&mut deck)?;
        }
        Ok(anki_proto::decks::DeckId { did: deck.id.0 })
    }

    fn deck_tree(
        &mut self,
        input: anki_proto::decks::DeckTreeRequest,
    ) -> error::Result<anki_proto::decks::DeckTreeNode> {
        let now = if input.now == 0 {
            None
        } else {
            Some(TimestampSecs(input.now))
        };
        self.deck_tree(now)
    }

    fn deck_tree_legacy(&mut self) -> error::Result<generic::Json> {
        let tree = self.legacy_deck_tree()?;
        serde_json::to_vec(&tree)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_all_decks_legacy(&mut self) -> error::Result<generic::Json> {
        let decks = self.storage.get_all_decks_as_schema11()?;
        serde_json::to_vec(&decks)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_deck_id_by_name(
        &mut self,
        input: generic::String,
    ) -> error::Result<anki_proto::decks::DeckId> {
        self.get_deck_id(&input.val)
            .and_then(|d| d.or_not_found(input.val).map(Into::into))
    }

    fn get_deck(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> error::Result<anki_proto::decks::Deck> {
        let did = input.into();
        Ok(self.storage.get_deck(did)?.or_not_found(did)?.into())
    }

    fn update_deck(
        &mut self,
        input: anki_proto::decks::Deck,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        let mut deck = Deck::try_from(input)?;
        self.update_deck(&mut deck).map(Into::into)
    }

    fn update_deck_legacy(
        &mut self,
        input: generic::Json,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        let deck: DeckSchema11 = serde_json::from_slice(&input.json)?;
        let mut deck = deck.into();
        self.update_deck(&mut deck).map(Into::into)
    }

    fn get_deck_legacy(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> error::Result<generic::Json> {
        let did = input.into();

        let deck: DeckSchema11 = self.storage.get_deck(did)?.or_not_found(did)?.into();
        serde_json::to_vec(&deck)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_deck_names(
        &mut self,
        input: anki_proto::decks::GetDeckNamesRequest,
    ) -> error::Result<anki_proto::decks::DeckNames> {
        let skip_default = input.skip_empty_default && self.default_deck_is_empty()?;
        let names = if input.include_filtered {
            self.get_all_deck_names(skip_default)?
        } else {
            self.get_all_normal_deck_names(skip_default)?
        };
        Ok(deck_names_to_proto(names))
    }

    fn get_deck_and_child_names(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> error::Result<anki_proto::decks::DeckNames> {
        Collection::get_deck_and_child_names(self, input.did.into()).map(deck_names_to_proto)
    }

    fn new_deck_legacy(&mut self, input: generic::Bool) -> error::Result<generic::Json> {
        let deck = if input.val {
            Deck::new_filtered()
        } else {
            Deck::new_normal()
        };
        let schema11: DeckSchema11 = deck.into();
        serde_json::to_vec(&schema11)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn remove_decks(
        &mut self,
        input: anki_proto::decks::DeckIds,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.remove_decks_and_child_decks(&input.dids.into_iter().map(DeckId).collect::<Vec<_>>())
            .map(Into::into)
    }

    fn reparent_decks(
        &mut self,
        input: anki_proto::decks::ReparentDecksRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        let deck_ids: Vec<_> = input.deck_ids.into_iter().map(Into::into).collect();
        let new_parent = if input.new_parent == 0 {
            None
        } else {
            Some(input.new_parent.into())
        };
        self.reparent_decks(&deck_ids, new_parent).map(Into::into)
    }

    fn rename_deck(
        &mut self,
        input: anki_proto::decks::RenameDeckRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        self.rename_deck(input.deck_id.into(), &input.new_name)
            .map(Into::into)
    }

    fn get_or_create_filtered_deck(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> error::Result<anki_proto::decks::FilteredDeckForUpdate> {
        self.get_or_create_filtered_deck(input.into())
            .map(Into::into)
    }

    fn add_or_update_filtered_deck(
        &mut self,
        input: anki_proto::decks::FilteredDeckForUpdate,
    ) -> error::Result<anki_proto::collection::OpChangesWithId> {
        self.add_or_update_filtered_deck(input.into())
            .map(|out| out.map(i64::from))
            .map(Into::into)
    }

    fn filtered_deck_order_labels(&mut self) -> error::Result<generic::StringList> {
        Ok(search_order_labels(&self.tr).into())
    }

    fn set_deck_collapsed(
        &mut self,
        input: anki_proto::decks::SetDeckCollapsedRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        self.set_deck_collapsed(input.deck_id.into(), input.collapsed, input.scope())
            .map(Into::into)
    }

    fn set_current_deck(
        &mut self,
        input: anki_proto::decks::DeckId,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        self.set_current_deck(input.did.into()).map(Into::into)
    }

    fn get_current_deck(&mut self) -> error::Result<anki_proto::decks::Deck> {
        self.get_current_deck().map(|deck| (*deck).clone().into())
    }
}

impl From<anki_proto::decks::DeckId> for DeckId {
    fn from(did: anki_proto::decks::DeckId) -> Self {
        DeckId(did.did)
    }
}

impl From<DeckId> for anki_proto::decks::DeckId {
    fn from(did: DeckId) -> Self {
        anki_proto::decks::DeckId { did: did.0 }
    }
}

impl From<FilteredDeckForUpdate> for anki_proto::decks::FilteredDeckForUpdate {
    fn from(deck: FilteredDeckForUpdate) -> Self {
        anki_proto::decks::FilteredDeckForUpdate {
            id: deck.id.into(),
            name: deck.human_name,
            config: Some(deck.config),
            allow_empty: deck.allow_empty,
        }
    }
}

impl From<anki_proto::decks::FilteredDeckForUpdate> for FilteredDeckForUpdate {
    fn from(deck: anki_proto::decks::FilteredDeckForUpdate) -> Self {
        FilteredDeckForUpdate {
            id: deck.id.into(),
            human_name: deck.name,
            config: deck.config.unwrap_or_default(),
            allow_empty: deck.allow_empty,
        }
    }
}

impl From<Deck> for anki_proto::decks::Deck {
    fn from(d: Deck) -> Self {
        anki_proto::decks::Deck {
            id: d.id.0,
            name: d.name.human_name(),
            mtime_secs: d.mtime_secs.0,
            usn: d.usn.0,
            common: Some(d.common),
            kind: Some(kind_from_inline(d.kind)),
        }
    }
}

impl TryFrom<anki_proto::decks::Deck> for Deck {
    type Error = AnkiError;

    fn try_from(d: anki_proto::decks::Deck) -> error::Result<Self, Self::Error> {
        Ok(Deck {
            id: DeckId(d.id),
            name: NativeDeckName::from_human_name(&d.name),
            mtime_secs: TimestampSecs(d.mtime_secs),
            usn: Usn(d.usn),
            common: d.common.unwrap_or_default(),
            kind: kind_to_inline(d.kind.or_invalid("missing kind")?),
        })
    }
}

fn kind_to_inline(kind: anki_proto::decks::deck::Kind) -> DeckKind {
    match kind {
        anki_proto::decks::deck::Kind::Normal(normal) => DeckKind::Normal(normal),
        anki_proto::decks::deck::Kind::Filtered(filtered) => DeckKind::Filtered(filtered),
    }
}

fn kind_from_inline(k: DeckKind) -> anki_proto::decks::deck::Kind {
    match k {
        DeckKind::Normal(n) => anki_proto::decks::deck::Kind::Normal(n),
        DeckKind::Filtered(f) => anki_proto::decks::deck::Kind::Filtered(f),
    }
}

fn deck_name_to_proto((id, name): (DeckId, String)) -> anki_proto::decks::DeckNameId {
    anki_proto::decks::DeckNameId { id: id.0, name }
}

fn deck_names_to_proto(names: Vec<(DeckId, String)>) -> anki_proto::decks::DeckNames {
    anki_proto::decks::DeckNames {
        entries: names.into_iter().map(deck_name_to_proto).collect(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::decks::FilteredDeck;
    use crate::decks::FilteredSearchOrder;
    use crate::decks::FilteredSearchTerm;
    use crate::prelude::*;
    use crate::services::DecksService;
    use crate::tests::DeckAdder;
    use crate::tests::NoteAdder;

    fn deck_id(did: i64) -> anki_proto::decks::DeckId {
        anki_proto::decks::DeckId { did }
    }

    fn deck_id_by_name(col: &mut Collection, name: &str) -> error::Result<i64> {
        DecksService::get_deck_id_by_name(
            col,
            generic::String {
                val: name.to_string(),
            },
        )
        .map(|d| d.did)
    }

    // ----------------------------------------------------------------------
    // Conversions (pure, no Collection)
    // ----------------------------------------------------------------------

    #[test]
    fn deck_round_trips_through_proto_for_both_kinds() {
        for (name, mut expected) in [
            ("Parent::Normal", Deck::new_normal()),
            ("Parent::Filtered", Deck::new_filtered()),
        ] {
            expected.id = DeckId(123);
            expected.name = NativeDeckName::from_human_name(name);
            expected.mtime_secs = TimestampSecs(456);
            expected.usn = Usn(789);
            expected.common.study_collapsed = false;
            expected.common.last_day_studied = 10;
            expected.common.new_studied = 11;
            expected.common.review_studied = 12;
            expected.common.learning_studied = 13;
            expected.common.milliseconds_studied = 14;
            expected.common.other = br#"{"custom":"value"}"#.to_vec();

            let proto: anki_proto::decks::Deck = expected.clone().into();
            let actual = Deck::try_from(proto).unwrap();

            assert_eq!(
                actual, expected,
                "{name} deck should survive the round trip"
            );
        }
    }

    #[test]
    fn deck_from_proto_without_kind_is_invalid() {
        let proto = anki_proto::decks::Deck {
            kind: None,
            ..Default::default()
        };
        let err = Deck::try_from(proto).unwrap_err();
        assert!(
            matches!(err, AnkiError::InvalidInput { .. }),
            "a deck without a kind should be an invalid-input error, got {err:?}"
        );
    }

    #[test]
    fn deck_from_proto_defaults_common_when_missing() {
        let proto = anki_proto::decks::Deck {
            name: "Parent::Child".to_string(),
            common: None,
            kind: Some(anki_proto::decks::deck::Kind::Normal(
                anki_proto::decks::deck::Normal::default(),
            )),
            ..Default::default()
        };

        let deck = Deck::try_from(proto).unwrap();

        assert_eq!(deck.common, crate::decks::DeckCommon::default());
        assert_eq!(deck.name, NativeDeckName::from_human_name("Parent::Child"));
    }

    #[test]
    fn filtered_deck_for_update_to_proto_preserves_fields() {
        let config = FilteredDeck {
            reschedule: false,
            preview_good_secs: 42,
            search_terms: vec![FilteredSearchTerm {
                search: "tag:important".to_string(),
                limit: 17,
                order: FilteredSearchOrder::Due as i32,
            }],
            ..Default::default()
        };
        let domain = FilteredDeckForUpdate {
            id: DeckId(7),
            human_name: "Filtered".to_string(),
            config: config.clone(),
            allow_empty: true,
        };
        let proto: anki_proto::decks::FilteredDeckForUpdate = domain.into();
        assert_eq!(proto.id, 7);
        assert_eq!(proto.name, "Filtered");
        assert_eq!(proto.config, Some(config));
        assert!(proto.allow_empty);
    }

    #[test]
    fn filtered_deck_for_update_from_proto_defaults_config_when_none() {
        let proto = anki_proto::decks::FilteredDeckForUpdate {
            id: 9,
            name: "NoConfig".to_string(),
            config: None,
            allow_empty: false,
        };
        let domain: FilteredDeckForUpdate = proto.into();
        assert_eq!(domain.id, DeckId(9));
        assert_eq!(domain.human_name, "NoConfig");
        assert_eq!(
            domain.config,
            FilteredDeck::default(),
            "missing config should fall back to the default"
        );
        assert!(!domain.allow_empty);
    }

    // ----------------------------------------------------------------------
    // Create & retrieve
    // ----------------------------------------------------------------------

    #[test]
    fn add_deck_is_retrievable_by_id_and_name() {
        let mut col = Collection::new();
        let mut proto = DecksService::new_deck(&mut col).unwrap();
        proto.name = "Created".to_string();
        let added = DecksService::add_deck(&mut col, proto).unwrap();
        assert!(added.id > 0);

        let by_id = DecksService::get_deck(&mut col, deck_id(added.id)).unwrap();
        assert_eq!(by_id.name, "Created");

        let by_name = DecksService::get_deck_id_by_name(
            &mut col,
            generic::String {
                val: "Created".to_string(),
            },
        )
        .unwrap();
        assert_eq!(by_name.did, added.id);
    }

    #[test]
    fn get_deck_with_unknown_id_errors() {
        let mut col = Collection::new();
        let err = DecksService::get_deck(&mut col, deck_id(999_999)).unwrap_err();
        assert!(
            matches!(err, AnkiError::NotFound { .. }),
            "unknown id should be a not-found error, got {err:?}"
        );
    }

    #[test]
    fn get_deck_id_by_name_with_unknown_name_errors() {
        let mut col = Collection::new();
        let err = DecksService::get_deck_id_by_name(
            &mut col,
            generic::String {
                val: "does-not-exist".to_string(),
            },
        )
        .unwrap_err();
        assert!(
            matches!(err, AnkiError::NotFound { .. }),
            "unknown name should be a not-found error, got {err:?}"
        );
    }

    // ----------------------------------------------------------------------
    // Update & rename
    // ----------------------------------------------------------------------

    #[test]
    fn update_deck_persists_common_and_kind_specific_changes() {
        let mut col = Collection::new();
        let deck = DeckAdder::new("BeforeUpdate").add(&mut col);
        let mut proto = DecksService::get_deck(&mut col, deck_id(deck.id.0)).unwrap();
        proto.name = "AfterUpdate".to_string();
        let common = proto.common.as_mut().unwrap();
        common.study_collapsed = false;
        common.new_studied = 7;
        let expected_kind =
            anki_proto::decks::deck::Kind::Normal(anki_proto::decks::deck::Normal {
                config_id: 1,
                description: "Updated description".to_string(),
                ..Default::default()
            });
        proto.kind = Some(expected_kind.clone());

        let _ = DecksService::update_deck(&mut col, proto).unwrap();

        let updated = DecksService::get_deck(&mut col, deck_id(deck.id.0)).unwrap();
        assert_eq!(updated.name, "AfterUpdate");
        let common = updated.common.unwrap();
        assert!(!common.study_collapsed);
        assert_eq!(common.new_studied, 7);
        assert_eq!(updated.kind, Some(expected_kind));
    }

    #[test]
    fn rename_deck_renames_the_deck_and_its_descendants() {
        let mut col = Collection::new();
        let parent = DeckAdder::new("Before").add(&mut col);
        let child = DeckAdder::new("Before::Child").add(&mut col);

        let _ = DecksService::rename_deck(
            &mut col,
            anki_proto::decks::RenameDeckRequest {
                deck_id: parent.id.0,
                new_name: "After".to_string(),
            },
        )
        .unwrap();

        assert_eq!(deck_id_by_name(&mut col, "After").unwrap(), parent.id.0);
        assert_eq!(
            deck_id_by_name(&mut col, "After::Child").unwrap(),
            child.id.0
        );
        let error = deck_id_by_name(&mut col, "Before").unwrap_err();
        assert!(matches!(error, AnkiError::NotFound { .. }));
    }

    // ----------------------------------------------------------------------
    // Delete & card effects
    // ----------------------------------------------------------------------

    #[test]
    fn remove_decks_deletes_the_deck_and_its_cards() {
        let mut col = Collection::new();
        let deck = DeckAdder::new("ToRemove").add(&mut col);
        let note = NoteAdder::basic(&mut col).deck(deck.id).add(&mut col);
        let cids = col.storage.card_ids_of_notes(&[note.id]).unwrap();
        assert_eq!(cids.len(), 1, "note should have produced one card");

        let out = DecksService::remove_decks(
            &mut col,
            anki_proto::decks::DeckIds {
                dids: vec![deck.id.0],
            },
        )
        .unwrap();
        assert_eq!(out.count, 1, "one card should have been removed");

        let error = DecksService::get_deck(&mut col, deck_id(deck.id.0)).unwrap_err();
        assert!(
            matches!(error, AnkiError::NotFound { .. }),
            "removed deck should produce a not-found error, got {error:?}"
        );
        assert!(
            col.storage.get_card(cids[0]).unwrap().is_none(),
            "card should be gone"
        );
    }

    // ----------------------------------------------------------------------
    // Reparent
    // ----------------------------------------------------------------------

    #[test]
    fn reparent_decks_moves_deck_under_new_parent() {
        let mut col = Collection::new();
        let parent = DeckAdder::new("Parent").add(&mut col);
        let child = DeckAdder::new("Child").add(&mut col);

        let _ = DecksService::reparent_decks(
            &mut col,
            anki_proto::decks::ReparentDecksRequest {
                deck_ids: vec![child.id.0],
                new_parent: parent.id.0,
            },
        )
        .unwrap();
        assert_eq!(
            deck_id_by_name(&mut col, "Parent::Child").unwrap(),
            child.id.0
        );
        let error = deck_id_by_name(&mut col, "Child").unwrap_err();
        assert!(matches!(error, AnkiError::NotFound { .. }));
    }

    #[test]
    fn reparent_decks_with_zero_parent_moves_deck_to_top_level() {
        let mut col = Collection::new();
        let child = DeckAdder::new("Parent::Child").add(&mut col);

        let _ = DecksService::reparent_decks(
            &mut col,
            anki_proto::decks::ReparentDecksRequest {
                deck_ids: vec![child.id.0],
                new_parent: 0,
            },
        )
        .unwrap();
        assert_eq!(deck_id_by_name(&mut col, "Child").unwrap(), child.id.0);
        let error = deck_id_by_name(&mut col, "Parent::Child").unwrap_err();
        assert!(matches!(error, AnkiError::NotFound { .. }));
    }

    // ----------------------------------------------------------------------
    // Names & tree
    // ----------------------------------------------------------------------

    #[test]
    fn get_deck_names_honours_include_filtered() {
        let mut col = Collection::new();
        DeckAdder::new("NormalDeck").add(&mut col);
        DeckAdder::new("FilteredDeck").filtered(true).add(&mut col);

        let normal_only = DecksService::get_deck_names(
            &mut col,
            anki_proto::decks::GetDeckNamesRequest {
                skip_empty_default: false,
                include_filtered: false,
            },
        )
        .unwrap();
        let names: Vec<&str> = normal_only
            .entries
            .iter()
            .map(|e| e.name.as_str())
            .collect();
        assert!(names.contains(&"NormalDeck"));
        assert!(!names.contains(&"FilteredDeck"), "filtered deck excluded");

        let all = DecksService::get_deck_names(
            &mut col,
            anki_proto::decks::GetDeckNamesRequest {
                skip_empty_default: false,
                include_filtered: true,
            },
        )
        .unwrap();
        let names: Vec<&str> = all.entries.iter().map(|e| e.name.as_str()).collect();
        assert!(names.contains(&"FilteredDeck"), "filtered deck included");
    }

    #[test]
    fn get_deck_names_skips_empty_default() {
        let mut col = Collection::new();
        let names = DecksService::get_deck_names(
            &mut col,
            anki_proto::decks::GetDeckNamesRequest {
                skip_empty_default: true,
                include_filtered: false,
            },
        )
        .unwrap();
        let names: Vec<&str> = names.entries.iter().map(|e| e.name.as_str()).collect();
        assert!(
            !names.contains(&"Default"),
            "empty default deck should be skipped"
        );
    }

    #[test]
    fn get_deck_names_keeps_nonempty_default_when_empty_default_is_skipped() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        let names = DecksService::get_deck_names(
            &mut col,
            anki_proto::decks::GetDeckNamesRequest {
                skip_empty_default: true,
                include_filtered: true,
            },
        )
        .unwrap();
        let names: Vec<&str> = names.entries.iter().map(|e| e.name.as_str()).collect();

        assert!(
            names.contains(&"Default"),
            "nonempty default deck should not be skipped"
        );
    }

    #[test]
    fn get_deck_and_child_names_includes_children() {
        let mut col = Collection::new();
        let parent = DeckAdder::new("P").add(&mut col);
        let child = DeckAdder::new("P::C").add(&mut col);
        DeckAdder::new("Unrelated").add(&mut col);

        let names = DecksService::get_deck_and_child_names(&mut col, deck_id(parent.id.0)).unwrap();
        let entries: Vec<_> = names
            .entries
            .into_iter()
            .map(|entry| (entry.id, entry.name))
            .collect();

        assert_eq!(entries.len(), 2, "unrelated decks should be excluded");
        assert!(entries.contains(&(parent.id.0, "P".to_string())));
        assert!(entries.contains(&(child.id.0, "P::C".to_string())));
    }

    #[test]
    fn deck_tree_with_now_zero_omits_counts() {
        let mut col = Collection::new();
        // a new card that would be counted if counts were requested
        NoteAdder::basic(&mut col).add(&mut col);

        let root = DecksService::deck_tree(&mut col, anki_proto::decks::DeckTreeRequest { now: 0 })
            .unwrap();
        let default = root
            .children
            .iter()
            .find(|c| c.name == "Default")
            .expect("tree should contain the Default deck");
        assert_eq!(
            default.new_count, 0,
            "counts should be omitted when now is 0"
        );
    }

    #[test]
    fn deck_tree_with_now_set_includes_counts() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);

        let root = DecksService::deck_tree(
            &mut col,
            anki_proto::decks::DeckTreeRequest {
                now: TimestampSecs::now().0,
            },
        )
        .unwrap();
        let default = root
            .children
            .iter()
            .find(|c| c.name == "Default")
            .expect("tree should contain the Default deck");
        assert_eq!(
            default.new_count, 1,
            "the single new card should be counted when now is set"
        );
    }

    #[test]
    fn deck_tree_legacy_contains_default_deck() {
        let mut col = Collection::new();
        let json = DecksService::deck_tree_legacy(&mut col).unwrap();
        // LegacyDueCounts serializes as a tuple: [name, deck_id, review, learn,
        // new, children]; children (index 5) is an array of the same shape.
        let value: serde_json::Value = serde_json::from_slice(&json.json).unwrap();
        let names: Vec<&str> = value[5]
            .as_array()
            .expect("legacy tree should have a children array at index 5")
            .iter()
            .filter_map(|child| child[0].as_str())
            .collect();
        assert!(
            names.contains(&"Default"),
            "legacy tree should list the Default deck, got {names:?}"
        );
    }

    // ----------------------------------------------------------------------
    // Current & collapsed state
    // ----------------------------------------------------------------------

    #[test]
    fn set_and_get_current_deck_round_trip() {
        let mut col = Collection::new();
        let deck = DeckAdder::new("Current").add(&mut col);
        let _ = DecksService::set_current_deck(&mut col, deck_id(deck.id.0)).unwrap();
        let current = DecksService::get_current_deck(&mut col).unwrap();
        assert_eq!(current.id, deck.id.0);
    }

    #[test]
    fn set_deck_collapsed_changes_only_the_requested_scope() {
        let mut col = Collection::new();
        let deck = DeckAdder::new("Collapse").add(&mut col);
        let _ = DecksService::set_deck_collapsed(
            &mut col,
            anki_proto::decks::SetDeckCollapsedRequest {
                deck_id: deck.id.0,
                collapsed: false,
                scope: anki_proto::decks::set_deck_collapsed_request::Scope::Reviewer as i32,
            },
        )
        .unwrap();
        let fetched = DecksService::get_deck(&mut col, deck_id(deck.id.0)).unwrap();
        let common = fetched.common.unwrap();
        assert!(!common.study_collapsed);
        assert!(
            common.browser_collapsed,
            "changing reviewer collapse state should not affect browser state"
        );
    }

    // ----------------------------------------------------------------------
    // Legacy JSON paths
    // ----------------------------------------------------------------------

    #[test]
    fn new_deck_legacy_reflects_filtered_flag() {
        let mut col = Collection::new();
        let normal = DecksService::new_deck_legacy(&mut col, generic::Bool { val: false }).unwrap();
        let normal: serde_json::Value = serde_json::from_slice(&normal.json).unwrap();
        assert_eq!(normal["dyn"], 0);

        let filtered =
            DecksService::new_deck_legacy(&mut col, generic::Bool { val: true }).unwrap();
        let filtered: serde_json::Value = serde_json::from_slice(&filtered.json).unwrap();
        assert_eq!(filtered["dyn"], 1);
    }

    #[test]
    fn add_deck_legacy_round_trips_through_get_deck_legacy() {
        let mut col = Collection::new();
        let json = DecksService::new_deck_legacy(&mut col, generic::Bool { val: false }).unwrap();
        let mut value: serde_json::Value = serde_json::from_slice(&json.json).unwrap();
        value["name"] = serde_json::json!("LegacyDeck");

        let added = DecksService::add_deck_legacy(
            &mut col,
            generic::Json {
                json: serde_json::to_vec(&value).unwrap(),
            },
        )
        .unwrap();
        assert!(added.id > 0);

        let fetched = DecksService::get_deck_legacy(&mut col, deck_id(added.id)).unwrap();
        let fetched: serde_json::Value = serde_json::from_slice(&fetched.json).unwrap();
        assert_eq!(fetched["name"], "LegacyDeck");
    }

    #[test]
    fn legacy_deck_mutations_reject_invalid_json() {
        let mut col = Collection::new();
        let invalid_json = b"not valid json".to_vec();
        let errors = [
            DecksService::add_deck_legacy(
                &mut col,
                generic::Json {
                    json: invalid_json.clone(),
                },
            )
            .unwrap_err(),
            DecksService::update_deck_legacy(
                &mut col,
                generic::Json {
                    json: invalid_json.clone(),
                },
            )
            .unwrap_err(),
            DecksService::add_or_update_deck_legacy(
                &mut col,
                anki_proto::decks::AddOrUpdateDeckLegacyRequest {
                    deck: invalid_json,
                    preserve_usn_and_mtime: false,
                },
            )
            .unwrap_err(),
        ];

        for error in errors {
            assert!(
                matches!(error, AnkiError::JsonError { .. }),
                "invalid legacy JSON should produce a JSON error, got {error:?}"
            );
        }
    }

    #[test]
    fn get_all_decks_legacy_includes_default() {
        let mut col = Collection::new();
        let json = DecksService::get_all_decks_legacy(&mut col).unwrap();
        let value: serde_json::Value = serde_json::from_slice(&json.json).unwrap();
        assert_eq!(value["1"]["name"], "Default");
    }

    #[test]
    fn update_deck_legacy_persists_changes() {
        let mut col = Collection::new();
        let deck = DeckAdder::new("LegacyUpdate").add(&mut col);
        let fetched = DecksService::get_deck_legacy(&mut col, deck_id(deck.id.0)).unwrap();
        let mut value: serde_json::Value = serde_json::from_slice(&fetched.json).unwrap();
        value["name"] = serde_json::json!("LegacyRenamed");

        let _ = DecksService::update_deck_legacy(
            &mut col,
            generic::Json {
                json: serde_json::to_vec(&value).unwrap(),
            },
        )
        .unwrap();

        let after = DecksService::get_deck(&mut col, deck_id(deck.id.0)).unwrap();
        assert_eq!(after.name, "LegacyRenamed");
    }

    #[test]
    fn add_or_update_deck_legacy_adds_when_id_is_zero() {
        let mut col = Collection::new();
        let json = DecksService::new_deck_legacy(&mut col, generic::Bool { val: false }).unwrap();
        let mut value: serde_json::Value = serde_json::from_slice(&json.json).unwrap();
        value["name"] = serde_json::json!("AddOrUpdateLegacy");

        // branch: !preserve_usn_and_mtime -> add_or_update_deck
        let added = DecksService::add_or_update_deck_legacy(
            &mut col,
            anki_proto::decks::AddOrUpdateDeckLegacyRequest {
                deck: serde_json::to_vec(&value).unwrap(),
                preserve_usn_and_mtime: false,
            },
        )
        .unwrap();
        assert!(added.did > 0);
        assert_eq!(
            DecksService::get_deck(&mut col, deck_id(added.did))
                .unwrap()
                .name,
            "AddOrUpdateLegacy"
        );
    }

    #[test]
    fn add_or_update_deck_legacy_preserves_usn_and_mtime_when_requested() {
        let mut col = Collection::new();
        let deck = DeckAdder::new("PreserveMetadata").add(&mut col);
        let fetched = DecksService::get_deck_legacy(&mut col, deck_id(deck.id.0)).unwrap();
        let mut value: serde_json::Value = serde_json::from_slice(&fetched.json).unwrap();
        value["name"] = serde_json::json!("PreservedName");
        value["mod"] = serde_json::json!(123);
        value["usn"] = serde_json::json!(456);

        let updated = DecksService::add_or_update_deck_legacy(
            &mut col,
            anki_proto::decks::AddOrUpdateDeckLegacyRequest {
                deck: serde_json::to_vec(&value).unwrap(),
                preserve_usn_and_mtime: true,
            },
        )
        .unwrap();
        assert_eq!(updated.did, deck.id.0);

        let fetched = DecksService::get_deck_legacy(&mut col, deck_id(deck.id.0)).unwrap();
        let value: serde_json::Value = serde_json::from_slice(&fetched.json).unwrap();
        assert_eq!(value["name"], "PreservedName");
        assert_eq!(value["mod"], 123);
        assert_eq!(value["usn"], 456);
    }

    // ----------------------------------------------------------------------
    // Filtered decks
    // ----------------------------------------------------------------------

    #[test]
    fn filtered_deck_order_labels_match_the_order_enum() {
        let mut col = Collection::new();
        let expected: Vec<String> = vec![
            col.tr.decks_oldest_seen_first().into(),
            col.tr.decks_random().into(),
            col.tr.decks_increasing_intervals().into(),
            col.tr.decks_decreasing_intervals().into(),
            col.tr.decks_most_lapses().into(),
            col.tr.decks_order_added().into(),
            col.tr.decks_order_due().into(),
            col.tr.decks_latest_added_first().into(),
            col.tr
                .deck_config_sort_order_retrievability_ascending()
                .into(),
            col.tr
                .deck_config_sort_order_retrievability_descending()
                .into(),
            col.tr.decks_relative_overdueness().into(),
        ];

        let labels = DecksService::filtered_deck_order_labels(&mut col).unwrap();

        assert_eq!(labels.vals, expected);
    }

    #[test]
    fn get_or_create_filtered_deck_returns_an_unsaved_deck_for_zero_id() {
        let mut col = Collection::new();
        let names_before = col.storage.get_all_deck_names().unwrap();

        let created = DecksService::get_or_create_filtered_deck(&mut col, deck_id(0)).unwrap();

        assert_eq!(created.id, 0);
        assert!(created.config.is_some());
        assert_eq!(
            col.storage.get_all_deck_names().unwrap(),
            names_before,
            "requesting a new filtered deck should not persist it"
        );
    }

    #[test]
    fn get_or_create_filtered_deck_errors_for_unknown_id() {
        let mut col = Collection::new();
        let err =
            DecksService::get_or_create_filtered_deck(&mut col, deck_id(999_999)).unwrap_err();
        assert!(
            matches!(err, AnkiError::NotFound { .. }),
            "unknown id should be a not-found error, got {err:?}"
        );
    }

    #[test]
    fn add_or_update_filtered_deck_builds_a_deck() {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let card_id = col.storage.card_ids_of_notes(&[note.id]).unwrap()[0];

        let mut template = DecksService::get_or_create_filtered_deck(&mut col, deck_id(0)).unwrap();
        template.name = "MyFiltered".to_string();
        let config = template
            .config
            .as_mut()
            .expect("a newly created filtered deck should carry a config");
        config.search_terms = vec![FilteredSearchTerm {
            search: "deck:Default".to_string(),
            limit: 100,
            order: FilteredSearchOrder::Random as i32,
        }];

        let out = DecksService::add_or_update_filtered_deck(&mut col, template).unwrap();
        assert_eq!(deck_id_by_name(&mut col, "MyFiltered").unwrap(), out.id);

        let card = col.storage.get_card(card_id).unwrap().unwrap();
        assert_eq!(card.deck_id, DeckId(out.id));
        assert_eq!(card.original_deck_id, DeckId(1));
    }
}
