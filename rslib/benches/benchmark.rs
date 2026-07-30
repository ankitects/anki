// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::hint::black_box;
use std::time::Duration;
use std::time::Instant;

use anki::card_rendering::anki_directive_benchmark;
use anki::collection::CollectionBuilder;
use anki::decks::DeckId;
use anki::notes::AddNoteRequest;
use anki::search::SortMode;
use anki_proto::stats::BrainliftScoreRequest;
use anki_proto::stats::BrainliftTopic;
use criterion::criterion_group;
use criterion::criterion_main;
use criterion::Criterion;
use criterion::Throughput;

pub fn criterion_benchmark(c: &mut Criterion) {
    c.bench_function("anki_tag_parse", |b| b.iter(|| anki_directive_benchmark()));
    brainlift_score_snapshot_50k(c);
}

fn brainlift_score_snapshot_50k(c: &mut Criterion) {
    const CARD_COUNT: usize = 50_000;
    const TOPIC_COUNT: usize = 100;

    let mut col = CollectionBuilder::default().build().unwrap();
    let notetype = col.get_notetype_by_name("Basic").unwrap().unwrap();
    let topics: Vec<_> = (0..TOPIC_COUNT)
        .map(|idx| BrainliftTopic {
            name: format!("Topic {idx}"),
            tag: format!("mcat::topic::{idx}"),
        })
        .collect();
    let mut notes: Vec<_> = (0..CARD_COUNT)
        .map(|idx| {
            let mut note = notetype.new_note();
            note.tags = vec![format!("mcat::topic::{}", idx % TOPIC_COUNT)];
            if (idx / TOPIC_COUNT) % 2 == 1 {
                note.tags.push("brainlift::evidence::performance".into());
            }
            AddNoteRequest {
                note,
                deck_id: DeckId(1),
            }
        })
        .collect();
    col.add_notes(&mut notes).unwrap();
    let card_ids = col.search_cards("", SortMode::NoOrder).unwrap();
    col.grade_now(&card_ids, 2).unwrap();
    let request = BrainliftScoreRequest { topics };

    let mut samples = Vec::with_capacity(20);
    for _ in 0..20 {
        let started = Instant::now();
        let _ = black_box(col.brainlift_score_snapshot(request.clone()).unwrap());
        samples.push(started.elapsed());
    }
    samples.sort_unstable();
    let median = samples[samples.len() / 2];
    let p95 = samples[(samples.len() * 95 / 100).min(samples.len() - 1)];
    let worst = *samples.last().unwrap_or(&Duration::ZERO);
    eprintln!(
        "brainlift_score_snapshot_50k median_ms={} p95_ms={} worst_ms={}",
        median.as_millis(),
        p95.as_millis(),
        worst.as_millis()
    );

    let mut group = c.benchmark_group("brainlift");
    group.throughput(Throughput::Elements(CARD_COUNT as u64));
    group.bench_function("score_snapshot_50k_cards", |b| {
        b.iter(|| {
            black_box(
                col.brainlift_score_snapshot(black_box(request.clone()))
                    .unwrap(),
            )
        })
    });
    group.finish();
}

criterion_group!(benches, criterion_benchmark);
criterion_main!(benches);
