// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::Eases;

use crate::card::CardType;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    /// (SM-2, FSRS)
    pub(super) fn eases(&self) -> (Eases, Eases) {
        let mut eases = Eases::default();
        let mut difficulty = Eases::default();
        for card in &self.cards {
            if let Some(state) = card.fsrs_memory_state {
                *difficulty
                    .eases
                    .entry(round_to_nearest_five(
                        (state.difficulty - 1.0) / 9.0 * 100.0,
                    ))
                    .or_insert_with(Default::default) += 1;
            } else if matches!(card.ctype, CardType::Review | CardType::Relearn) {
                *eases
                    .eases
                    .entry((card.ease_factor / 10) as u32)
                    .or_insert_with(Default::default) += 1;
            }
        }
        (eases, difficulty)
    }
}

pub(super) fn round_to_nearest_five(x: f32) -> u32 {
    let scaled = x * 10.0;
    let rounded = (scaled / 5.0).round() * 5.0;
    (rounded / 10.0) as u32
}
