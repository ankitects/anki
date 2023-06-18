// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::TryFrom;

pub(super) use anki_proto::decks::decks_service::Service as DecksService;
use anki_proto::generic;

use super::Backend;
use crate::decks::filtered::search_order_labels;
use crate::decks::DeckSchema11;
use crate::prelude::*;
use crate::scheduler::filtered::FilteredDeckForUpdate;

impl DecksService for Backend {
    type Error = AnkiError;

    fn new_deck(&self) -> Result<anki_proto::decks::Deck> {
        Ok(Deck::new_normal().into())
    }

    fn add_deck(
        &self,
        deck: anki_proto::decks::Deck,
    ) -> Result<anki_proto::collection::OpChangesWithId> {
        let mut deck: Deck = deck.try_into()?;
        self.with_col(|col| Ok(col.add_deck(&mut deck)?.map(|_| deck.id.0).into()))
    }

    fn add_deck_legacy(
        &self,
        input: generic::Json,
    ) -> Result<anki_proto::collection::OpChangesWithId> {
        let schema11: DeckSchema11 = serde_json::from_slice(&input.json)?;
        let mut deck: Deck = schema11.into();
        self.with_col(|col| {
            let output = col.add_deck(&mut deck)?;
            Ok(output.map(|_| deck.id.0).into())
        })
    }

    fn add_or_update_deck_legacy(
        &self,
        input: anki_proto::decks::AddOrUpdateDeckLegacyRequest,
    ) -> Result<anki_proto::decks::DeckId> {
        self.with_col(|col| {
            let schema11: DeckSchema11 = serde_json::from_slice(&input.deck)?;
            let mut deck: Deck = schema11.into();
            if input.preserve_usn_and_mtime {
                col.transact_no_undo(|col| {
                    let usn = col.usn()?;
                    col.add_or_update_single_deck_with_existing_id(&mut deck, usn)
                })?;
            } else {
                col.add_or_update_deck(&mut deck)?;
            }
            Ok(anki_proto::decks::DeckId { did: deck.id.0 })
        })
    }

    fn deck_tree(
        &self,
        input: anki_proto::decks::DeckTreeRequest,
    ) -> Result<anki_proto::decks::DeckTreeNode> {
        self.with_col(|col| {
            let now = if input.now == 0 {
                None
            } else {
                Some(TimestampSecs(input.now))
            };
            col.deck_tree(now)
        })
    }

    fn deck_tree_legacy(&self) -> Result<generic::Json> {
        self.with_col(|col| {
            let tree = col.legacy_deck_tree()?;
            serde_json::to_vec(&tree)
                .map_err(Into::into)
                .map(Into::into)
        })
    }

    fn get_all_decks_legacy(&self) -> Result<generic::Json> {
        self.with_col(|col| {
            let decks = col.storage.get_all_decks_as_schema11()?;
            serde_json::to_vec(&decks).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_deck_id_by_name(&self, input: generic::String) -> Result<anki_proto::decks::DeckId> {
        self.with_col(|col| {
            col.get_deck_id(&input.val).and_then(|d| {
                d.or_not_found(input.val)
                    .map(|d| anki_proto::decks::DeckId { did: d.0 })
            })
        })
    }

    fn get_deck(&self, input: anki_proto::decks::DeckId) -> Result<anki_proto::decks::Deck> {
        let did = input.into();
        self.with_col(|col| Ok(col.storage.get_deck(did)?.or_not_found(did)?.into()))
    }

    fn update_deck(
        &self,
        input: anki_proto::decks::Deck,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| {
            let mut deck = Deck::try_from(input)?;
            col.update_deck(&mut deck).map(Into::into)
        })
    }

    fn update_deck_legacy(
        &self,
        input: generic::Json,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| {
            let deck: DeckSchema11 = serde_json::from_slice(&input.json)?;
            let mut deck = deck.into();
            col.update_deck(&mut deck).map(Into::into)
        })
    }

    fn get_deck_legacy(&self, input: anki_proto::decks::DeckId) -> Result<generic::Json> {
        let did = input.into();
        self.with_col(|col| {
            let deck: DeckSchema11 = col.storage.get_deck(did)?.or_not_found(did)?.into();
            serde_json::to_vec(&deck)
                .map_err(Into::into)
                .map(Into::into)
        })
    }

    fn get_deck_names(
        &self,
        input: anki_proto::decks::GetDeckNamesRequest,
    ) -> Result<anki_proto::decks::DeckNames> {
        self.with_col(|col| {
            let names = if input.include_filtered {
                col.get_all_deck_names(input.skip_empty_default)?
            } else {
                col.get_all_normal_deck_names()?
            };
            Ok(deck_names_to_proto(names))
        })
    }

    fn get_deck_and_child_names(
        &self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::decks::DeckNames> {
        self.with_col(|col| {
            col.get_deck_and_child_names(input.did.into())
                .map(deck_names_to_proto)
        })
    }

    fn new_deck_legacy(&self, input: generic::Bool) -> Result<generic::Json> {
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
        &self,
        input: anki_proto::decks::DeckIds,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        self.with_col(|col| {
            col.remove_decks_and_child_decks(
                &input.dids.into_iter().map(DeckId).collect::<Vec<_>>(),
            )
        })
        .map(Into::into)
    }

    fn reparent_decks(
        &self,
        input: anki_proto::decks::ReparentDecksRequest,
    ) -> Result<anki_proto::collection::OpChangesWithCount> {
        let deck_ids: Vec<_> = input.deck_ids.into_iter().map(Into::into).collect();
        let new_parent = if input.new_parent == 0 {
            None
        } else {
            Some(input.new_parent.into())
        };
        self.with_col(|col| col.reparent_decks(&deck_ids, new_parent))
            .map(Into::into)
    }

    fn rename_deck(
        &self,
        input: anki_proto::decks::RenameDeckRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.rename_deck(input.deck_id.into(), &input.new_name))
            .map(Into::into)
    }

    fn get_or_create_filtered_deck(
        &self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::decks::FilteredDeckForUpdate> {
        self.with_col(|col| col.get_or_create_filtered_deck(input.into()))
            .map(Into::into)
    }

    fn add_or_update_filtered_deck(
        &self,
        input: anki_proto::decks::FilteredDeckForUpdate,
    ) -> Result<anki_proto::collection::OpChangesWithId> {
        self.with_col(|col| col.add_or_update_filtered_deck(input.into()))
            .map(|out| out.map(i64::from))
            .map(Into::into)
    }

    fn filtered_deck_order_labels(&self) -> Result<generic::StringList> {
        Ok(search_order_labels(&self.tr).into())
    }

    fn set_deck_collapsed(
        &self,
        input: anki_proto::decks::SetDeckCollapsedRequest,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| {
            col.set_deck_collapsed(input.deck_id.into(), input.collapsed, input.scope())
        })
        .map(Into::into)
    }

    fn set_current_deck(
        &self,
        input: anki_proto::decks::DeckId,
    ) -> Result<anki_proto::collection::OpChanges> {
        self.with_col(|col| col.set_current_deck(input.did.into()))
            .map(Into::into)
    }

    fn get_current_deck(&self) -> Result<anki_proto::decks::Deck> {
        self.with_col(|col| col.get_current_deck())
            .map(|deck| (*deck).clone().into())
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
        }
    }
}

impl From<anki_proto::decks::FilteredDeckForUpdate> for FilteredDeckForUpdate {
    fn from(deck: anki_proto::decks::FilteredDeckForUpdate) -> Self {
        FilteredDeckForUpdate {
            id: deck.id.into(),
            human_name: deck.name,
            config: deck.config.unwrap_or_default(),
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

    fn try_from(d: anki_proto::decks::Deck) -> Result<Self, Self::Error> {
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
