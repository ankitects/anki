// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::card_counts::Counts;
use anki_proto::stats::graphs_response::CardCounts;

use crate::card::Card;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    pub(super) fn card_counts(&self) -> CardCounts {
        let mut excluding_inactive = Counts::default();
        let mut including_inactive = Counts::default();
        for card in &self.cards {
            match card.queue {
                CardQueue::Suspended => {
                    excluding_inactive.suspended += 1;
                }
                CardQueue::SchedBuried | CardQueue::UserBuried => {
                    excluding_inactive.buried += 1;
                }
                _ => increment_counts(&mut excluding_inactive, card),
            };
            increment_counts(&mut including_inactive, card);
        }
        CardCounts {
            excluding_inactive: Some(excluding_inactive),
            including_inactive: Some(including_inactive),
        }
    }
}

fn increment_counts(counts: &mut Counts, card: &Card) {
    match card.ctype {
        CardType::New => {
            counts.new_cards += 1;
        }
        CardType::Learn => {
            counts.learn += 1;
        }
        CardType::Review => {
            if card.interval < 21 {
                counts.young += 1;
            } else {
                counts.mature += 1;
            }
        }
        CardType::Relearn => {
            counts.relearn += 1;
        }
    }
}
