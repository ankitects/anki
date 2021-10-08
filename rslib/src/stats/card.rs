// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use chrono::prelude::*;

use crate::{
    backend_proto as pb, card::CardQueue, i18n::I18n, prelude::*, revlog::RevlogEntry,
    scheduler::timespan::time_span,
};

impl Collection {
    pub fn card_stats(&mut self, cid: CardId) -> Result<pb::CardStatsResponse> {
        let card = self.storage.get_card(cid)?.ok_or(AnkiError::NotFound)?;
        let note = self
            .storage
            .get_note(card.note_id)?
            .ok_or(AnkiError::NotFound)?;
        let nt = self
            .get_notetype(note.notetype_id)?
            .ok_or(AnkiError::NotFound)?;
        let deck = self
            .storage
            .get_deck(card.deck_id)?
            .ok_or(AnkiError::NotFound)?;
        let revlog = self.storage.get_revlog_entries_for_card(card.id)?;

        let (average_secs, total_secs) = average_and_total_secs_strings(&self.tr, &revlog);
        let (due_date, due_position) = self.due_date_and_position_strings(&card)?;

        Ok(pb::CardStatsResponse {
            card_id: card.id.to_string(),
            note_id: card.note_id.to_string(),
            deck: deck.human_name(),
            added: card.id.as_secs().date_string(),
            first_review: first_review_string(&revlog),
            latest_review: latest_review_string(&revlog),
            due_date,
            due_position,
            interval: interval_string(&self.tr, &card),
            ease: ease_string(card.ease_factor as u32),
            reviews: card.reps.to_string(),
            lapses: card.lapses.to_string(),
            average_secs,
            total_secs,
            card_type: nt.get_template(card.template_idx)?.name.clone(),
            notetype: nt.name.clone(),
            revlog: revlog
                .iter()
                .rev()
                .map(|entry| stats_revlog_entry(&self.tr, entry))
                .collect(),
        })
    }

    fn due_date_and_position_strings(&mut self, card: &Card) -> Result<(String, String)> {
        let due = if card.original_due != 0 {
            card.original_due
        } else {
            card.due
        };
        Ok(match card.queue {
            CardQueue::New => ("".to_string(), due.to_string()),
            CardQueue::Learn => (TimestampSecs::now().date_string(), "".to_string()),
            CardQueue::Review | CardQueue::DayLearn => (
                {
                    let days_remaining = due - (self.timing_today()?.days_elapsed as i32);
                    let mut due = TimestampSecs::now();
                    due.0 += (days_remaining as i64) * 86_400;
                    due.date_string()
                },
                "".to_string(),
            ),
            _ => ("".to_string(), "".to_string()),
        })
    }
}

fn first_review_string(revlog: &[RevlogEntry]) -> String {
    if let Some(entry) = revlog.first() {
        entry.id.as_secs().date_string()
    } else {
        "".to_string()
    }
}

fn latest_review_string(revlog: &[RevlogEntry]) -> String {
    if let Some(entry) = revlog.last() {
        entry.id.as_secs().date_string()
    } else {
        "".to_string()
    }
}

fn ease_string(ease_factor: u32) -> String {
    if ease_factor > 0 {
        format!("{}%", ease_factor / 10)
    } else {
        "".to_string()
    }
}

fn interval_string(tr: &I18n, card: &Card) -> String {
    if card.interval > 0 {
        time_span((card.interval * 86_400) as f32, tr, true)
    } else {
        "".to_string()
    }
}

fn average_and_total_secs_strings(tr: &I18n, revlog: &[RevlogEntry]) -> (String, String) {
    let normal_answer_count = revlog.iter().filter(|r| r.button_chosen > 0).count();
    let total_secs: f32 = revlog
        .iter()
        .map(|entry| (entry.taken_millis as f32) / 1000.0)
        .sum();
    if normal_answer_count == 0 || total_secs == 0.0 {
        ("".to_string(), "".to_string())
    } else {
        let average_secs = total_secs / normal_answer_count as f32;
        (
            time_span(average_secs, tr, true),
            time_span(average_secs, tr, true),
        )
    }
}

fn stats_revlog_entry(tr: &I18n, entry: &RevlogEntry) -> pb::card_stats_response::StatsRevlogEntry {
    let dt = Local.timestamp(entry.id.as_secs().0, 0);
    let time = dt.format("%Y-%m-%d @ %H:%M").to_string();
    let interval = if entry.interval == 0 {
        String::from("")
    } else {
        let interval_secs = entry.interval_secs();
        time_span(interval_secs as f32, tr, true)
    };
    let taken_secs = tr
        .statistics_seconds_taken((entry.taken_millis / 1000) as i32)
        .into();

    pb::card_stats_response::StatsRevlogEntry {
        time,
        review_kind: entry.review_kind.into(),
        button_chosen: entry.button_chosen as u32,
        interval,
        ease: ease_string(entry.ease_factor),
        taken_secs,
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::{collection::open_test_collection, search::SortMode};

    #[test]
    fn stats() -> Result<()> {
        let mut col = open_test_collection();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let _report = col.card_stats(cid)?;
        //println!("report {}", report);

        Ok(())
    }
}
