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
        self.get_deck_id(&input.val).and_then(|d| {
            d.or_not_found(input.val)
                .map(|d| anki_proto::decks::DeckId { did: d.0 })
        })
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
