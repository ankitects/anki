// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{Deck, DeckID};
pub use crate::backend_proto::{
    deck_kind::Kind as DeckKind, filtered_search_term::FilteredSearchOrder, Deck as DeckProto,
    DeckCommon, DeckKind as DeckKindProto, FilteredDeck, FilteredSearchTerm, NormalDeck,
};
use crate::{
    card::{CardID, CardQueue},
    collection::Collection,
    config::SchedulerVersion,
    err::Result,
    prelude::AnkiError,
    search::SortMode,
    timestamp::TimestampSecs,
    types::Usn,
};

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
        for mut card in self.storage.all_searched_cards()? {
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
