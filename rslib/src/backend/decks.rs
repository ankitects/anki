// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::TryFrom;

use super::Backend;
use crate::decks::DeckSchema11;
use crate::decks::FilteredSearchOrder;
use crate::pb;
pub(super) use crate::pb::decks::decks_service::Service as DecksService;
use crate::prelude::*;
use crate::scheduler::filtered::FilteredDeckForUpdate;

impl DecksService for Backend {
    fn new_deck(&self, _input: pb::generic::Empty) -> Result<pb::decks::Deck> {
        Ok(Deck::new_normal().into())
    }

    fn add_deck(&self, deck: pb::decks::Deck) -> Result<pb::collection::OpChangesWithId> {
        let mut deck: Deck = deck.try_into()?;
        self.with_col(|col| Ok(col.add_deck(&mut deck)?.map(|_| deck.id.0).into()))
    }

    fn add_deck_legacy(&self, input: pb::generic::Json) -> Result<pb::collection::OpChangesWithId> {
        let schema11: DeckSchema11 = serde_json::from_slice(&input.json)?;
        let mut deck: Deck = schema11.into();
        self.with_col(|col| {
            let output = col.add_deck(&mut deck)?;
            Ok(output.map(|_| deck.id.0).into())
        })
    }

    fn add_or_update_deck_legacy(
        &self,
        input: pb::decks::AddOrUpdateDeckLegacyRequest,
    ) -> Result<pb::decks::DeckId> {
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
            Ok(pb::decks::DeckId { did: deck.id.0 })
        })
    }

    fn deck_tree(&self, input: pb::decks::DeckTreeRequest) -> Result<pb::decks::DeckTreeNode> {
        self.with_col(|col| {
            let now = if input.now == 0 {
                None
            } else {
                Some(TimestampSecs(input.now))
            };
            col.deck_tree(now)
        })
    }

    fn deck_tree_legacy(&self, _input: pb::generic::Empty) -> Result<pb::generic::Json> {
        self.with_col(|col| {
            let tree = col.legacy_deck_tree()?;
            serde_json::to_vec(&tree)
                .map_err(Into::into)
                .map(Into::into)
        })
    }

    fn get_all_decks_legacy(&self, _input: pb::generic::Empty) -> Result<pb::generic::Json> {
        self.with_col(|col| {
            let decks = col.storage.get_all_decks_as_schema11()?;
            serde_json::to_vec(&decks).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_deck_id_by_name(&self, input: pb::generic::String) -> Result<pb::decks::DeckId> {
        self.with_col(|col| {
            col.get_deck_id(&input.val).and_then(|d| {
                d.or_not_found(input.val)
                    .map(|d| pb::decks::DeckId { did: d.0 })
            })
        })
    }

    fn get_deck(&self, input: pb::decks::DeckId) -> Result<pb::decks::Deck> {
        let did = input.into();
        self.with_col(|col| Ok(col.storage.get_deck(did)?.or_not_found(did)?.into()))
    }

    fn update_deck(&self, input: pb::decks::Deck) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            let mut deck = Deck::try_from(input)?;
            col.update_deck(&mut deck).map(Into::into)
        })
    }

    fn update_deck_legacy(&self, input: pb::generic::Json) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            let deck: DeckSchema11 = serde_json::from_slice(&input.json)?;
            let mut deck = deck.into();
            col.update_deck(&mut deck).map(Into::into)
        })
    }

    fn get_deck_legacy(&self, input: pb::decks::DeckId) -> Result<pb::generic::Json> {
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
        input: pb::decks::GetDeckNamesRequest,
    ) -> Result<pb::decks::DeckNames> {
        self.with_col(|col| {
            let names = if input.include_filtered {
                col.get_all_deck_names(input.skip_empty_default)?
            } else {
                col.get_all_normal_deck_names()?
            };
            Ok(names.into())
        })
    }

    fn get_deck_and_child_names(&self, input: pb::decks::DeckId) -> Result<pb::decks::DeckNames> {
        self.with_col(|col| {
            col.get_deck_and_child_names(input.did.into())
                .map(Into::into)
        })
    }

    fn new_deck_legacy(&self, input: pb::generic::Bool) -> Result<pb::generic::Json> {
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
        input: pb::decks::DeckIds,
    ) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| col.remove_decks_and_child_decks(&Into::<Vec<DeckId>>::into(input)))
            .map(Into::into)
    }

    fn reparent_decks(
        &self,
        input: pb::decks::ReparentDecksRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
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
        input: pb::decks::RenameDeckRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| col.rename_deck(input.deck_id.into(), &input.new_name))
            .map(Into::into)
    }

    fn get_or_create_filtered_deck(
        &self,
        input: pb::decks::DeckId,
    ) -> Result<pb::decks::FilteredDeckForUpdate> {
        self.with_col(|col| col.get_or_create_filtered_deck(input.into()))
            .map(Into::into)
    }

    fn add_or_update_filtered_deck(
        &self,
        input: pb::decks::FilteredDeckForUpdate,
    ) -> Result<pb::collection::OpChangesWithId> {
        self.with_col(|col| col.add_or_update_filtered_deck(input.into()))
            .map(|out| out.map(i64::from))
            .map(Into::into)
    }

    fn filtered_deck_order_labels(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::generic::StringList> {
        Ok(FilteredSearchOrder::labels(&self.tr).into())
    }

    fn set_deck_collapsed(
        &self,
        input: pb::decks::SetDeckCollapsedRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            col.set_deck_collapsed(input.deck_id.into(), input.collapsed, input.scope())
        })
        .map(Into::into)
    }

    fn set_current_deck(&self, input: pb::decks::DeckId) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| col.set_current_deck(input.did.into()))
            .map(Into::into)
    }

    fn get_current_deck(&self, _input: pb::generic::Empty) -> Result<pb::decks::Deck> {
        self.with_col(|col| col.get_current_deck())
            .map(|deck| (*deck).clone().into())
    }
}

impl From<pb::decks::DeckId> for DeckId {
    fn from(did: pb::decks::DeckId) -> Self {
        DeckId(did.did)
    }
}

impl From<pb::decks::DeckIds> for Vec<DeckId> {
    fn from(dids: pb::decks::DeckIds) -> Self {
        dids.dids.into_iter().map(DeckId).collect()
    }
}

impl From<DeckId> for pb::decks::DeckId {
    fn from(did: DeckId) -> Self {
        pb::decks::DeckId { did: did.0 }
    }
}

impl From<FilteredDeckForUpdate> for pb::decks::FilteredDeckForUpdate {
    fn from(deck: FilteredDeckForUpdate) -> Self {
        pb::decks::FilteredDeckForUpdate {
            id: deck.id.into(),
            name: deck.human_name,
            config: Some(deck.config),
        }
    }
}

impl From<pb::decks::FilteredDeckForUpdate> for FilteredDeckForUpdate {
    fn from(deck: pb::decks::FilteredDeckForUpdate) -> Self {
        FilteredDeckForUpdate {
            id: deck.id.into(),
            human_name: deck.name,
            config: deck.config.unwrap_or_default(),
        }
    }
}

impl From<Deck> for pb::decks::Deck {
    fn from(d: Deck) -> Self {
        pb::decks::Deck {
            id: d.id.0,
            name: d.name.human_name(),
            mtime_secs: d.mtime_secs.0,
            usn: d.usn.0,
            common: Some(d.common),
            kind: Some(d.kind.into()),
        }
    }
}

impl TryFrom<pb::decks::Deck> for Deck {
    type Error = AnkiError;

    fn try_from(d: pb::decks::Deck) -> Result<Self, Self::Error> {
        Ok(Deck {
            id: DeckId(d.id),
            name: NativeDeckName::from_human_name(&d.name),
            mtime_secs: TimestampSecs(d.mtime_secs),
            usn: Usn(d.usn),
            common: d.common.unwrap_or_default(),
            kind: d.kind.or_invalid("missing kind")?.into(),
        })
    }
}

impl From<DeckKind> for pb::decks::deck::Kind {
    fn from(k: DeckKind) -> Self {
        match k {
            DeckKind::Normal(n) => pb::decks::deck::Kind::Normal(n),
            DeckKind::Filtered(f) => pb::decks::deck::Kind::Filtered(f),
        }
    }
}

impl From<pb::decks::deck::Kind> for DeckKind {
    fn from(kind: pb::decks::deck::Kind) -> Self {
        match kind {
            pb::decks::deck::Kind::Normal(normal) => DeckKind::Normal(normal),
            pb::decks::deck::Kind::Filtered(filtered) => DeckKind::Filtered(filtered),
        }
    }
}

impl From<(DeckId, String)> for pb::decks::DeckNameId {
    fn from(id_name: (DeckId, String)) -> Self {
        pb::decks::DeckNameId {
            id: id_name.0 .0,
            name: id_name.1,
        }
    }
}

impl From<Vec<(DeckId, String)>> for pb::decks::DeckNames {
    fn from(id_names: Vec<(DeckId, String)>) -> Self {
        pb::decks::DeckNames {
            entries: id_names.into_iter().map(Into::into).collect(),
        }
    }
}

// fn new_deck(&self, input: pb::generic::Bool) -> Result<pb::decks::Deck> {
//     let deck = if input.val {
//         Deck::new_filtered()
//     } else {
//         Deck::new_normal()
//     };
//     Ok(deck.into())
// }
