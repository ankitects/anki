// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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
    pub fn congrats_info(&mut self) -> Result<anki_proto::scheduler::CongratsInfoResponse> {
        let deck = self.get_current_deck()?;
        let today = self.timing_today()?.days_elapsed;
        let info = self.storage.congrats_info(&deck, today)?;
        let is_filtered_deck = deck.is_filtered();
        let deck_description = deck.rendered_description();
        let forecast = self.sched_forecast(8).unwrap_or_default();
        let secs_until_next_learn = if info.next_learn_due == 0 {
            // signal to the frontend that no learning cards are due later
            86_400
        } else {
            ((info.next_learn_due as i64) - self.learn_ahead_secs() as i64 - TimestampSecs::now().0)
                .max(60) as u32
        };
        Ok(anki_proto::scheduler::CongratsInfoResponse {
            learn_remaining: info.learn_count,
            review_remaining: info.review_remaining,
            new_remaining: info.new_remaining,
            have_sched_buried: info.have_sched_buried,
            have_user_buried: info.have_user_buried,
            is_filtered_deck,
            secs_until_next_learn,
            bridge_commands_supported: true,
            deck_description,
            forecast,
        })
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn empty() {
        let mut col = Collection::new();
        let info = col.congrats_info().unwrap();
        let expected_forecast = (0..7)
            .map(|offset| anki_proto::scheduler::ReviewForecastDay {
                day_offset: offset,
                total: 0,
                review: 0,
                learn: 0,
                new: 0,
            })
            .collect();
        assert_eq!(
            info,
            anki_proto::scheduler::CongratsInfoResponse {
                learn_remaining: 0,
                review_remaining: false,
                new_remaining: false,
                have_sched_buried: false,
                have_user_buried: false,
                is_filtered_deck: false,
                secs_until_next_learn: 86_400,
                bridge_commands_supported: true,
                deck_description: "".to_string(),
                forecast: expected_forecast
            }
        )
    }

    #[test]
    fn cards_added_to_graph() {
        let mut col = Collection::new();
        let timing = col.timing_today().unwrap();
        let today = timing.days_elapsed;
        // Create a simple card directly in the database
        col.storage.db.execute_batch(&format!(
            "INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data)
             VALUES 
             (1, 1, 1, 0, {}, 0, 2, 2, {}, 1, 2500, 1, 0, 0, 0, 0, 0, ''),
             (2, 1, 1, 0, {}, 0, 2, 2, {}, 1, 2500, 1, 0, 0, 0, 0, 0, ''),
             (3, 1, 1, 0, {}, 0, 2, 2, {}, 1, 2500, 1, 0, 0, 0, 0, 0, '')",
            timing.now.0,
            today,             // Card 1 due today
            timing.now.0,
            today + 1,         // Card 2 due tomorrow
            timing.now.0,
            today + 2,         // Card 3 due day after tomorrow
        )).unwrap();
        let forecast = col.sched_forecast(7).unwrap();
        // Check that cards appear on the correct days
        assert_eq!(forecast[0].total, 1); // Today: 1 card
        assert_eq!(forecast[0].review, 1);
        assert_eq!(forecast[1].total, 1); // Tomorrow: 1 card
        assert_eq!(forecast[1].review, 1);
        assert_eq!(forecast[2].total, 1); // Day 2: 1 card
        assert_eq!(forecast[2].review, 1);
        // Days 3-6 should have no cards
        for day in forecast.iter().skip(3).take(4) {
            assert_eq!(day.total, 0);
            assert_eq!(day.review, 0);
        }
        // All days should have learn = 0, new = 0 (current implementation)
        for day in &forecast {
            assert_eq!(day.learn, 0);
            assert_eq!(day.new, 0);
        }
    }
}
