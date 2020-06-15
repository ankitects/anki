// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::CardQueue, i18n::I18n, prelude::*, sched::timespan::time_span, sync::ReviewLogEntry,
};
use askama::Template;
use chrono::prelude::*;

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
    note_type: String,
    deck: String,
    nid: NoteID,
    cid: CardID,
    revlog: Vec<BasicRevlog>,
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

struct BasicRevlog {
    time: TimestampSecs,
    kind: u8,
    rating: u8,
    interval: i32,
    ease: u32,
    taken_secs: f32,
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

impl From<ReviewLogEntry> for BasicRevlog {
    fn from(e: ReviewLogEntry) -> Self {
        BasicRevlog {
            time: e.id.as_secs(),
            kind: e.kind,
            rating: e.ease,
            interval: e.interval,
            ease: e.factor,
            taken_secs: (e.time as f32) / 1000.0,
        }
    }
}

impl Collection {
    pub fn card_stats(&mut self, cid: CardID) -> Result<String> {
        let stats = self.gather_card_stats(cid)?;
        Ok(self.card_stats_to_string(stats))
    }

    fn gather_card_stats(&mut self, cid: CardID) -> Result<CardStats> {
        let card = self.storage.get_card(cid)?.ok_or(AnkiError::NotFound)?;
        let note = self
            .storage
            .get_note(card.nid)?
            .ok_or(AnkiError::NotFound)?;
        let nt = self.get_notetype(note.ntid)?.ok_or(AnkiError::NotFound)?;
        let deck = self
            .storage
            .get_deck(card.did)?
            .ok_or(AnkiError::NotFound)?;

        let revlog = self.storage.get_revlog_entries_for_card(card.id)?;
        let average_secs;
        let total_secs;
        if revlog.is_empty() {
            average_secs = 0.0;
            total_secs = 0.0;
        } else {
            total_secs = revlog.iter().map(|e| (e.time as f32) / 1000.0).sum();
            average_secs = total_secs / (revlog.len() as f32);
        }

        let due = match card.queue {
            CardQueue::New => Due::Position(card.due),
            CardQueue::Learn => Due::Time(TimestampSecs::now()),
            CardQueue::Review | CardQueue::DayLearn => Due::Time({
                let days_remaining = card.due - (self.timing_today()?.days_elapsed as i32);
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
            interval_secs: card.ivl * 86_400,
            ease: (card.factor as u32) / 10,
            reviews: card.reps,
            lapses: card.lapses,
            average_secs,
            total_secs,
            card_type: nt.get_template(card.ord)?.name.clone(),
            note_type: nt.name.clone(),
            deck: deck.human_name(),
            nid: card.nid,
            cid: card.id,
            revlog: revlog.into_iter().map(Into::into).collect(),
        })
    }

    fn card_stats_to_string(&self, cs: CardStats) -> String {
        let offset = self.local_offset();
        let i18n = &self.i18n;

        let mut stats = vec![(
            i18n.tr(TR::CardStatsAdded).to_string(),
            cs.added.date_string(offset),
        )];
        if let Some(first) = cs.first_review {
            stats.push((
                i18n.tr(TR::CardStatsFirstReview).into(),
                first.date_string(offset),
            ))
        }
        if let Some(last) = cs.latest_review {
            stats.push((
                i18n.tr(TR::CardStatsLatestReview).into(),
                last.date_string(offset),
            ))
        }

        match cs.due {
            Due::Time(secs) => {
                stats.push((
                    i18n.tr(TR::StatisticsDueDate).into(),
                    secs.date_string(offset),
                ));
            }
            Due::Position(pos) => {
                stats.push((
                    i18n.tr(TR::CardStatsNewCardPosition).into(),
                    pos.to_string(),
                ));
            }
            Due::Unknown => {}
        };

        if cs.interval_secs > 0 {
            stats.push((
                i18n.tr(TR::CardStatsInterval).into(),
                time_span(cs.interval_secs as f32, i18n, true),
            ));
        }

        if cs.ease > 0 {
            stats.push((i18n.tr(TR::CardStatsEase).into(), format!("{}%", cs.ease)));
        }
        stats.push((
            i18n.tr(TR::CardStatsReviewCount).into(),
            cs.reviews.to_string(),
        ));
        stats.push((
            i18n.tr(TR::CardStatsLapseCount).into(),
            cs.lapses.to_string(),
        ));

        if cs.total_secs > 0.0 {
            stats.push((
                i18n.tr(TR::CardStatsAverageTime).into(),
                time_span(cs.average_secs, i18n, true),
            ));
            stats.push((
                i18n.tr(TR::CardStatsTotalTime).into(),
                time_span(cs.total_secs, i18n, true),
            ));
        }

        stats.push((i18n.tr(TR::CardStatsCardTemplate).into(), cs.card_type));
        stats.push((i18n.tr(TR::CardStatsNoteType).into(), cs.note_type));
        stats.push((i18n.tr(TR::CardStatsDeckName).into(), cs.deck));
        stats.push((i18n.tr(TR::CardStatsNoteId).into(), cs.cid.0.to_string()));
        stats.push((i18n.tr(TR::CardStatsCardId).into(), cs.nid.0.to_string()));

        let revlog = cs
            .revlog
            .into_iter()
            .map(|e| revlog_to_text(e, i18n, offset))
            .collect();
        let revlog_titles = RevlogText {
            time: i18n.tr(TR::CardStatsReviewLogDate).into(),
            kind: i18n.tr(TR::CardStatsReviewLogType).into(),
            kind_class: "".to_string(),
            rating: i18n.tr(TR::CardStatsReviewLogRating).into(),
            interval: i18n.tr(TR::CardStatsInterval).into(),
            ease: i18n.tr(TR::CardStatsEase).into(),
            rating_class: "".to_string(),
            taken_secs: i18n.tr(TR::CardStatsReviewLogTimeTaken).into(),
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

fn revlog_to_text(e: BasicRevlog, i18n: &I18n, offset: FixedOffset) -> RevlogText {
    let dt = offset.timestamp(e.time.0, 0);
    let time = dt.format("<b>%Y-%m-%d</b> @ %H:%M").to_string();
    let kind = match e.kind {
        0 => i18n.tr(TR::CardStatsReviewLogTypeLearn).into(),
        1 => i18n.tr(TR::CardStatsReviewLogTypeReview).into(),
        2 => i18n.tr(TR::CardStatsReviewLogTypeRelearn).into(),
        3 => i18n.tr(TR::CardStatsReviewLogTypeFiltered).into(),
        4 => i18n.tr(TR::CardStatsReviewLogTypeRescheduled).into(),
        _ => String::from("?"),
    };
    let kind_class = match e.kind {
        0 => String::from("revlog-learn"),
        1 => String::from("revlog-review"),
        2 => String::from("revlog-relearn"),
        3 => String::from("revlog-filtered"),
        4 => String::from("revlog-rescheduled"),
        _ => String::from(""),
    };
    let rating = e.rating.to_string();
    let interval = if e.interval == 0 {
        String::from("")
    } else {
        let interval_secs = if e.interval > 0 {
            e.interval * 86_400
        } else {
            e.interval.abs()
        };
        time_span(interval_secs as f32, i18n, true)
    };
    let ease = if e.ease > 0 {
        format!("{:.0}%", (e.ease as f32) / 10.0)
    } else {
        "".to_string()
    };
    let rating_class = if e.rating == 1 {
        String::from("revlog-ease1")
    } else {
        "".to_string()
    };
    let taken_secs = i18n.trn(
        TR::StatisticsSecondsTaken,
        tr_args!["seconds"=>e.taken_secs as i32],
    );

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
        col.add_note(&mut note, DeckID(1))?;

        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let _report = col.card_stats(cid)?;
        //println!("report {}", report);

        Ok(())
    }
}
