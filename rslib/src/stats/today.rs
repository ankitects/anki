// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_i18n::I18n;

use crate::prelude::*;
use crate::scheduler::timespan::Timespan;
use crate::scheduler::timespan::TimespanUnit;

pub fn studied_today(cards: u32, secs: f32, tr: &I18n) -> String {
    let span = Timespan::from_secs(secs).to_unit(if secs < 60. {
        TimespanUnit::Seconds
    } else {
        TimespanUnit::Minutes
    });
    let amount = span.as_unit();
    let unit = span.unit().as_str();
    let secs_per_card = if cards > 0 {
        secs / (cards as f32)
    } else {
        0.0
    };
    tr.statistics_studied_today(unit, secs_per_card, amount, cards)
        .into()
}

impl Collection {
    pub fn studied_today(&mut self) -> Result<String> {
        let timing = self.timing_today()?;
        let today = self.storage.studied_today(timing.next_day_at)?;
        Ok(studied_today(today.cards, today.seconds as f32, &self.tr))
    }
}

#[cfg(test)]
mod test {
    use anki_i18n::I18n;
    use super::studied_today;
    #[test]
    fn today() {
        let tr = I18n::template_only();
        assert_eq!(
            &studied_today(3, 13.0, &tr).replace('\n', " "),
            "Studied 3 cards in 13 seconds today (4.33s/card)"
        );
        assert_eq!(
            &studied_today(300, 5400.0, &tr).replace('\n', " "),
            "Studied 300 cards in 90 minutes today (18s/card)"
        );
    }
}