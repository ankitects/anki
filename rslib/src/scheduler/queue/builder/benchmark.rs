// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::hint::black_box;
use std::time::Instant;

use super::*;
use crate::card::CardType;
use crate::card::FsrsMemoryState;
use crate::deckconfig::FsrsVersion;
use crate::notes::AddNoteRequest;
use crate::scheduler::fsrs::preset::FsrsPreset;
use crate::storage::card::data::CardData;

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
        for version in [FsrsVersion::Six, FsrsVersion::Seven] {
            for order in [
                ReviewCardOrder::Day,
                ReviewCardOrder::Random,
                ReviewCardOrder::RetrievabilityAscending,
                ReviewCardOrder::RetrievabilityDescending,
            ] {
                let mut config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
                config.inner.fsrs_version = version as i32;
                config.inner.fsrs_params_7 = fsrs::DEFAULT_PARAMETERS.to_vec();
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
                col.get_queued_cards(1, false, true)?;
                let mut answers = Vec::new();
                for _ in 0..25 {
                    let start = Instant::now();
                    black_box(col.answer_good());
                    black_box(col.get_queued_cards(1, false, true)?);
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
        if count == 100_000 {
            benchmark_score_preparation(&mut col)?;
        }
    }
    Ok(())
}

fn benchmark_score_preparation(col: &mut Collection) -> Result<()> {
    let cards = col.storage.get_all_cards();
    let fsrs = fsrs::FSRS::new(&fsrs::DEFAULT_PARAMETERS)?;
    let start = Instant::now();
    for card in &cards {
        black_box(col.storage.get_card(card.id)?);
    }
    println!(
        "stage,load_cards_once,{:.3}",
        start.elapsed().as_secs_f64() * 1_000.0
    );
    let start = Instant::now();
    for card in &cards {
        black_box(col.fsrs_preset_for_card(card)?);
    }
    println!(
        "stage,resolve_presets_individually,{:.3}",
        start.elapsed().as_secs_f64() * 1_000.0
    );
    let start = Instant::now();
    black_box(col.fsrs_presets_for_cards(&cards)?);
    println!(
        "stage,resolve_presets_in_batch,{:.3}",
        start.elapsed().as_secs_f64() * 1_000.0
    );
    let start = Instant::now();
    for _ in &cards {
        black_box(fsrs::FSRS::new(black_box(&fsrs::DEFAULT_PARAMETERS))?);
    }
    println!(
        "stage,construct_models_individually,{:.3}",
        start.elapsed().as_secs_f64() * 1_000.0
    );
    let start = Instant::now();
    for card in &cards {
        black_box(fsrs.current_retrievability(card.memory_state.unwrap().into(), black_box(30.0)));
    }
    println!(
        "stage,fsrs7_math_reusing_model,{:.3}",
        start.elapsed().as_secs_f64() * 1_000.0
    );
    Ok(())
}

#[test]
#[ignore]
fn fsrs_queue_profile() -> Result<()> {
    for count in [10_000, 100_000] {
        let mut col = collection_with_due_cards(count)?;
        for limit in [200, 9_999] {
            let mut config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
            config.inner.fsrs_version = FsrsVersion::Seven as i32;
            config.inner.fsrs_params_7 = fsrs::DEFAULT_PARAMETERS.to_vec();
            config.inner.review_order = ReviewCardOrder::RetrievabilityAscending as i32;
            config.inner.reviews_per_day = limit;
            col.add_or_update_deck_config(&mut config)?;
            let subscriber = tracing_subscriber::fmt()
                .with_env_filter("anki::scheduler::queue::builder=debug")
                .with_ansi(false)
                .without_time()
                .finish();
            tracing::subscriber::with_default(subscriber, || -> Result<()> {
                for sample in 0..5 {
                    let start = Instant::now();
                    let queues = col.build_queues(DeckId(1))?;
                    let elapsed_ms = start.elapsed().as_secs_f64() * 1_000.0;
                    assert_eq!(queues.iter().count(), count.min(limit as usize));
                    println!(
                        "profile,{count},{limit},{sample},{:.3},{}",
                        elapsed_ms,
                        queues.iter().count()
                    );
                }
                Ok(())
            })?;
        }
        benchmark_candidate_reads(&mut col, count)?;
        benchmark_card_data_decoding(&mut col, count)?;
        benchmark_preset_resolution(&mut col, count)?;
        super::gathering::benchmark_retrievability_sort(&mut col)?;
        benchmark_queue_preparation(&mut col, count)?;
    }
    Ok(())
}

/// Sustained queue builds so a native CPU sampler can attach after fixture
/// setup.
#[test]
#[ignore]
fn fsrs_queue_sampling() -> Result<()> {
    let mut col = collection_with_due_cards(100_000)?;
    let mut config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
    config.inner.fsrs_version = FsrsVersion::Seven as i32;
    config.inner.fsrs_params_7 = fsrs::DEFAULT_PARAMETERS.to_vec();
    config.inner.review_order = ReviewCardOrder::RetrievabilityAscending as i32;
    config.inner.reviews_per_day = 200;
    col.add_or_update_deck_config(&mut config)?;
    println!("queue_sampling_ready,pid,{}", std::process::id());
    for _ in 0..200 {
        black_box(col.build_queues(DeckId(1))?);
    }
    Ok(())
}

fn benchmark_card_data_decoding(col: &mut Collection, count: usize) -> Result<()> {
    let data = col
        .storage
        .get_all_cards()
        .iter()
        .map(|card| CardData::from_card(card).convert_to_json())
        .collect::<Result<Vec<_>>>()?;
    let mut bytes_ms = Vec::new();
    let mut text_ms = Vec::new();
    for sample in 0..10 {
        let mut results = Vec::new();
        for text in if sample % 2 == 0 {
            [false, true]
        } else {
            [true, false]
        } {
            let start = Instant::now();
            let parsed = data
                .iter()
                .map(|json| {
                    let bytes = black_box(json.as_bytes());
                    if text {
                        CardData::from_str(std::str::from_utf8(bytes).unwrap())
                    } else {
                        serde_json::from_slice::<CardData>(bytes).unwrap_or_default()
                    }
                })
                .collect::<Vec<_>>();
            let elapsed = start.elapsed().as_secs_f64() * 1_000.0;
            if text {
                text_ms.push(elapsed);
            } else {
                bytes_ms.push(elapsed);
            }
            results.push(parsed);
        }
        assert_eq!(results[0], results[1]);
    }
    println!(
        "card_data,{count},bytes_ms,{:.3},validated_text_ms,{:.3}",
        median_ms(bytes_ms),
        median_ms(text_ms)
    );
    Ok(())
}

fn benchmark_preset_resolution(col: &mut Collection, count: usize) -> Result<()> {
    let cards = col.storage.get_all_cards();
    let mut original_ms = Vec::new();
    let mut reused_ms = Vec::new();
    for sample in 0..10 {
        let mut original = HashMap::new();
        let mut reused = HashMap::new();
        for reuse in if sample % 2 == 0 {
            [false, true]
        } else {
            [true, false]
        } {
            let start = Instant::now();
            if reuse {
                reused = col.fsrs_presets_for_cards(&cards)?;
            } else {
                // Previous batch fallback, for this fixture without overlays.
                let decks = col.storage.get_decks_map()?;
                let configs = col.storage.get_deck_config_map()?;
                let mut presets = HashMap::new();
                for card in &cards {
                    if presets.contains_key(&card.id) {
                        continue;
                    }
                    let deck = &decks[&card.original_deck_id.or(card.deck_id)];
                    let config = &configs[&deck.config_id().unwrap()];
                    presets.insert(card.id, FsrsPreset::from_deck_config(config, deck)?);
                }
                original = presets;
            }
            let elapsed = start.elapsed().as_secs_f64() * 1_000.0;
            if reuse {
                reused_ms.push(elapsed);
            } else {
                original_ms.push(elapsed);
            }
        }
        assert_eq!(original.len(), count);
        assert_eq!(reused.len(), count);
        for (id, before) in &original {
            let after = &reused[id];
            assert_eq!(before.id, after.id);
            assert_eq!(before.params, after.params);
            assert_eq!(before.desired_retention, after.desired_retention);
        }
    }
    println!(
        "presets,{count},original_ms,{:.3},reused_ms,{:.3}",
        median_ms(original_ms),
        median_ms(reused_ms)
    );
    Ok(())
}

fn benchmark_queue_preparation(col: &mut Collection, count: usize) -> Result<()> {
    for enabled in [true, false] {
        col.set_config_bool(BoolKey::LoadBalancerEnabled, enabled, false)?;
        let mut samples = Vec::new();
        for _ in 0..5 {
            let start = Instant::now();
            let builder = QueueBuilder::new(col, DeckId(1))?;
            samples.push(start.elapsed().as_secs_f64() * 1_000.0);
            assert_eq!(builder.load_balancer.is_some(), enabled);
            black_box(builder);
        }
        println!(
            "preparation,{count},load_balancer={enabled},{:.3}",
            median_ms(samples)
        );
    }
    Ok(())
}

fn benchmark_candidate_reads(col: &mut Collection, count: usize) -> Result<()> {
    let timing = col.timing_today()?;
    let mut full_ms = Vec::new();
    let mut narrow_ms = Vec::new();
    for sample in 0..10 {
        let mut full_cards = Vec::new();
        let mut narrow_cards = Vec::new();
        // Alternate order to avoid consistently giving one path a warmer cache.
        for narrow in if sample % 2 == 0 {
            [false, true]
        } else {
            [true, false]
        } {
            let start = Instant::now();
            if narrow {
                narrow_cards = col.storage.due_cards_with_state_in_active_decks(timing)?;
                narrow_ms.push(start.elapsed().as_secs_f64() * 1_000.0);
            } else {
                full_cards = col.storage.due_cards_full_state_for_benchmark(timing)?;
                full_ms.push(start.elapsed().as_secs_f64() * 1_000.0);
            }
        }
        full_cards.sort_unstable_by_key(|card| card.id);
        narrow_cards.sort_unstable_by_key(|card| card.card.id);
        assert_eq!(full_cards.len(), count);
        assert_eq!(
            full_cards
                .iter()
                .map(DueCardWithState::from)
                .collect::<Vec<_>>(),
            narrow_cards
        );
    }
    println!(
        "candidate_reads,{count},full_ms,{:.3},narrow_ms,{:.3}",
        median_ms(full_ms),
        median_ms(narrow_ms)
    );
    Ok(())
}
