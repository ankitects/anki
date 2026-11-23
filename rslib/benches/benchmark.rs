// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::hint::black_box;

use anki::card_rendering::anki_directive_benchmark;
use criterion::criterion_group;
use criterion::criterion_main;
use criterion::Criterion;
use criterion::Throughput;
use fsrs::MemoryState;
use fsrs::DEFAULT_PARAMETERS;
use fsrs::FSRS;

const TARGET_RETRIEVABILITY: f32 = 0.9;
const STABILITY_SAMPLES: [f32; 14] = [
    0.1, 0.5, 1.0, 2.5, 5.0, 10.0, 20.0, 40.0, 80.0, 160.0, 365.0, 730.0, 1460.0, 3650.0,
];
const RETRIEVABILITY_SAMPLE_COUNT: usize = 4096;
const S_MIN: f32 = 0.0001;
const S_MAX: f32 = 36_500.0;
const BISECTION_ITERS: usize = 50;

fn fsrs7_forgetting_curve_without_lut(t: f32, stability: f32) -> f32 {
    let w = &DEFAULT_PARAMETERS;
    let stability = stability.max(S_MIN);
    let t_over_s = t.max(0.0) / stability;

    let decay1 = -w[27];
    let decay2 = -w[28];
    let base1 = w[29];
    let base2 = w[30];

    let factor1 = base1.powf(1.0 / decay1) - 1.0;
    let factor2 = base2.powf(1.0 / decay2) - 1.0;
    let r1 = (1.0 + factor1 * t_over_s).powf(decay1);
    let r2 = (1.0 + factor2 * t_over_s).powf(decay2);

    let weight1 = w[31] * stability.powf(-w[33]);
    let weight2 = w[32] * stability.powf(w[34]);

    (weight1 * r1 + weight2 * r2) / (weight1 + weight2)
}

fn fsrs7_retrievability_scalar(stability: f32, elapsed_days: f32) -> f32 {
    fsrs7_forgetting_curve_without_lut(elapsed_days, stability)
}

fn fsrs7_s90_without_lut(stability: f32) -> f32 {
    let stability = stability.max(S_MIN);
    let mut low = 0.0;
    let mut high = stability.max(1.0).clamp(0.0, S_MAX).max(S_MIN);

    while fsrs7_forgetting_curve_without_lut(high, stability) > TARGET_RETRIEVABILITY
        && high < S_MAX
    {
        high = (high * 2.0).min(S_MAX);
        if (high - S_MAX).abs() < f32::EPSILON {
            break;
        }
    }

    for _ in 0..BISECTION_ITERS {
        let mid = (low + high) / 2.0;
        let r = fsrs7_forgetting_curve_without_lut(mid, stability);
        if r > TARGET_RETRIEVABILITY {
            low = mid;
        } else {
            high = mid;
        }
    }

    ((low + high) / 2.0).clamp(0.0, S_MAX)
}

pub fn criterion_benchmark(c: &mut Criterion) {
    c.bench_function("anki_tag_parse", |b| b.iter(|| anki_directive_benchmark()));

    let fsrs = FSRS::new(&DEFAULT_PARAMETERS).expect("default FSRS parameters are valid");

    c.bench_function("fsrs_s90_from_stability", |b| {
        b.iter(|| {
            fsrs.interval_at_retrievability(
                MemoryState {
                    stability: black_box(30.0),
                    difficulty: black_box(5.0),
                },
                black_box(TARGET_RETRIEVABILITY),
            )
        })
    });

    let mut group = c.benchmark_group("fsrs_s90_from_stability_batch");
    group.throughput(Throughput::Elements(STABILITY_SAMPLES.len() as u64));
    group.bench_function("14_stabilities", |b| {
        b.iter(|| {
            let total_s90: f32 = STABILITY_SAMPLES
                .iter()
                .map(|stability| {
                    fsrs.interval_at_retrievability(
                        MemoryState {
                            stability: black_box(*stability),
                            difficulty: black_box(5.0),
                        },
                        black_box(TARGET_RETRIEVABILITY),
                    )
                })
                .sum();

            black_box(total_s90)
        })
    });
    group.finish();

    c.bench_function("fsrs_retrievability_from_stability_elapsed", |b| {
        b.iter(|| {
            fsrs.current_retrievability(
                MemoryState {
                    stability: black_box(30.0),
                    difficulty: black_box(5.0),
                },
                black_box(21.0),
            )
        })
    });

    c.bench_function("fsrs_retrievability_scalar_from_stability_elapsed", |b| {
        b.iter(|| fsrs7_retrievability_scalar(black_box(30.0), black_box(21.0)))
    });

    c.bench_function("fsrs_retrievability_with_model_build", |b| {
        b.iter(|| {
            FSRS::new(&DEFAULT_PARAMETERS)
                .expect("default FSRS parameters are valid")
                .current_retrievability(
                    MemoryState {
                        stability: black_box(30.0),
                        difficulty: black_box(5.0),
                    },
                    black_box(21.0),
                )
        })
    });

    c.bench_function("fsrs_r_and_s90_reuse_model", |b| {
        b.iter(|| {
            let memory_state = MemoryState {
                stability: black_box(30.0),
                difficulty: black_box(5.0),
            };
            let retrievability = fsrs.current_retrievability(memory_state, black_box(21.0));
            let s90 =
                fsrs.interval_at_retrievability(memory_state, black_box(TARGET_RETRIEVABILITY));

            black_box((retrievability, s90))
        })
    });

    c.bench_function("fsrs_r_scalar_and_s90_reuse_model", |b| {
        b.iter(|| {
            let memory_state = MemoryState {
                stability: black_box(30.0),
                difficulty: black_box(5.0),
            };
            let retrievability =
                fsrs7_retrievability_scalar(memory_state.stability, black_box(21.0));
            let s90 =
                fsrs.interval_at_retrievability(memory_state, black_box(TARGET_RETRIEVABILITY));

            black_box((retrievability, s90))
        })
    });

    c.bench_function("fsrs_r_and_s90_build_model_per_metric", |b| {
        b.iter(|| {
            let memory_state = MemoryState {
                stability: black_box(30.0),
                difficulty: black_box(5.0),
            };
            let retrievability = FSRS::new(&DEFAULT_PARAMETERS)
                .expect("default FSRS parameters are valid")
                .current_retrievability(memory_state, black_box(21.0));
            let s90 = FSRS::new(&DEFAULT_PARAMETERS)
                .expect("default FSRS parameters are valid")
                .interval_at_retrievability(memory_state, black_box(TARGET_RETRIEVABILITY));

            black_box((retrievability, s90))
        })
    });

    c.bench_function("fsrs_r_and_s90_without_lut", |b| {
        b.iter(|| {
            let memory_state = MemoryState {
                stability: black_box(30.0),
                difficulty: black_box(5.0),
            };
            let retrievability = fsrs.current_retrievability(memory_state, black_box(21.0));
            let s90 = fsrs7_s90_without_lut(memory_state.stability);

            black_box((retrievability, s90))
        })
    });

    let retrievability_samples: Vec<(f32, f32)> = (0..RETRIEVABILITY_SAMPLE_COUNT)
        .map(|idx| {
            let stability = 0.1 + (idx % 3650) as f32;
            let elapsed_days = (idx % 730) as f32;
            (stability, elapsed_days)
        })
        .collect();

    let mut group = c.benchmark_group("fsrs_retrievability_batch");
    group.throughput(Throughput::Elements(retrievability_samples.len() as u64));
    group.bench_function("4096_cards_reuse_model", |b| {
        b.iter(|| {
            let total_retrievability: f32 = retrievability_samples
                .iter()
                .map(|(stability, elapsed_days)| {
                    fsrs.current_retrievability(
                        MemoryState {
                            stability: black_box(*stability),
                            difficulty: black_box(5.0),
                        },
                        black_box(*elapsed_days),
                    )
                })
                .sum();

            black_box(total_retrievability)
        })
    });
    group.bench_function("4096_cards_scalar", |b| {
        b.iter(|| {
            let total_retrievability: f32 = retrievability_samples
                .iter()
                .map(|(stability, elapsed_days)| {
                    fsrs7_retrievability_scalar(black_box(*stability), black_box(*elapsed_days))
                })
                .sum();

            black_box(total_retrievability)
        })
    });
    group.finish();

    let mut group = c.benchmark_group("fsrs_r_and_s90_batch");
    group.throughput(Throughput::Elements(retrievability_samples.len() as u64));
    group.bench_function("4096_cards_reuse_model", |b| {
        b.iter(|| {
            let total_metrics: f32 = retrievability_samples
                .iter()
                .map(|(stability, elapsed_days)| {
                    let memory_state = MemoryState {
                        stability: black_box(*stability),
                        difficulty: black_box(5.0),
                    };
                    fsrs.current_retrievability(memory_state, black_box(*elapsed_days))
                        + fsrs.interval_at_retrievability(
                            memory_state,
                            black_box(TARGET_RETRIEVABILITY),
                        )
                })
                .sum();

            black_box(total_metrics)
        })
    });
    group.bench_function("4096_cards_scalar_r_reuse_model_for_s90", |b| {
        b.iter(|| {
            let total_metrics: f32 = retrievability_samples
                .iter()
                .map(|(stability, elapsed_days)| {
                    let memory_state = MemoryState {
                        stability: black_box(*stability),
                        difficulty: black_box(5.0),
                    };
                    fsrs7_retrievability_scalar(memory_state.stability, black_box(*elapsed_days))
                        + fsrs.interval_at_retrievability(
                            memory_state,
                            black_box(TARGET_RETRIEVABILITY),
                        )
                })
                .sum();

            black_box(total_metrics)
        })
    });
    group.bench_function("4096_cards_build_model_per_metric", |b| {
        b.iter(|| {
            let total_metrics: f32 = retrievability_samples
                .iter()
                .map(|(stability, elapsed_days)| {
                    let memory_state = MemoryState {
                        stability: black_box(*stability),
                        difficulty: black_box(5.0),
                    };
                    FSRS::new(&DEFAULT_PARAMETERS)
                        .expect("default FSRS parameters are valid")
                        .current_retrievability(memory_state, black_box(*elapsed_days))
                        + FSRS::new(&DEFAULT_PARAMETERS)
                            .expect("default FSRS parameters are valid")
                            .interval_at_retrievability(
                                memory_state,
                                black_box(TARGET_RETRIEVABILITY),
                            )
                })
                .sum();

            black_box(total_metrics)
        })
    });
    group.bench_function("4096_cards_without_lut", |b| {
        b.iter(|| {
            let total_metrics: f32 = retrievability_samples
                .iter()
                .map(|(stability, elapsed_days)| {
                    let memory_state = MemoryState {
                        stability: black_box(*stability),
                        difficulty: black_box(5.0),
                    };
                    fsrs.current_retrievability(memory_state, black_box(*elapsed_days))
                        + fsrs7_s90_without_lut(memory_state.stability)
                })
                .sum();

            black_box(total_metrics)
        })
    });
    group.finish();
}

criterion_group!(benches, criterion_benchmark);
criterion_main!(benches);
