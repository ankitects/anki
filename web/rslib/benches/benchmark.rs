// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki::card_rendering::anki_directive_benchmark;
use criterion::criterion_group;
use criterion::criterion_main;
use criterion::Criterion;

pub fn criterion_benchmark(c: &mut Criterion) {
    c.bench_function("anki_tag_parse", |b| b.iter(|| anki_directive_benchmark()));
}

criterion_group!(benches, criterion_benchmark);
criterion_main!(benches);
