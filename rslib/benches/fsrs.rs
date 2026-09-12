// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::time::Duration;
use std::time::Instant;

use anki::collection::CollectionBuilder;
use anki::notes::AddNoteRequest;
use anki::prelude::BoolKey;
use anki::prelude::CardId;
use anki::prelude::DeckId;
use anki::search::SortMode;
use criterion::criterion_group;
use criterion::criterion_main;
use criterion::Criterion;

const CARD_COUNT: usize = 100_000;

struct TransferBenchState {
    col: anki::collection::Collection,
    card_ids: Vec<CardId>,
    source_deck_id: DeckId,
    target_deck_id: DeckId,
}

fn setup_transfer_state() -> TransferBenchState {
    let mut col = CollectionBuilder::default().build().unwrap();
    col.set_config_bool(BoolKey::Fsrs, true, false).unwrap();

    let source_deck = col.get_or_create_normal_deck("fsrs-source").unwrap();
    let target_deck = col.get_or_create_normal_deck("fsrs-target").unwrap();

    let notetype = col.get_notetype_by_name("Basic").unwrap().unwrap();

    const BATCH_SIZE: usize = 1_000;
    for batch_start in (0..CARD_COUNT).step_by(BATCH_SIZE) {
        let batch_end = (batch_start + BATCH_SIZE).min(CARD_COUNT);
        let mut requests = Vec::with_capacity(batch_end - batch_start);

        for idx in batch_start..batch_end {
            let mut note = notetype.new_note();
            note.set_field(0, format!("front {idx}")).unwrap();
            note.set_field(1, format!("back {idx}")).unwrap();
            requests.push(AddNoteRequest {
                note,
                deck_id: source_deck.id,
            });
        }

        col.add_notes(&mut requests).unwrap();
    }

    let card_ids = col
        .search_cards("deck:fsrs-source", SortMode::NoOrder)
        .unwrap();
    col.grade_now(&card_ids, 1).unwrap();
    col.grade_now(&card_ids, 3).unwrap();
    col.grade_now(&card_ids, 3).unwrap();

    TransferBenchState {
        col,
        card_ids,
        source_deck_id: source_deck.id,
        target_deck_id: target_deck.id,
    }
}

fn bench_transfer_100k_fsrs(c: &mut Criterion) {
    let mut state = setup_transfer_state();

    let mut group = c.benchmark_group("fsrs_deck_transfer");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(30));
    group.bench_function("move_100k_cards_between_decks", |b| {
        b.iter_custom(|iters| {
            let mut elapsed = Duration::ZERO;
            for _ in 0..iters {
                let start = Instant::now();
                state
                    .col
                    .set_deck(&state.card_ids, state.target_deck_id)
                    .unwrap();
                elapsed += start.elapsed();

                state
                    .col
                    .set_deck(&state.card_ids, state.source_deck_id)
                    .unwrap();
            }
            elapsed
        })
    });
    group.finish();
}

criterion_group!(benches, bench_transfer_100k_fsrs);
criterion_main!(benches);
