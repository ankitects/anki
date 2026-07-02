// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::Added;

use super::GraphsContext;

impl GraphsContext {
    pub(super) fn added_days(&self) -> Added {
        let mut data = Added::default();
        for card in &self.cards {
            // this could perhaps be simplified; it currently tries to match the old TS code
            // logic
            let day = ((card.id.as_secs().elapsed_secs_since(self.next_day_start) as f64)
                / 86_400.0)
                .ceil() as i32;
            *data.added.entry(day).or_insert_with(Default::default) += 1;
        }
        data
    }
}
