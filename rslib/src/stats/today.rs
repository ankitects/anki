// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{i18n::I18n, prelude::*, sched::timespan::Timespan};

pub fn studied_today(cards: u32, secs: f32, i18n: &I18n) -> String {
    let span = Timespan::from_secs(secs).natural_span();
    let amount = span.as_unit();
    let unit = span.unit().as_str();
    let secs_per = if cards > 0 {
        secs / (cards as f32)
    } else {
        0.0
    };
    let args = tr_args!["amount" => amount, "unit" => unit,
        "cards" => cards, "secs-per-card" => secs_per];
    i18n.trn(TR::StatisticsStudiedToday, args)
}

impl Collection {
    pub fn studied_today(&self) -> Result<String> {
        let today = self
            .storage
            .studied_today(self.timing_today()?.next_day_at)?;
        Ok(studied_today(today.cards, today.seconds as f32, &self.i18n))
    }
}

#[cfg(test)]
mod test {
    use super::studied_today;
    use crate::i18n::I18n;
    use crate::log;

    #[test]
    fn today() {
        // temporary test of fluent term handling
        let log = log::terminal();
        let i18n = I18n::new(&["zz"], "", log);
        assert_eq!(
            &studied_today(3, 13.0, &i18n).replace("\n", " "),
            "Studied 3 cards in 13 seconds today (4.33s/card)"
        );
    }
}
