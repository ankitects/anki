// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::{
    backend_proto::{self as pb},
    decks::{Deck, DeckID, DeckSchema11},
    prelude::*,
};
pub(super) use pb::decks_service::Service as DecksService;

impl DecksService for Backend {
    fn add_or_update_deck_legacy(&self, input: pb::AddOrUpdateDeckLegacyIn) -> Result<pb::DeckId> {
        self.with_col(|col| {
            let schema11: DeckSchema11 = serde_json::from_slice(&input.deck)?;
            let mut deck: Deck = schema11.into();
            if input.preserve_usn_and_mtime {
                col.transact(None, |col| {
                    let usn = col.usn()?;
                    col.add_or_update_single_deck_with_existing_id(&mut deck, usn)
                })?;
            } else {
                col.add_or_update_deck(&mut deck)?;
            }
            Ok(pb::DeckId { did: deck.id.0 })
        })
    }

    fn deck_tree(&self, input: pb::DeckTreeIn) -> Result<pb::DeckTreeNode> {
        let lim = if input.top_deck_id > 0 {
            Some(DeckID(input.top_deck_id))
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

    fn get_deck_names(&self, input: pb::GetDeckNamesIn) -> Result<pb::DeckNames> {
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

    fn remove_deck(&self, input: pb::DeckId) -> Result<pb::Empty> {
        self.with_col(|col| col.remove_deck_and_child_decks(input.into()))
            .map(Into::into)
    }

    fn drag_drop_decks(&self, input: pb::DragDropDecksIn) -> Result<pb::Empty> {
        let source_dids: Vec<_> = input.source_deck_ids.into_iter().map(Into::into).collect();
        let target_did = if input.target_deck_id == 0 {
            None
        } else {
            Some(input.target_deck_id.into())
        };
        self.with_col(|col| col.drag_drop_decks(&source_dids, target_did))
            .map(Into::into)
    }

    fn rename_deck(&self, input: pb::RenameDeckIn) -> Result<pb::Empty> {
        self.with_col(|col| col.rename_deck(input.deck_id.into(), &input.new_name))
            .map(Into::into)
    }
}

impl From<pb::DeckId> for DeckID {
    fn from(did: pb::DeckId) -> Self {
        DeckID(did.did)
    }
}

impl From<DeckID> for pb::DeckId {
    fn from(did: DeckID) -> Self {
        pb::DeckId { did: did.0 }
    }
}
