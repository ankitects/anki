// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::alloc::GlobalAlloc;
use std::alloc::Layout;
use std::alloc::System;
use std::hint::black_box;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering;
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

struct TrackingAllocator;

static LIVE_HEAP_BYTES: AtomicUsize = AtomicUsize::new(0);
static PEAK_LIVE_HEAP_BYTES: AtomicUsize = AtomicUsize::new(0);

#[global_allocator]
static GLOBAL_ALLOCATOR: TrackingAllocator = TrackingAllocator;

unsafe impl GlobalAlloc for TrackingAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        let allocated = unsafe { System.alloc(layout) };
        if !allocated.is_null() {
            record_allocation(layout.size());
        }
        allocated
    }

    unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
        let allocated = unsafe { System.alloc_zeroed(layout) };
        if !allocated.is_null() {
            record_allocation(layout.size());
        }
        allocated
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        unsafe { System.dealloc(ptr, layout) };
        record_deallocation(layout.size());
    }

    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
        let reallocated = unsafe { System.realloc(ptr, layout, new_size) };
        if !reallocated.is_null() {
            if new_size >= layout.size() {
                record_allocation(new_size - layout.size());
            } else {
                record_deallocation(layout.size() - new_size);
            }
        }
        reallocated
    }
}

fn record_allocation(bytes: usize) {
    let live = LIVE_HEAP_BYTES
        .fetch_update(Ordering::SeqCst, Ordering::SeqCst, |current| {
            Some(current.saturating_add(bytes))
        })
        .unwrap()
        .saturating_add(bytes);
    PEAK_LIVE_HEAP_BYTES.fetch_max(live, Ordering::SeqCst);
}

fn record_deallocation(bytes: usize) {
    LIVE_HEAP_BYTES
        .fetch_update(Ordering::SeqCst, Ordering::SeqCst, |current| {
            Some(current.saturating_sub(bytes))
        })
        .unwrap();
}

fn reset_peak_live_heap_bytes() -> usize {
    let baseline = LIVE_HEAP_BYTES.load(Ordering::SeqCst);
    PEAK_LIVE_HEAP_BYTES.store(baseline, Ordering::SeqCst);
    PEAK_LIVE_HEAP_BYTES.fetch_max(LIVE_HEAP_BYTES.load(Ordering::SeqCst), Ordering::SeqCst);
    baseline
}

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
                note.tags.push("brainlift::evidence::performance::1".into());
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
    let mut peak_additional_bytes = 0;
    for _ in 0..20 {
        let baseline_live_bytes = reset_peak_live_heap_bytes();
        let started = Instant::now();
        let snapshot = black_box(col.brainlift_score_snapshot(request.clone()).unwrap());
        samples.push(started.elapsed());
        peak_additional_bytes = peak_additional_bytes.max(
            PEAK_LIVE_HEAP_BYTES
                .load(Ordering::SeqCst)
                .saturating_sub(baseline_live_bytes),
        );
        black_box(&snapshot);
        drop(snapshot);
    }
    samples.sort_unstable();
    let median = samples[samples.len() / 2];
    let p95 = samples[(samples.len() * 95 / 100).min(samples.len() - 1)];
    let worst = *samples.last().unwrap_or(&Duration::ZERO);
    eprintln!(
        "brainlift_score_snapshot_50k median_ms={} p95_ms={} worst_ms={} \
         peak_additional_bytes={} peak_additional_mib={:.2}",
        median.as_millis(),
        p95.as_millis(),
        worst.as_millis(),
        peak_additional_bytes,
        peak_additional_bytes as f64 / (1024 * 1024) as f64,
    );
    assert!(
        p95 < Duration::from_millis(200),
        "brainlift_score_snapshot_50k p95 latency budget exceeded: {p95:?} is not below 200ms"
    );
    assert!(
        worst < Duration::from_millis(500),
        "brainlift_score_snapshot_50k worst latency budget exceeded: {worst:?} is not below 500ms"
    );
    assert!(
        peak_additional_bytes < 64 * 1024 * 1024,
        "brainlift_score_snapshot_50k peak additional live heap budget exceeded: \
         {peak_additional_bytes} bytes ({:.2} MiB) is not below 64 MiB",
        peak_additional_bytes as f64 / (1024 * 1024) as f64,
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
