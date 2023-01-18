// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::card::Card;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::pb::stats::graphs_response::card_counts::Counts;
use crate::pb::stats::graphs_response::CardCounts;
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
                _ => excluding_inactive.increment(card),
            };
            including_inactive.increment(card);
        }
        CardCounts {
            excluding_inactive: Some(excluding_inactive),
            including_inactive: Some(including_inactive),
        }
    }
}

impl Counts {
    fn increment(&mut self, card: &Card) {
        match card.ctype {
            CardType::New => {
                self.new_cards += 1;
            }
            CardType::Learn => {
                self.learn += 1;
            }
            CardType::Review => {
                if card.interval < 21 {
                    self.young += 1;
                } else {
                    self.mature += 1;
                }
            }
            CardType::Relearn => {
                self.relearn += 1;
            }
        }
    }
}
