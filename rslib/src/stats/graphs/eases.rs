// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::card::CardType;
use crate::pb::stats::graphs_response::Eases;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    pub(super) fn eases(&self) -> Eases {
        let mut data = Eases::default();
        for card in &self.cards {
            if matches!(card.ctype, CardType::Review | CardType::Relearn) {
                *data
                    .eases
                    .entry((card.ease_factor / 10) as u32)
                    .or_insert_with(Default::default) += 1;
            }
        }
        data
    }
}
