// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::hint::black_box;
use std::time::Instant;

use super::query_decay;
use super::softplus;
use super::Norm;

fn compare(name: &str, mut run: impl FnMut(bool)) {
    let mut samples = [Vec::new(), Vec::new()];
    run(false);
    run(true);
    for round in 0..15 {
        for optimized in if round % 2 == 0 {
            [false, true]
        } else {
            [true, false]
        } {
            let start = Instant::now();
            run(optimized);
            samples[usize::from(optimized)].push(start.elapsed().as_secs_f64() * 1000.0);
        }
    }
    for values in &mut samples {
        values.sort_by(f64::total_cmp);
    }
    let before = samples[0][7];
    let after = samples[1][7];
    println!(
        "{name}: before_ms={before:.3} after_ms={after:.3} speedup={:.3}",
        before / after
    );
}

#[test]
#[ignore = "manual native math throughput benchmark"]
fn rwkv_query_math_benchmark() {
    let rows = 128;
    for (dim, groups) in [(128, 1), (128, 4), (512, 1)] {
        let norm = Norm {
            dim,
            groups,
            eps: 1e-5,
            weight: (0..dim).map(|i| (i as f32 * 0.17).sin()).collect(),
            bias: (0..dim).map(|i| (i as f32 * 0.03).cos()).collect(),
        };
        let input = (0..rows * dim)
            .map(|i| (i as f32 * 0.37).sin())
            .collect::<Vec<_>>();
        let mut out = vec![0.0; input.len()];
        compare(&format!("norm_dim_{dim}_groups_{groups}"), |optimized| {
            for _ in 0..128 {
                let input = black_box(input.as_slice());
                if optimized {
                    norm.apply_batch(input, rows, &mut out);
                } else {
                    for (input, output) in input.chunks(dim).zip(out.chunks_mut(dim)) {
                        norm.apply_into(input, output);
                    }
                }
                black_box(&out);
            }
        });
    }
    let input = (0..16_384)
        .map(|i| (i as f32 * 0.17).sin() * 8.0)
        .collect::<Vec<_>>();
    let mut out = vec![0.0; input.len()];
    compare("query_decay", |optimized| {
        for _ in 0..128 {
            for (&value, output) in black_box(input.as_slice()).iter().zip(&mut out) {
                *output = if optimized {
                    query_decay(value)
                } else {
                    let decay = -0.5 - softplus(-value);
                    (-decay.exp()).exp()
                };
            }
            black_box(&out);
        }
    });
}
