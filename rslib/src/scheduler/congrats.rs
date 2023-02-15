// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::pb;
use crate::prelude::*;

#[derive(Debug)]
pub(crate) struct CongratsInfo {
    pub learn_count: u32,
    pub next_learn_due: u32,
    pub review_remaining: bool,
    pub new_remaining: bool,
    pub have_sched_buried: bool,
    pub have_user_buried: bool,
}

impl Collection {
    pub fn congrats_info(&mut self) -> Result<pb::scheduler::CongratsInfoResponse> {
        let deck = self.get_current_deck()?;
        let today = self.timing_today()?.days_elapsed;
        let info = self.storage.congrats_info(&deck, today)?;
        let is_filtered_deck = deck.is_filtered();
        let deck_description = deck.rendered_description();
        let secs_until_next_learn = if info.next_learn_due == 0 {
            // signal to the frontend that no learning cards are due later
            86_400
        } else {
            ((info.next_learn_due as i64) - self.learn_ahead_secs() as i64 - TimestampSecs::now().0)
                .max(60) as u32
        };
        Ok(pb::scheduler::CongratsInfoResponse {
            learn_remaining: info.learn_count,
            review_remaining: info.review_remaining,
            new_remaining: info.new_remaining,
            have_sched_buried: info.have_sched_buried,
            have_user_buried: info.have_user_buried,
            is_filtered_deck,
            secs_until_next_learn,
            bridge_commands_supported: true,
            deck_description,
        })
    }
}

#[cfg(test)]
mod test {
    use crate::collection::open_test_collection;

    #[test]
    fn empty() {
        let mut col = open_test_collection();
        let info = col.congrats_info().unwrap();
        assert_eq!(
            info,
            crate::pb::scheduler::CongratsInfoResponse {
                learn_remaining: 0,
                review_remaining: false,
                new_remaining: false,
                have_sched_buried: false,
                have_user_buried: false,
                is_filtered_deck: false,
                secs_until_next_learn: 86_400,
                bridge_commands_supported: true,
                deck_description: "".to_string()
            }
        )
    }
}
