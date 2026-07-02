// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::Intervals;

use crate::card::CardType;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    pub(super) fn intervals(&self) -> Intervals {
        let mut data = Intervals::default();
        for card in &self.cards {
            if matches!(card.ctype, CardType::Review | CardType::Relearn) {
                *data
                    .intervals
                    .entry(card.interval)
                    .or_insert_with(Default::default) += 1;
            }
        }
        data
    }

    pub(super) fn stability(&self) -> Intervals {
        let mut data = Intervals::default();
        for card in &self.cards {
            if let Some(state) = &card.memory_state {
                *data
                    .intervals
                    .entry(state.stability.round() as u32)
                    .or_insert_with(Default::default) += 1;
            }
        }
        data
    }
}
