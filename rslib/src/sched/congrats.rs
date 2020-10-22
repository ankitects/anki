// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto as pb;
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
    pub fn congrats_info(&mut self) -> Result<pb::CongratsInfoOut> {
        let did = self.get_current_deck_id();
        let deck = self.get_deck(did)?.ok_or(AnkiError::NotFound)?;
        let today = self.timing_today()?.days_elapsed;
        let info = self.storage.congrats_info(&deck, today)?;
        let is_filtered_deck = deck.is_filtered();
        let secs_until_next_learn = ((info.next_learn_due as i64)
            - self.learn_ahead_secs() as i64
            - TimestampSecs::now().0)
            .max(0) as u32;
        Ok(pb::CongratsInfoOut {
            learn_remaining: info.learn_count,
            review_remaining: info.review_remaining,
            new_remaining: info.new_remaining,
            have_sched_buried: info.have_sched_buried,
            have_user_buried: info.have_user_buried,
            is_filtered_deck,
            secs_until_next_learn,
            bridge_commands_supported: true,
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
            crate::backend_proto::CongratsInfoOut {
                learn_remaining: 0,
                review_remaining: false,
                new_remaining: false,
                have_sched_buried: false,
                have_user_buried: false,
                is_filtered_deck: false,
                secs_until_next_learn: 0,
                bridge_commands_supported: true,
            }
        )
    }
}
