use anki::card_rendering::anki_tag_benchmark;
use criterion::{criterion_group, criterion_main, Criterion};

pub fn criterion_benchmark(c: &mut Criterion) {
    c.bench_function("anki_tag_parse", |b| b.iter(|| anki_tag_benchmark()));
}

criterion_group!(benches, criterion_benchmark);
criterion_main!(benches);
