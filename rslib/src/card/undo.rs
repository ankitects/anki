// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Undo;
use crate::prelude::*;

#[derive(Debug)]
pub(crate) struct CardAdded(Card);

impl Undo for CardAdded {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.remove_card_only(self.0)
    }
}

#[derive(Debug)]
pub(crate) struct CardRemoved(Card);

impl Undo for CardRemoved {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.readd_deleted_card(self.0)
    }
}

#[derive(Debug)]
pub(crate) struct CardGraveAdded(CardID, Usn);

impl Undo for CardGraveAdded {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.remove_card_grave(self.0, self.1)
    }
}

#[derive(Debug)]
pub(crate) struct CardGraveRemoved(CardID, Usn);

impl Undo for CardGraveRemoved {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        col.add_card_grave_undoable(self.0, self.1)
    }
}

#[derive(Debug)]
pub(crate) struct CardUpdated(Card);

impl Undo for CardUpdated {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        let current = col
            .storage
            .get_card(self.0.id)?
            .ok_or_else(|| AnkiError::invalid_input("card disappeared"))?;
        col.update_card_undoable(&mut self.0.clone(), &current)
    }
}

impl Collection {
    pub(super) fn add_card_undoable(&mut self, card: &mut Card) -> Result<(), AnkiError> {
        self.storage.add_card(card)?;
        self.save_undo(Box::new(CardAdded(card.clone())));
        Ok(())
    }

    pub(super) fn update_card_undoable(&mut self, card: &mut Card, original: &Card) -> Result<()> {
        if card.id.0 == 0 {
            return Err(AnkiError::invalid_input("card id not set"));
        }
        self.save_undo(Box::new(CardUpdated(original.clone())));
        self.storage.update_card(card)
    }

    pub(crate) fn remove_card_and_add_grave_undoable(
        &mut self,
        card: Card,
        usn: Usn,
    ) -> Result<()> {
        self.add_card_grave_undoable(card.id, usn)?;
        self.storage.remove_card(card.id)?;
        self.save_undo(Box::new(CardRemoved(card)));

        Ok(())
    }

    fn add_card_grave_undoable(&mut self, cid: CardID, usn: Usn) -> Result<()> {
        self.save_undo(Box::new(CardGraveAdded(cid, usn)));
        self.storage.add_card_grave(cid, usn)
    }

    fn readd_deleted_card(&mut self, card: Card) -> Result<()> {
        self.storage.add_or_update_card(&card)?;
        self.save_undo(Box::new(CardAdded(card)));
        Ok(())
    }

    fn remove_card_only(&mut self, card: Card) -> Result<()> {
        self.storage.remove_card(card.id)?;
        self.save_undo(Box::new(CardRemoved(card)));
        Ok(())
    }

    fn remove_card_grave(&mut self, cid: CardID, usn: Usn) -> Result<()> {
        self.save_undo(Box::new(CardGraveRemoved(cid, usn)));
        self.storage.remove_card_grave(cid)
    }
}
