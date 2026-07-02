# MCAT Speedrun — a study app built on Anki

**Exam:** [MCAT](https://students-residents.aamc.org/about-mcat-exam/about-mcat-exam) — scored **472–528**, four sections each scored **118–132** (Chem/Phys, CARS, Bio/Biochem, Psych/Soc). A large fact base plus reading passages; the hard part is covering it all.

**MCAT Speedrun** is a fork of [Anki](https://apps.ankiweb.net) with a **desktop app** and an **iOS companion** that share one engine — Anki's Rust core. It adds a real change inside that Rust engine (a per-topic mastery query), an **honest memory-readiness dashboard**, and MCAT-tuned study flows, on top of the MileDown MCAT deck.

This is a fork of Anki and is distributed under **AGPL-3.0-or-later**, with credit to Anki and its contributors (see [Upstream Anki](#upstream-anki) and [LICENSE](./LICENSE)). Some parts of Anki are BSD-3-Clause.

Development happens on the `mcat-speedrun-fork` branch.

## Status

What runs today (the "core works on both screens, no AI" milestone):

- ✅ Anki forked and **building from source**.
- ✅ A real **Rust engine change** — a per-topic mastery query — with **23 Rust unit tests + 3 Python tests** (see [The Rust engine change](#the-rust-engine-change)).
- ✅ **Desktop** review loop on the MCAT deck, with interleaved study ("Start Flashcards" / "Focus a Category").
- ✅ An **honest memory-readiness dashboard**: per-topic recall, an overall recall **range** (confidence interval), topic coverage, a "how sure" indicator, the next best topic, and a **give-up rule** that shows no score when there isn't enough data.
- ✅ A **macOS desktop installer** (Briefcase DMG) that ships two profiles — a clean-slate default and a pre-seeded demo profile.
- ✅ An **iOS companion** that builds and runs on the simulator, loads the MCAT deck, and runs a real review session **on the shared Rust engine** (via the C FFI).

Not built yet (later milestones, tracked honestly): AI card generation/checks, two-way desktop⇄phone sync, the phone's own dashboard, a projected exam-score model, and calibration/ablation evidence. The dashboard measures **memory readiness only** — it does **not** claim a projected MCAT score.

## Architecture — two apps, one engine

Both apps drive the same Anki Rust core (`rslib`); neither reimplements the scheduler.

- **Core engine** — Rust in `rslib/` (FSRS scheduling, storage, our mastery query). Exposed to other layers over protobuf.
- **Desktop** — Python/Qt in `qt/aqt/` embedding Svelte/TypeScript web views (`ts/`), talking to the engine through the PyO3 bridge (`pylib/rsbridge`).
- **iOS companion** — SwiftUI in `ios/AnkiMCAT/`, calling the **same Rust core** through a thin C ABI (`rslib/ios/`, built as an `xcframework`) with swift-protobuf messages. No scheduler is rewritten in Swift.

## Build & run

Everything is wrapped in the project `justfile` (`just --list`). Do not call `./ninja`/`./run` directly.

**Desktop (development):**

```
just run                 # build pylib + qt and launch Anki
```

**Desktop installer (macOS):**

```
just installer           # -> out/installer/dist/anki-26.05-mac-apple.dmg
```

The DMG is unsigned/unnotarized (no certs), so on first launch clear Gatekeeper with
right-click → Open, or `xattr -dr com.apple.quarantine "/Applications/Anki.app"`.

**iOS companion (simulator):**

```
cd ios/AnkiMCAT && ./build-sim.sh run     # requires xcodegen + an iOS simulator runtime
```

**Tests for the engine change:**

```
cargo test -p anki tag_mastery                    # 23 Rust unit tests
PYTHONPATH=out/pylib ./out/pyenv/bin/python \
    -m pytest pylib/tests/test_tag_mastery.py     # 3 Python tests (calling the Rust RPC)
```

The MCAT deck (`MCAT_Milesdown.apkg`) and derived seed decks are large binaries kept out of
git; the installer's demo profile is regenerated with
`PYTHONPATH=out/pylib ./out/pyenv/bin/python qt/tools/generate_demo_seed.py`.

## The Rust engine change

The primary engine change is a **per-topic mastery query** in the Rust core (challenge "Mastery
query"): a backend RPC that returns, for each topic (the `::` tag hierarchy, depth 2), how many
cards are mastered and the average current FSRS recall, plus the honest-score aggregates
(coverage, confidence interval, "how sure", next best topic, give-up rule). It runs over a
session-local search table and is **read-only** (proven by a test), so undo and collection
integrity are unaffected.

A second engine change adds the **"I never learned this"** flow: a topic-level bulk tag + suspend
operation (`Op::SetNeverLearned`) with its own RPCs.

**Why Rust, not Python:** the mastery query aggregates recall over every card in the collection
and must power the dashboard on large decks (target: 50k cards) within the speed budget; doing it
in the Rust core keeps it on the engine's SQLite/search path and ships the same logic to both the
desktop and the phone, rather than duplicating it per platform.

**Upstream files touched (merge surface):**

| File | Change | Merge risk |
|---|---|---|
| `proto/anki/stats.proto` | honest-score fields on `TagMasteryResponse`; `CardTopics` RPC | low (additive) |
| `proto/anki/tags.proto` | never-learned RPCs | low (additive) |
| `rslib/src/stats/tag_mastery.rs` | mastery query + honest-score computation (+ 23 tests) | low (new module logic) |
| `rslib/src/stats/service.rs` | dispatch the new stats RPCs | low (additive) |
| `rslib/src/tags/never_learned.rs` | new module (bulk tag + suspend) | low (new file) |
| `rslib/src/tags/{mod.rs,service.rs}` | wire the never-learned module + RPCs | low (additive) |
| `rslib/src/ops.rs` | add `SetNeverLearned` op | low (additive enum arm) |
| `rslib/ios/{Cargo.toml,anki_ios.h,src/lib.rs}` | new C ABI shim for the iOS engine | none upstream (new crate) |
| `rslib/src/storage/sqlite.rs`, `rslib/src/media/files.rs` | iOS storage guards | low (small, cfg-gated) |

New non-engine code lives under `qt/aqt/` (dashboard, interleaving, never-learned UI),
`ts/routes/mastery/` (dashboard view), and `ios/AnkiMCAT/` (the companion app).

## The honest memory-readiness score

Every number on the Topic Mastery dashboard comes with its context, per the honesty rule:

- **Per-topic memory score** — mean *current* FSRS retrievability over that topic's cards that
  actually have memory state (the honest denominator: "scored" vs "total").
- **Overall recall as a range** — a confidence interval, not a single number.
- **Coverage** — how many topics have been studied vs the total.
- **How sure** — a confidence indicator driven by sample size and interval width.
- **Next best topic** — the single most useful thing to study next.
- **Give-up rule** — when there isn't enough graded history across enough topics, the dashboard
  **abstains** and shows no overall score rather than guessing.

---

# Upstream Anki

The sections below are from the upstream Anki project this repo is forked from.

[![Build Status](https://github.com/ankitects/anki/actions/workflows/ci.yml/badge.svg)](https://github.com/ankitects/anki/actions/workflows/ci.yml)
[![Documentation](https://img.shields.io/badge/docs-dev--docs.ankiweb.net-blue)](https://dev-docs.ankiweb.net)

This repo contains the source code for the computer version of
[Anki](https://apps.ankiweb.net).

## About

Anki is a spaced repetition program. Please see the [website](https://apps.ankiweb.net) to learn more.

## Getting Started

### Contributing

Want to contribute to Anki? Check out the [Contribution Guidelines](./docs/contributing.md).

For more information on building and developing, please see [Development](./docs/development.md).

#### Contributors

The following people have contributed to Anki: [CONTRIBUTORS](./CONTRIBUTORS)

### Anki Betas

If you'd like to try development builds of Anki but don't feel comfortable
building the code, please see [Anki betas](https://betas.ankiweb.net/).

## License

Anki's license: [LICENSE](./LICENSE)
