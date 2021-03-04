// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum CollectionOp {
    UpdateCard,
    AnswerCard,
    Bury,
    Suspend,
}

impl Collection {
    pub fn describe_collection_op(&self, op: CollectionOp) -> String {
        let key = match op {
            CollectionOp::UpdateCard => todo!(),
            CollectionOp::AnswerCard => TR::UndoAnswerCard,
            CollectionOp::Bury => TR::StudyingBury,
            CollectionOp::Suspend => TR::StudyingSuspend,
        };

        self.i18n.tr(key).to_string()
    }
}
