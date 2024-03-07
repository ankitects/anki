// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use fsrs::FSRS;

use crate::card::CardQueue;
use crate::card::CardType;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::scheduler::timing::is_unix_epoch_timestamp;

impl Collection {
    pub fn card_stats(&mut self, cid: CardId) -> Result<anki_proto::stats::CardStatsResponse> {
        let card = self.storage.get_card(cid)?.or_not_found(cid)?;
        let note = self
            .storage
            .get_note(card.note_id)?
            .or_not_found(card.note_id)?;
        let nt = self
            .get_notetype(note.notetype_id)?
            .or_not_found(note.notetype_id)?;
        let deck = self
            .storage
            .get_deck(card.deck_id)?
            .or_not_found(card.deck_id)?;
        let revlog = self.storage.get_revlog_entries_for_card(card.id)?;

        let (average_secs, total_secs) = average_and_total_secs_strings(&revlog);
        let (due_date, due_position) = self.due_date_and_position(&card)?;
        let timing = self.timing_today()?;
        let days_elapsed = self
            .storage
            .time_of_last_review(card.id)?
            .map(|ts| timing.next_day_at.elapsed_days_since(ts))
            .unwrap_or_default() as u32;
        let fsrs_retrievability = card
            .memory_state
            .zip(Some(days_elapsed))
            .map(|(state, days)| {
                FSRS::new(None)
                    .unwrap()
                    .current_retrievability(state.into(), days)
            });

        let original_deck = if card.original_deck_id == DeckId(0) {
            deck.clone()
        } else {
            self.storage
                .get_deck(card.original_deck_id)?
                .or_not_found(card.original_deck_id)?
        };
        let config_id = original_deck.config_id().unwrap();
        let preset = self
            .get_deck_config(config_id, true)?
            .or_not_found(config_id.to_string())?;
        Ok(anki_proto::stats::CardStatsResponse {
            card_id: card.id.into(),
            note_id: card.note_id.into(),
            deck: deck.human_name(),
            added: card.id.as_secs().0,
            first_review: revlog.first().map(|entry| entry.id.as_secs().0),
            latest_review: revlog.last().map(|entry| entry.id.as_secs().0),
            due_date,
            due_position,
            interval: card.interval,
            ease: card.ease_factor as u32,
            reviews: card.reps,
            lapses: card.lapses,
            average_secs,
            total_secs,
            card_type: nt.get_template(card.template_idx)?.name.clone(),
            notetype: nt.name.clone(),
            revlog: revlog.iter().rev().map(stats_revlog_entry).collect(),
            memory_state: card.memory_state.map(Into::into),
            fsrs_retrievability,
            custom_data: card.custom_data,
            preset: preset.name,
            original_deck: if original_deck != deck {
                Some(original_deck.human_name())
            } else {
                None
            },
        })
    }

    fn due_date_and_position(&mut self, card: &Card) -> Result<(Option<i64>, Option<i32>)> {
        let due = if card.original_due != 0 {
            card.original_due
        } else {
            card.due
        };
        Ok(match card.ctype {
            CardType::New => {
                if matches!(card.queue, CardQueue::Review | CardQueue::DayLearn) {
                    // new preview card not answered yet
                    (None, card.original_position.map(|u| u as i32))
                } else {
                    (None, Some(due))
                }
            }
            CardType::Review | CardType::Learn | CardType::Relearn => (
                {
                    if !is_unix_epoch_timestamp(due) {
                        let days_remaining = due - (self.timing_today()?.days_elapsed as i32);
                        let mut due = TimestampSecs::now();
                        due.0 += (days_remaining as i64) * 86_400;
                        Some(due.0)
                    } else {
                        Some(due as i64)
                    }
                },
                None,
            ),
        })
    }
}

fn average_and_total_secs_strings(revlog: &[RevlogEntry]) -> (f32, f32) {
    let normal_answer_count = revlog.iter().filter(|r| r.button_chosen > 0).count();
    let total_secs: f32 = revlog
        .iter()
        .map(|entry| (entry.taken_millis as f32) / 1000.0)
        .sum();
    if normal_answer_count == 0 || total_secs == 0.0 {
        (0.0, 0.0)
    } else {
        (total_secs / normal_answer_count as f32, total_secs)
    }
}

fn stats_revlog_entry(
    entry: &RevlogEntry,
) -> anki_proto::stats::card_stats_response::StatsRevlogEntry {
    anki_proto::stats::card_stats_response::StatsRevlogEntry {
        time: entry.id.as_secs().0,
        review_kind: entry.review_kind.into(),
        button_chosen: entry.button_chosen as u32,
        interval: entry.interval_secs(),
        ease: entry.ease_factor,
        taken_secs: entry.taken_millis as f32 / 1000.,
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::search::SortMode;

    #[test]
    fn stats() -> Result<()> {
        let mut col = Collection::new();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let _report = col.card_stats(cid)?;
        //println!("report {}", report);

        Ok(())
    }
}
