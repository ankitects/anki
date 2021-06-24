// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::TryFrom;

use super::Backend;
pub(super) use crate::backend_proto::decks_service::Service as DecksService;
use crate::{
    backend_proto::{self as pb},
    decks::{DeckSchema11, FilteredSearchOrder},
    prelude::*,
    scheduler::filtered::FilteredDeckForUpdate,
};

impl DecksService for Backend {
    fn add_deck_legacy(&self, input: pb::Json) -> Result<pb::OpChangesWithId> {
        let schema11: DeckSchema11 = serde_json::from_slice(&input.json)?;
        let mut deck: Deck = schema11.into();
        self.with_col(|col| {
            let output = col.add_deck(&mut deck)?;
            Ok(output.map(|_| deck.id.0).into())
        })
    }

    fn add_or_update_deck_legacy(
        &self,
        input: pb::AddOrUpdateDeckLegacyRequest,
    ) -> Result<pb::DeckId> {
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
            Ok(pb::DeckId { did: deck.id.0 })
        })
    }

    fn deck_tree(&self, input: pb::DeckTreeRequest) -> Result<pb::DeckTreeNode> {
        let lim = if input.top_deck_id > 0 {
            Some(DeckId(input.top_deck_id))
        } else {
            None
        };
        self.with_col(|col| {
            let now = if input.now == 0 {
                None
            } else {
                Some(TimestampSecs(input.now))
            };
            col.deck_tree(now, lim)
        })
    }

    fn deck_tree_legacy(&self, _input: pb::Empty) -> Result<pb::Json> {
        self.with_col(|col| {
            let tree = col.legacy_deck_tree()?;
            serde_json::to_vec(&tree)
                .map_err(Into::into)
                .map(Into::into)
        })
    }

    fn get_all_decks_legacy(&self, _input: pb::Empty) -> Result<pb::Json> {
        self.with_col(|col| {
            let decks = col.storage.get_all_decks_as_schema11()?;
            serde_json::to_vec(&decks).map_err(Into::into)
        })
        .map(Into::into)
    }

    fn get_deck_id_by_name(&self, input: pb::String) -> Result<pb::DeckId> {
        self.with_col(|col| {
            col.get_deck_id(&input.val).and_then(|d| {
                d.ok_or(AnkiError::NotFound)
                    .map(|d| pb::DeckId { did: d.0 })
            })
        })
    }

    fn get_deck(&self, input: pb::DeckId) -> Result<pb::Deck> {
        self.with_col(|col| {
            Ok(col
                .storage
                .get_deck(input.into())?
                .ok_or(AnkiError::NotFound)?
                .into())
        })
    }

    fn update_deck(&self, input: pb::Deck) -> Result<pb::OpChanges> {
        self.with_col(|col| {
            let mut deck = Deck::try_from(input)?;
            col.update_deck(&mut deck).map(Into::into)
        })
    }

    fn update_deck_legacy(&self, input: pb::Json) -> Result<pb::OpChanges> {
        self.with_col(|col| {
            let deck: DeckSchema11 = serde_json::from_slice(&input.json)?;
            let mut deck = deck.into();
            col.update_deck(&mut deck).map(Into::into)
        })
    }

    fn get_deck_legacy(&self, input: pb::DeckId) -> Result<pb::Json> {
        self.with_col(|col| {
            let deck: DeckSchema11 = col
                .storage
                .get_deck(input.into())?
                .ok_or(AnkiError::NotFound)?
                .into();
            serde_json::to_vec(&deck)
                .map_err(Into::into)
                .map(Into::into)
        })
    }

    fn get_deck_names(&self, input: pb::GetDeckNamesRequest) -> Result<pb::DeckNames> {
        self.with_col(|col| {
            let names = if input.include_filtered {
                col.get_all_deck_names(input.skip_empty_default)?
            } else {
                col.get_all_normal_deck_names()?
            };
            Ok(pb::DeckNames {
                entries: names
                    .into_iter()
                    .map(|(id, name)| pb::DeckNameId { id: id.0, name })
                    .collect(),
            })
        })
    }

    fn new_deck_legacy(&self, input: pb::Bool) -> Result<pb::Json> {
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

    fn remove_decks(&self, input: pb::DeckIds) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| col.remove_decks_and_child_decks(&Into::<Vec<DeckId>>::into(input)))
            .map(Into::into)
    }

    fn reparent_decks(&self, input: pb::ReparentDecksRequest) -> Result<pb::OpChangesWithCount> {
        let deck_ids: Vec<_> = input.deck_ids.into_iter().map(Into::into).collect();
        let new_parent = if input.new_parent == 0 {
            None
        } else {
            Some(input.new_parent.into())
        };
        self.with_col(|col| col.reparent_decks(&deck_ids, new_parent))
            .map(Into::into)
    }

    fn rename_deck(&self, input: pb::RenameDeckRequest) -> Result<pb::OpChanges> {
        self.with_col(|col| col.rename_deck(input.deck_id.into(), &input.new_name))
            .map(Into::into)
    }

    fn get_or_create_filtered_deck(&self, input: pb::DeckId) -> Result<pb::FilteredDeckForUpdate> {
        self.with_col(|col| col.get_or_create_filtered_deck(input.into()))
            .map(Into::into)
    }

    fn add_or_update_filtered_deck(
        &self,
        input: pb::FilteredDeckForUpdate,
    ) -> Result<pb::OpChangesWithId> {
        self.with_col(|col| col.add_or_update_filtered_deck(input.into()))
            .map(|out| out.map(i64::from))
            .map(Into::into)
    }

    fn filtered_deck_order_labels(&self, _input: pb::Empty) -> Result<pb::StringList> {
        Ok(FilteredSearchOrder::labels(&self.tr).into())
    }

    fn set_deck_collapsed(&self, input: pb::SetDeckCollapsedRequest) -> Result<pb::OpChanges> {
        self.with_col(|col| {
            col.set_deck_collapsed(input.deck_id.into(), input.collapsed, input.scope())
        })
        .map(Into::into)
    }

    fn set_current_deck(&self, input: pb::DeckId) -> Result<pb::OpChanges> {
        self.with_col(|col| col.set_current_deck(input.did.into()))
            .map(Into::into)
    }

    fn get_current_deck(&self, _input: pb::Empty) -> Result<pb::Deck> {
        self.with_col(|col| col.get_current_deck())
            .map(|deck| (*deck).clone().into())
    }
}

impl From<pb::DeckId> for DeckId {
    fn from(did: pb::DeckId) -> Self {
        DeckId(did.did)
    }
}

impl From<pb::DeckIds> for Vec<DeckId> {
    fn from(dids: pb::DeckIds) -> Self {
        dids.dids.into_iter().map(DeckId).collect()
    }
}

impl From<DeckId> for pb::DeckId {
    fn from(did: DeckId) -> Self {
        pb::DeckId { did: did.0 }
    }
}

impl From<FilteredDeckForUpdate> for pb::FilteredDeckForUpdate {
    fn from(deck: FilteredDeckForUpdate) -> Self {
        pb::FilteredDeckForUpdate {
            id: deck.id.into(),
            name: deck.human_name,
            config: Some(deck.config),
        }
    }
}

impl From<pb::FilteredDeckForUpdate> for FilteredDeckForUpdate {
    fn from(deck: pb::FilteredDeckForUpdate) -> Self {
        FilteredDeckForUpdate {
            id: deck.id.into(),
            human_name: deck.name,
            config: deck.config.unwrap_or_default(),
        }
    }
}

impl From<Deck> for pb::Deck {
    fn from(d: Deck) -> Self {
        pb::Deck {
            id: d.id.0,
            name: d.name.human_name(),
            mtime_secs: d.mtime_secs.0,
            usn: d.usn.0,
            common: Some(d.common),
            kind: Some(d.kind.into()),
        }
    }
}

impl TryFrom<pb::Deck> for Deck {
    type Error = AnkiError;

    fn try_from(d: pb::Deck) -> Result<Self, Self::Error> {
        Ok(Deck {
            id: DeckId(d.id),
            name: NativeDeckName::from_human_name(&d.name),
            mtime_secs: TimestampSecs(d.mtime_secs),
            usn: Usn(d.usn),
            common: d.common.unwrap_or_default(),
            kind: d
                .kind
                .ok_or_else(|| AnkiError::invalid_input("missing kind"))?
                .into(),
        })
    }
}

impl From<DeckKind> for pb::deck::Kind {
    fn from(k: DeckKind) -> Self {
        match k {
            DeckKind::Normal(n) => pb::deck::Kind::Normal(n),
            DeckKind::Filtered(f) => pb::deck::Kind::Filtered(f),
        }
    }
}

impl From<pb::deck::Kind> for DeckKind {
    fn from(kind: pb::deck::Kind) -> Self {
        match kind {
            pb::deck::Kind::Normal(normal) => DeckKind::Normal(normal),
            pb::deck::Kind::Filtered(filtered) => DeckKind::Filtered(filtered),
        }
    }
}

// fn new_deck(&self, input: pb::Bool) -> Result<pb::Deck> {
//     let deck = if input.val {
//         Deck::new_filtered()
//     } else {
//         Deck::new_normal()
//     };
//     Ok(deck.into())
// }
