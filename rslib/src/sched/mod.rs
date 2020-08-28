// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection, config::SchedulerVersion, err::Result, timestamp::TimestampSecs,
};

pub mod cutoff;
pub mod timespan;

use chrono::FixedOffset;
use cutoff::{
    fixed_offset_from_minutes, local_minutes_west_for_stamp, sched_timing_today,
    v1_creation_date_adjusted_to_hour, v1_rollover_from_creation_stamp, SchedTimingToday,
};

impl Collection {
    pub fn timing_today(&self) -> Result<SchedTimingToday> {
        self.timing_for_timestamp(TimestampSecs::now())
    }

    pub fn current_due_day(&mut self, delta: i32) -> Result<u32> {
        Ok(((self.timing_today()?.days_elapsed as i32) + delta).max(0) as u32)
    }

    pub(crate) fn timing_for_timestamp(&self, now: TimestampSecs) -> Result<SchedTimingToday> {
        let local_offset = if self.server {
            self.get_local_mins_west()
        } else {
            None
        };

        let rollover_hour = match self.sched_ver() {
            SchedulerVersion::V1 => None,
            SchedulerVersion::V2 => self.get_v2_rollover(),
        };

        Ok(sched_timing_today(
            self.storage.creation_stamp()?,
            now,
            self.get_creation_mins_west(),
            local_offset,
            rollover_hour,
        ))
    }

    /// Get the local timezone.
    /// We could use this to simplify timing_for_timestamp() in the future
    pub(crate) fn local_offset(&self) -> FixedOffset {
        let local_mins_west = if self.server {
            self.get_local_mins_west()
        } else {
            None
        };
        let local_mins_west =
            local_mins_west.unwrap_or_else(|| local_minutes_west_for_stamp(TimestampSecs::now().0));
        fixed_offset_from_minutes(local_mins_west)
    }

    pub fn rollover_for_current_scheduler(&self) -> Result<u8> {
        match self.sched_ver() {
            SchedulerVersion::V1 => Ok(v1_rollover_from_creation_stamp(
                self.storage.creation_stamp()?.0,
            )),
            SchedulerVersion::V2 => Ok(self.get_v2_rollover().unwrap_or(4)),
        }
    }

    pub(crate) fn set_rollover_for_current_scheduler(&self, hour: u8) -> Result<()> {
        match self.sched_ver() {
            SchedulerVersion::V1 => {
                self.storage
                    .set_creation_stamp(TimestampSecs(v1_creation_date_adjusted_to_hour(
                        self.storage.creation_stamp()?.0,
                        hour,
                    )))
            }
            SchedulerVersion::V2 => self.set_v2_rollover(hour as u32),
        }
    }

    pub(crate) fn unbury_if_day_rolled_over(&mut self) -> Result<()> {
        let last_unburied = self.get_last_unburied_day();
        let today = self.timing_today()?.days_elapsed;
        if last_unburied < today || (today + 7) < last_unburied {
            self.unbury_on_day_rollover()?;
            self.set_last_unburied_day(today)?;
        }

        Ok(())
    }

    fn unbury_on_day_rollover(&mut self) -> Result<()> {
        self.search_cards_into_table("is:buried")?;
        self.storage.for_each_card_in_search(|mut card| {
            card.restore_queue_after_bury_or_suspend();
            self.storage.update_card(&card)
        })?;
        self.clear_searched_cards()?;

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use crate::{
        card::{Card, CardQueue},
        collection::{open_test_collection, Collection},
        search::SortMode,
    };

    #[test]
    fn unbury() {
        let mut col = open_test_collection();
        let mut card = Card::default();
        card.queue = CardQueue::UserBuried;
        col.add_card(&mut card).unwrap();
        let assert_count = |col: &mut Collection, cnt| {
            assert_eq!(
                col.search_cards("is:buried", SortMode::NoOrder)
                    .unwrap()
                    .len(),
                cnt
            );
        };
        assert_count(&mut col, 1);
        // day 0, last unburied 0, so no change
        col.unbury_if_day_rolled_over().unwrap();
        assert_count(&mut col, 1);
        // move creation time back and it should succeed
        let mut stamp = col.storage.creation_stamp().unwrap();
        stamp.0 -= 86_400;
        col.storage.set_creation_stamp(stamp).unwrap();
        col.unbury_if_day_rolled_over().unwrap();
        assert_count(&mut col, 0);
    }
}
