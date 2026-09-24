// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::hint::black_box;
use std::time::Instant;

use super::*;
use crate::card::CardType;
use crate::card::FsrsMemoryState;
use crate::notes::AddNoteRequest;

fn collection_with_due_cards(count: usize) -> Result<Collection> {
    let mut col = Collection::new();
    col.set_config_bool(BoolKey::Fsrs, true, false)?;
    let notetype = col.get_notetype_by_name("Basic")?.unwrap();
    for start in (0..count).step_by(1_000) {
        let mut notes = (start..(start + 1_000).min(count))
            .map(|index| {
                let mut note = notetype.new_note();
                note.set_field(0, format!("benchmark {index}"))?;
                Ok(AddNoteRequest {
                    note,
                    deck_id: DeckId(1),
                })
            })
            .collect::<Result<Vec<_>>>()?;
        col.add_notes(&mut notes)?;
    }
    let timing = col.timing_today()?;
    let mut cards = col.storage.get_all_cards();
    for (index, card) in cards.iter_mut().enumerate() {
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.due = timing.days_elapsed as i32;
        card.interval = 30;
        card.reps = 10;
        card.memory_state = Some(FsrsMemoryState {
            stability: 1.0 + (index % 365) as f32,
            stability_internal: 1.0 + (index % 365) as f32,
            stability_fast: Some(0.1 + (index % 30) as f32),
            difficulty: 1.0 + (index % 90) as f32 / 10.0,
        });
        card.desired_retention = Some(0.9);
        card.last_review_time = Some(timing.now.adding_secs(-((index % 90 + 1) as i64 * 86_400)));
    }
    col.update_cards_maybe_undoable(cards, false)?;
    Ok(col)
}

fn median_ms(mut samples: Vec<f64>) -> f64 {
    samples.sort_by(f64::total_cmp);
    samples[samples.len() / 2]
}

/// Opt-in performance measurements, without wall-clock assertions or user data.
#[test]
#[ignore]
fn fsrs_queue_benchmark() -> Result<()> {
    println!("cards,version,order,cold_build_ms,warm_build_median_ms,answer_next_median_ms");
    for count in [1_000, 10_000, 50_000, 100_000] {
        let mut col = collection_with_due_cards(count)?;
        let original_cards = col.storage.get_all_cards();
        let original_deck = col.storage.get_deck(DeckId(1))?.unwrap();
        for version in [6, 7] {
            for order in [
                ReviewCardOrder::Day,
                ReviewCardOrder::Random,
                ReviewCardOrder::RetrievabilityAscending,
                ReviewCardOrder::RetrievabilityDescending,
            ] {
                let mut config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
                config.clear_fsrs_params();
                if version == 7 {
                    config.inner.fsrs_params_7 = fsrs::DEFAULT_PARAMETERS.to_vec();
                } else {
                    config.inner.fsrs_params_6 = fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec();
                }
                config.inner.review_order = order as i32;
                config.inner.reviews_per_day = 200;
                col.add_or_update_deck_config(&mut config)?;
                let start = Instant::now();
                let queues = col.build_queues(DeckId(1))?;
                let cold_ms = start.elapsed().as_secs_f64() * 1_000.0;
                assert_eq!(queues.iter().count(), 200);
                black_box(queues);
                let mut builds = Vec::new();
                for _ in 0..5 {
                    let start = Instant::now();
                    black_box(col.build_queues(DeckId(1))?);
                    builds.push(start.elapsed().as_secs_f64() * 1_000.0);
                }
                col.clear_study_queues();
                col.get_queued_cards(1, false)?;
                let mut answers = Vec::new();
                for _ in 0..25 {
                    let start = Instant::now();
                    black_box(col.answer_good());
                    black_box(col.get_queued_cards(1, false)?);
                    answers.push(start.elapsed().as_secs_f64() * 1_000.0);
                }
                println!(
                    "{count},{version:?},{order:?},{cold_ms:.3},{:.3},{:.3}",
                    median_ms(builds),
                    median_ms(answers)
                );
                // Restore memory states, due backlog, and daily counters outside timings.
                col.update_cards_maybe_undoable(original_cards.clone(), false)?;
                col.add_or_update_deck(&mut original_deck.clone())?;
            }
        }
    }
    Ok(())
}
