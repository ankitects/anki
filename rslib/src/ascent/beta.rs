// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Exact Beta posterior quantiles, ported from `scoring_reference.py`.
//!
//! Explicitly **not** Wald: at 5 correct out of 5, Wald reports a zero-width
//! interval at 100%, which is a literal implementation of the failure mode
//! this product exists to prevent (report.md §3.2).

/// Continued-fraction expansion of the incomplete beta function (Lentz's
/// method), the standard Numerical-Recipes `betacf`.
fn betacf(a: f64, b: f64, x: f64) -> f64 {
    const MAXIT: usize = 300;
    const EPS: f64 = 3e-16;
    const FPMIN: f64 = 1e-300;
    let (qab, qap, qam) = (a + b, a + 1.0, a - 1.0);
    let mut c = 1.0;
    let mut d = 1.0 - qab * x / qap;
    if d.abs() < FPMIN {
        d = FPMIN;
    }
    d = 1.0 / d;
    let mut h = d;
    for m in 1..=MAXIT {
        let m = m as f64;
        let m2 = 2.0 * m;
        let mut aa = m * (b - m) * x / ((qam + m2) * (a + m2));
        d = 1.0 + aa * d;
        if d.abs() < FPMIN {
            d = FPMIN;
        }
        c = 1.0 + aa / c;
        if c.abs() < FPMIN {
            c = FPMIN;
        }
        d = 1.0 / d;
        h *= d * c;
        aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
        d = 1.0 + aa * d;
        if d.abs() < FPMIN {
            d = FPMIN;
        }
        c = 1.0 + aa / c;
        if c.abs() < FPMIN {
            c = FPMIN;
        }
        d = 1.0 / d;
        let de = d * c;
        h *= de;
        if (de - 1.0).abs() < EPS {
            break;
        }
    }
    h
}

fn ln_gamma(x: f64) -> f64 {
    // Lanczos approximation, g=7, n=9. Accurate to ~1e-13 for x > 0, which is
    // far tighter than the bisection tolerance below needs.
    const C: [f64; 9] = [
        0.999_999_999_999_809_9,
        676.520_368_121_885_1,
        -1_259.139_216_722_402_8,
        771.323_428_777_653_1,
        -176.615_029_162_140_6,
        12.507_343_278_686_905,
        -0.138_571_095_265_720_12,
        9.984_369_578_019_572e-6,
        1.505_632_735_149_311_6e-7,
    ];
    if x < 0.5 {
        // reflection formula
        (std::f64::consts::PI / (std::f64::consts::PI * x).sin()).ln() - ln_gamma(1.0 - x)
    } else {
        let x = x - 1.0;
        let mut a = C[0];
        let t = x + 7.5;
        for (i, c) in C.iter().enumerate().skip(1) {
            a += c / (x + i as f64);
        }
        0.5 * (2.0 * std::f64::consts::PI).ln() + (x + 0.5) * t.ln() - t + a.ln()
    }
}

/// Regularised incomplete beta function I_x(a, b).
pub(crate) fn beta_cdf(a: f64, b: f64, x: f64) -> f64 {
    if x <= 0.0 {
        return 0.0;
    }
    if x >= 1.0 {
        return 1.0;
    }
    let lb = ln_gamma(a + b) - ln_gamma(a) - ln_gamma(b);
    let front = (lb + a * x.ln() + b * (1.0 - x).ln()).exp();
    if x < (a + 1.0) / (a + b + 2.0) {
        front * betacf(a, b, x) / a
    } else {
        1.0 - front * betacf(b, a, 1.0 - x) / b
    }
}

/// Inverse CDF by bisection. 200 halvings is far past f64 resolution; it
/// matches the reference implementation rather than optimising it.
pub(crate) fn beta_ppf(p: f64, a: f64, b: f64) -> f64 {
    let (mut lo, mut hi) = (0.0f64, 1.0f64);
    for _ in 0..200 {
        let mid = (lo + hi) / 2.0;
        if beta_cdf(a, b, mid) < p {
            lo = mid;
        } else {
            hi = mid;
        }
    }
    (lo + hi) / 2.0
}

/// Equal-tailed central interval at `level` (0.80 gives the 10th/90th
/// percentiles).
pub(crate) fn beta_interval(a: f64, b: f64, level: f64) -> (f64, f64) {
    let tail = (1.0 - level) / 2.0;
    (beta_ppf(tail, a, b), beta_ppf(1.0 - tail, a, b))
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn cdf_matches_known_values() {
        // Beta(1,1) is uniform.
        assert!((beta_cdf(1.0, 1.0, 0.25) - 0.25).abs() < 1e-12);
        // Beta(2,2) CDF is 3x^2 - 2x^3.
        for x in [0.1, 0.5, 0.9] {
            let expected = 3.0 * x * x - 2.0 * x * x * x;
            assert!((beta_cdf(2.0, 2.0, x) - expected).abs() < 1e-12, "x={x}");
        }
        // symmetry
        assert!((beta_cdf(3.0, 5.0, 0.4) - (1.0 - beta_cdf(5.0, 3.0, 0.6))).abs() < 1e-12);
    }

    #[test]
    fn ppf_inverts_cdf() {
        for (a, b) in [(2.0, 2.0), (7.0, 3.0), (0.5, 40.0)] {
            for p in [0.1, 0.5, 0.9] {
                let x = beta_ppf(p, a, b);
                assert!((beta_cdf(a, b, x) - p).abs() < 1e-9, "a={a} b={b} p={p}");
            }
        }
    }

    /// The headline reason this module exists: the interval after five
    /// straight correct answers is nowhere near zero-width, and its upper
    /// bound stays strictly below certainty.
    #[test]
    fn five_of_five_is_not_certainty() {
        let (lo, hi) = beta_interval(2.0 + 5.0, 2.0, 0.80);
        assert!(hi < 1.0, "upper bound must stay below certainty, got {hi}");
        assert!((hi - lo) > 0.25, "width was {}", hi - lo);
        // matches report.md §3.2: Beta(2,2) posterior 0.78, 80% [0.59, 0.93]
        assert!(
            (lo - 0.59).abs() < 0.02 && (hi - 0.93).abs() < 0.02,
            "{lo} {hi}"
        );
    }
}
