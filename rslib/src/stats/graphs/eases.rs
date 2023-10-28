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
            if let Some(state) = card.memory_state {
                *difficulty
                    .eases
                    .entry(percent_to_bin(state.difficulty() * 100.0))
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

/// Bins the number into a bin of 0, 5, .. 95
pub(super) fn percent_to_bin(x: f32) -> u32 {
    if x == 100.0 {
        95
    } else {
        ((x / 5.0).floor() * 5.0) as u32
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bins() {
        assert_eq!(percent_to_bin(0.0), 0);
        assert_eq!(percent_to_bin(4.9), 0);
        assert_eq!(percent_to_bin(5.0), 5);
        assert_eq!(percent_to_bin(9.9), 5);
        assert_eq!(percent_to_bin(99.9), 95);
        assert_eq!(percent_to_bin(100.0), 95);
    }
}
