// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use askama::Template;
use chrono::prelude::*;

use crate::{
    card::CardQueue,
    i18n::I18n,
    prelude::*,
    revlog::{RevlogEntry, RevlogReviewKind},
    scheduler::timespan::time_span,
};

struct CardStats {
    added: TimestampSecs,
    first_review: Option<TimestampSecs>,
    latest_review: Option<TimestampSecs>,
    due: Due,
    interval_secs: u32,
    ease: u32,
    reviews: u32,
    lapses: u32,
    average_secs: f32,
    total_secs: f32,
    card_type: String,
    notetype: String,
    deck: String,
    nid: NoteId,
    cid: CardId,
    revlog: Vec<RevlogEntry>,
}

#[derive(Template)]
#[template(path = "../src/stats/card_stats.html")]
struct CardStatsTemplate {
    stats: Vec<(String, String)>,
    revlog: Vec<RevlogText>,
    revlog_titles: RevlogText,
}

enum Due {
    Time(TimestampSecs),
    Position(i32),
    Unknown,
}

struct RevlogText {
    time: String,
    kind: String,
    kind_class: String,
    rating: String,
    rating_class: String,
    interval: String,
    ease: String,
    taken_secs: String,
}

impl Collection {
    pub fn card_stats(&mut self, cid: CardId) -> Result<String> {
        let stats = self.gather_card_stats(cid)?;
        Ok(self.card_stats_to_string(stats))
    }

    fn gather_card_stats(&mut self, cid: CardId) -> Result<CardStats> {
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
        let average_secs;
        let total_secs;
        let normal_answer_count = revlog.iter().filter(|r| r.button_chosen > 0).count();
        if normal_answer_count == 0 {
            average_secs = 0.0;
            total_secs = 0.0;
        } else {
            total_secs = revlog
                .iter()
                .map(|e| (e.taken_millis as f32) / 1000.0)
                .sum();
            average_secs = total_secs / normal_answer_count as f32;
        }

        let due = if card.original_due != 0 {
            card.original_due
        } else {
            card.due
        };
        let due = match card.queue {
            CardQueue::New => Due::Position(due),
            CardQueue::Learn => Due::Time(TimestampSecs::now()),
            CardQueue::Review | CardQueue::DayLearn => Due::Time({
                let days_remaining = due - (self.timing_today()?.days_elapsed as i32);
                let mut due = TimestampSecs::now();
                due.0 += (days_remaining as i64) * 86_400;
                due
            }),
            _ => Due::Unknown,
        };

        Ok(CardStats {
            added: card.id.as_secs(),
            first_review: revlog.first().map(|e| e.id.as_secs()),
            latest_review: revlog.last().map(|e| e.id.as_secs()),
            due,
            interval_secs: card.interval * 86_400,
            ease: (card.ease_factor as u32) / 10,
            reviews: card.reps,
            lapses: card.lapses,
            average_secs,
            total_secs,
            card_type: nt.get_template(card.template_idx)?.name.clone(),
            notetype: nt.name.clone(),
            deck: deck.human_name(),
            nid: card.note_id,
            cid: card.id,
            revlog: revlog.into_iter().map(Into::into).collect(),
        })
    }

    fn card_stats_to_string(&mut self, cs: CardStats) -> String {
        let tr = &self.tr;

        let mut stats = vec![(tr.card_stats_added().into(), cs.added.date_string())];
        if let Some(first) = cs.first_review {
            stats.push((tr.card_stats_first_review().into(), first.date_string()))
        }
        if let Some(last) = cs.latest_review {
            stats.push((tr.card_stats_latest_review().into(), last.date_string()))
        }

        match cs.due {
            Due::Time(secs) => {
                stats.push((tr.statistics_due_date().into(), secs.date_string()));
            }
            Due::Position(pos) => {
                stats.push((tr.card_stats_new_card_position().into(), pos.to_string()));
            }
            Due::Unknown => {}
        };

        if cs.interval_secs > 0 {
            stats.push((
                tr.card_stats_interval().into(),
                time_span(cs.interval_secs as f32, tr, true),
            ));
        }

        if cs.ease > 0 {
            stats.push((tr.card_stats_ease().into(), format!("{}%", cs.ease)));
        }
        stats.push((tr.card_stats_review_count().into(), cs.reviews.to_string()));
        stats.push((tr.card_stats_lapse_count().into(), cs.lapses.to_string()));

        if cs.total_secs > 0.0 {
            stats.push((
                tr.card_stats_average_time().into(),
                time_span(cs.average_secs, tr, true),
            ));
            stats.push((
                tr.card_stats_total_time().into(),
                time_span(cs.total_secs, tr, true),
            ));
        }

        stats.push((tr.card_stats_card_template().into(), cs.card_type));
        stats.push((tr.card_stats_note_type().into(), cs.notetype));
        stats.push((tr.card_stats_deck_name().into(), cs.deck));
        stats.push((tr.card_stats_card_id().into(), cs.cid.0.to_string()));
        stats.push((tr.card_stats_note_id().into(), cs.nid.0.to_string()));

        let revlog = cs
            .revlog
            .into_iter()
            .rev()
            .map(|e| revlog_to_text(e, tr))
            .collect();
        let revlog_titles = RevlogText {
            time: tr.card_stats_review_log_date().into(),
            kind: tr.card_stats_review_log_type().into(),
            kind_class: "".to_string(),
            rating: tr.card_stats_review_log_rating().into(),
            interval: tr.card_stats_interval().into(),
            ease: tr.card_stats_ease().into(),
            rating_class: "".to_string(),
            taken_secs: tr.card_stats_review_log_time_taken().into(),
        };

        CardStatsTemplate {
            stats,
            revlog,
            revlog_titles,
        }
        .render()
        .unwrap()
    }
}

fn revlog_to_text(e: RevlogEntry, tr: &I18n) -> RevlogText {
    let dt = Local.timestamp(e.id.as_secs().0, 0);
    let time = dt.format("<b>%Y-%m-%d</b> @ %H:%M").to_string();
    let kind = match e.review_kind {
        RevlogReviewKind::Learning => tr.card_stats_review_log_type_learn().into(),
        RevlogReviewKind::Review => tr.card_stats_review_log_type_review().into(),
        RevlogReviewKind::Relearning => tr.card_stats_review_log_type_relearn().into(),
        RevlogReviewKind::EarlyReview => tr.card_stats_review_log_type_filtered().into(),
        RevlogReviewKind::Manual => tr.card_stats_review_log_type_manual().into(),
    };
    let kind_class = match e.review_kind {
        RevlogReviewKind::Learning => String::from("revlog-learn"),
        RevlogReviewKind::Review => String::from("revlog-review"),
        RevlogReviewKind::Relearning => String::from("revlog-relearn"),
        RevlogReviewKind::EarlyReview => String::from("revlog-filtered"),
        RevlogReviewKind::Manual => String::from("revlog-manual"),
    };
    let rating = e.button_chosen.to_string();
    let interval = if e.interval == 0 {
        String::from("")
    } else {
        let interval_secs = e.interval_secs();
        time_span(interval_secs as f32, tr, true)
    };
    let ease = if e.ease_factor > 0 {
        format!("{}%", e.ease_factor / 10)
    } else {
        "".to_string()
    };
    let rating_class = if e.button_chosen == 1 {
        String::from("revlog-ease1")
    } else {
        "".to_string()
    };
    let taken_secs = tr
        .statistics_seconds_taken((e.taken_millis / 1000) as i32)
        .into();

    RevlogText {
        time,
        kind,
        kind_class,
        rating,
        rating_class,
        interval,
        ease,
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
