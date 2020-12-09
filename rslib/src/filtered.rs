// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use crate::backend_proto::{
    deck_kind::Kind as DeckKind, filtered_search_term::FilteredSearchOrder, Deck as DeckProto,
    DeckCommon, DeckKind as DeckKindProto, FilteredDeck, FilteredSearchTerm, NormalDeck,
};
use crate::decks::{Deck, DeckID};
use crate::{
    card::{Card, CardID, CardQueue, CardType},
    collection::Collection,
    config::SchedulerVersion,
    err::Result,
    prelude::AnkiError,
    search::SortMode,
    timestamp::TimestampSecs,
    types::Usn,
};

impl Card {
    pub(crate) fn move_into_filtered_deck(&mut self, ctx: &DeckFilterContext, position: i32) {
        // filtered and v1 learning cards are excluded, so odue should be guaranteed to be zero
        if self.original_due != 0 {
            println!("bug: odue was set");
            return;
        }

        self.original_deck_id = self.deck_id;
        self.deck_id = ctx.target_deck;

        self.original_due = self.due;

        if ctx.scheduler == SchedulerVersion::V1 {
            if self.ctype == CardType::Review && self.due <= ctx.today as i32 {
                // review cards that are due are left in the review queue
            } else {
                // new + non-due go into new queue
                self.queue = CardQueue::New;
            }
            if self.due != 0 {
                self.due = position;
            }
        } else {
            // if rescheduling is disabled, all cards go in the review queue
            if !ctx.config.reschedule {
                self.queue = CardQueue::Review;
            }
            // fixme: can we unify this with v1 scheduler in the future?
            // https://anki.tenderapp.com/discussions/ankidesktop/35978-rebuilding-filtered-deck-on-experimental-v2-empties-deck-and-reschedules-to-the-year-1745
            if self.due > 0 {
                self.due = position;
            }
        }
    }

    /// Restores to the original deck and clears original_due.
    /// This does not update the queue or type, so should only be used as
    /// part of an operation that adjusts those separately.
    pub(crate) fn remove_from_filtered_deck_before_reschedule(&mut self) {
        if self.original_deck_id.0 != 0 {
            self.deck_id = self.original_deck_id;
            self.original_deck_id.0 = 0;
            self.original_due = 0;
        }
    }

    pub(crate) fn remove_from_filtered_deck_restoring_queue(&mut self, sched: SchedulerVersion) {
        if self.original_deck_id.0 == 0 {
            // not in a filtered deck
            return;
        }

        self.deck_id = self.original_deck_id;
        self.original_deck_id.0 = 0;

        match sched {
            SchedulerVersion::V1 => {
                self.due = self.original_due;
                self.queue = match self.ctype {
                    CardType::New => CardQueue::New,
                    CardType::Learn => CardQueue::New,
                    CardType::Review => CardQueue::Review,
                    // not applicable in v1, should not happen
                    CardType::Relearn => {
                        println!("did not expect relearn type in v1 for card {}", self.id);
                        CardQueue::New
                    }
                };
                if self.ctype == CardType::Learn {
                    self.ctype = CardType::New;
                }
            }
            SchedulerVersion::V2 => {
                // original_due is cleared if card answered in filtered deck
                if self.original_due > 0 {
                    self.due = self.original_due;
                }

                if (self.queue as i8) >= 0 {
                    self.queue = match self.ctype {
                        CardType::Learn | CardType::Relearn => {
                            if self.due > 1_000_000_000 {
                                // unix timestamp
                                CardQueue::Learn
                            } else {
                                // day number
                                CardQueue::DayLearn
                            }
                        }
                        CardType::New => CardQueue::New,
                        CardType::Review => CardQueue::Review,
                    }
                }
            }
        }

        self.original_due = 0;
    }
}

impl Deck {
    pub fn new_filtered() -> Deck {
        let mut filt = FilteredDeck::default();
        filt.search_terms.push(FilteredSearchTerm {
            search: "".into(),
            limit: 100,
            order: 0,
        });
        filt.preview_delay = 10;
        filt.reschedule = true;
        Deck {
            id: DeckID(0),
            name: "".into(),
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            common: DeckCommon::default(),
            kind: DeckKind::Filtered(filt),
        }
    }

    pub(crate) fn is_filtered(&self) -> bool {
        matches!(self.kind, DeckKind::Filtered(_))
    }
}

pub(crate) struct DeckFilterContext<'a> {
    pub target_deck: DeckID,
    pub config: &'a FilteredDeck,
    pub scheduler: SchedulerVersion,
    pub usn: Usn,
    pub today: u32,
}

impl Collection {
    pub fn empty_filtered_deck(&mut self, did: DeckID) -> Result<()> {
        self.transact(None, |col| col.return_all_cards_in_filtered_deck(did))
    }
    pub(super) fn return_all_cards_in_filtered_deck(&mut self, did: DeckID) -> Result<()> {
        let cids = self.storage.all_cards_in_single_deck(did)?;
        self.return_cards_to_home_deck(&cids)
    }

    // Unlike the old Python code, this also marks the cards as modified.
    fn return_cards_to_home_deck(&mut self, cids: &[CardID]) -> Result<()> {
        let sched = self.sched_ver();
        let usn = self.usn()?;
        for cid in cids {
            if let Some(mut card) = self.storage.get_card(*cid)? {
                let original = card.clone();
                card.remove_from_filtered_deck_restoring_queue(sched);
                self.update_card(&mut card, &original, usn)?;
            }
        }
        Ok(())
    }

    // Unlike the old Python code, this also marks the cards as modified.
    pub fn rebuild_filtered_deck(&mut self, did: DeckID) -> Result<u32> {
        let deck = self.get_deck(did)?.ok_or(AnkiError::NotFound)?;
        let config = if let DeckKind::Filtered(kind) = &deck.kind {
            kind
        } else {
            return Err(AnkiError::invalid_input("not filtered"));
        };
        let ctx = DeckFilterContext {
            target_deck: did,
            config,
            scheduler: self.sched_ver(),
            usn: self.usn()?,
            today: self.timing_today()?.days_elapsed,
        };

        self.transact(None, |col| {
            col.return_all_cards_in_filtered_deck(did)?;
            col.build_filtered_deck(ctx)
        })
    }

    fn build_filtered_deck(&mut self, ctx: DeckFilterContext) -> Result<u32> {
        let start = -100_000;
        let mut position = start;
        for term in &ctx.config.search_terms {
            position = self.move_cards_matching_term(&ctx, term, position)?;
        }

        Ok((position - start) as u32)
    }

    /// Move matching cards into filtered deck.
    /// Returns the new starting position.
    fn move_cards_matching_term(
        &mut self,
        ctx: &DeckFilterContext,
        term: &FilteredSearchTerm,
        mut position: i32,
    ) -> Result<i32> {
        let search = format!(
            "{} -is:suspended -is:buried -deck:filtered {}",
            if term.search.trim().is_empty() {
                "".to_string()
            } else {
                format!("({})", term.search)
            },
            if ctx.scheduler == SchedulerVersion::V1 {
                "-is:learn"
            } else {
                ""
            }
        );
        let order = order_and_limit_for_search(term, ctx.today);

        self.search_cards_into_table(&search, SortMode::Custom(order))?;
        for mut card in self.storage.all_searched_cards_in_search_order()? {
            let original = card.clone();
            card.move_into_filtered_deck(ctx, position);
            self.update_card(&mut card, &original, ctx.usn)?;
            position += 1;
        }

        Ok(position)
    }
}

fn order_and_limit_for_search(term: &FilteredSearchTerm, today: u32) -> String {
    let temp_string;
    let order = match term.order() {
        FilteredSearchOrder::OldestFirst => "(select max(id) from revlog where cid=c.id)",
        FilteredSearchOrder::Random => "random()",
        FilteredSearchOrder::IntervalsAscending => "ivl",
        FilteredSearchOrder::IntervalsDescending => "ivl desc",
        FilteredSearchOrder::Lapses => "lapses desc",
        FilteredSearchOrder::Added => "n.id",
        FilteredSearchOrder::ReverseAdded => "n.id desc",
        FilteredSearchOrder::Due => "c.due, c.ord",
        FilteredSearchOrder::DuePriority => {
            temp_string = format!(
                "
(case when queue={rev_queue} and due <= {today}
then (ivl / cast({today}-due+0.001 as real)) else 100000+due end)",
                rev_queue = CardQueue::Review as i8,
                today = today
            );
            &temp_string
        }
    };

    format!("{} limit {}", order, term.limit)
}
