# MCAT fork — at-scale deck, coverage map & one-command benchmark

This covers the 50,000-card performance benchmark (spec §7h / §10) and the
coverage map at scale (FR-2). Everything here is **deterministic — no AI.**

> **Why a synthetic deck?** The real AnKing MCAT deck (~35k cards) is gated
> behind AnkiHub (subscription) / AnkiWeb (JS + captcha) and can't be pulled
> programmatically here. So we generate a realistic 50k-card stand-in with
> AnKing-style hierarchical tags to exercise performance and the coverage
> pipeline at scale. Importing the **real** deck is a one-liner once you supply
> the `.apkg` (Anki's standard importer; `coverage.py` already accepts `.apkg`).
> Exact tag-pattern validation against the real deck still depends on that file.

## Commands

All commands need the dev toolchain on PATH plus the built `anki` package. In
PowerShell:

```powershell
$machine=[Environment]::GetEnvironmentVariable('Path','Machine')
$user=[Environment]::GetEnvironmentVariable('Path','User')
$env:Path="C:\msys64\usr\bin;$machine;$user"
$env:PYTHONPATH="C:\Users\aryan\projects\anki\out\pylib"
$env:ANKI_TEST_MODE="1"
$py="C:\Users\aryan\projects\anki\out\pyenv\scripts\python.exe"

# 1. Generate the 50k-card deck (deterministic; seed fixed)
& $py mcat\make_synthetic_deck.py --n 50000 --out mcat\fixtures\synthetic_50k.anki2

# 2. Coverage map at scale
& $py mcat\coverage.py --cards mcat\fixtures\synthetic_50k.anki2 --taxonomy mcat\taxonomy.json --out mcat\fixtures\synthetic_50k_coverage.json

# 3. One-command benchmark (spec §7h)
& $py mcat\bench.py --deck mcat\fixtures\synthetic_50k.anki2 --out mcat\fixtures\synthetic_50k_bench.json
```

A `just bench` wrapper is provided (see the root `justfile`).

## Coverage map at scale (50,000 cards)

From `fixtures/synthetic_50k_coverage.json`:

- **Overall: 24/31 concepts covered = 77.42%** (weighted 79.19%).
- Bio/Biochem **100%** (9/9), Chem/Phys **100%** (10/10), Psych/Soc **41.67%** (5/12).
- Uncovered concepts: `6C, 7A, 8A, 8B, 8C, 9B, 10A`.

The Psych/Soc gap (< 50%) deliberately demonstrates the FR-5 give-up rule firing
on a deck that is large but skips a high-weight section (spec §7c) — the
weighted coverage figure keeps such a deck from clearing the bar.

## Benchmark (50,000 cards, 40 timed iterations + 5 warm-up)

From `fixtures/synthetic_50k_bench.json` (measured on the dev machine — Windows
11, debug-profile `rsbridge`):

| RPC                 | rows   | p50     | p95     | worst (max) |
| ------------------- | ------ | ------- | ------- | ----------- |
| `ConceptAwareQueue` | 50,000 | 2879 ms | 3519 ms | 3888 ms     |
| `ConceptMastery`    | 31     | 2820 ms | 3263 ms | 3471 ms     |

### Honest interpretation vs spec §10

⚠️ **Both RPCs are currently ~3 s on 50k cards — well over the spec's "dashboard
first load p95 < 1 s" target.** Reporting this honestly (the spec values true
numbers over flattering ones). Two caveats on the measurement, then the real
cause:

- These numbers are from the **debug-profile** backend (`out/rust/debug/rsbridge.dll`).
  A release build is typically several times faster; the benchmark should be
  re-run against a release `rsbridge` before drawing final conclusions.
- The cost is on the Rust scan side, not the bridge: `ConceptMastery` returns
  only 31 rows yet costs as much as `ConceptAwareQueue` (which returns 50k), so
  serialization is not the bottleneck — the per-card scan is.

**Diagnosed bottleneck (optimization path):** the tag→concept matcher
(`concepts_for_tags` / `tag_matches_pattern` in `rslib/src/concepts/mod.rs`)
re-lowercases every pattern **and** every tag on every comparison — for 50k
cards × 31 concepts × patterns × tags that is tens of millions of throwaway
`String` allocations. Pre-normalising (lowercasing + parsing prefix/glob/exact)
each taxonomy pattern **once** before the per-card loop, and lowercasing each
tag once per card, should bring this comfortably under the 1 s target. This is a
contained change guarded by the existing 7 Rust unit tests. **Tracked as the
next optimization for FR-3.**

The button-press (< 50 ms) and next-card (< 100 ms) targets in §10 are the
standard FSRS answer path, which this change does not touch.
