// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::Eases;

use crate::card::CardType;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    /// (SM-2, FSRS)
    pub(super) fn eases(&self) -> (Eases, Eases) {
        let mut eases = Eases::default();
        let mut ease_values = Vec::new();
        let mut difficulty = Eases::default();
        let mut difficulty_values = Vec::new();
        for card in &self.cards {
            if let Some(state) = card.memory_state {
                *difficulty
                    .eases
                    .entry(percent_to_bin(state.difficulty() * 100.0))
                    .or_insert_with(Default::default) += 1;
                difficulty_values.push(state.difficulty());
            } else if matches!(card.ctype, CardType::Review | CardType::Relearn) {
                *eases
                    .eases
                    .entry((card.ease_factor / 10) as u32)
                    .or_insert_with(Default::default) += 1;
                ease_values.push(card.ease_factor as f32);
            }
        }
        
        eases.average = median(&mut ease_values) / 10.0;
        difficulty.average = median(&mut difficulty_values) * 100.0;

        (eases, difficulty)
    }
}

/// Helper function to calculate the median of a vector
fn median(data: &mut Vec<f32>) -> f32 {
    if data.is_empty() {
        return 0.0;
    }
    data.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let mid = data.len() / 2;
    if data.len() % 2 == 0 {
        (data[mid - 1] + data[mid]) / 2.0
    } else {
        data[mid]
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
