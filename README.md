# Anki

> **This is a brownfield fork of [Anki](https://apps.ankiweb.net)** that adapts its Rust engine for
> **CFA Level I** exam preparation with evidence-based study features. It is built on, and credits,
> upstream Anki (AGPL-3.0-or-later). The upstream project README follows the CFA section below.

## CFA Level I Speedrun — implemented vs planned

**Exam:** CFA Level I — pass/fail, 180 standalone A/B/C MCQs, 10 topic areas.
**Thesis:** engineer the *schedule and selection* of cards (not just their spacing) to bridge
**Memory → Performance → Readiness** beyond what plain spaced repetition + FSRS already do.

Design & evidence: [`brainlift.md`](./brainlift.md) · [`PRD.md`](./PRD.md) ·
[`RESEARCH_ADDENDUM.md`](./RESEARCH_ADDENDUM.md) — revised plans:
[`PHASE1_PLAN_V2.md`](./PHASE1_PLAN_V2.md) · [`PHASE2_PLAN_V2.md`](./PHASE2_PLAN_V2.md) ·
[`PHASE3_PLAN_V2.md`](./PHASE3_PLAN_V2.md) — decisions:
[`PLAN_CHANGELOG.md`](./PLAN_CHANGELOG.md) · [`GRILLING_NOTES.md`](./GRILLING_NOTES.md).

### ✅ Implemented so far (Phase 1 — engine + desktop + mobile, tested)

**Engine — Rust (`rslib/`), the real in-engine change:**

- **Contrast scheduling (SPOV 1 + SPOV 3)** — tag-derived confusable clusters; a post-gather pass in
  `build_queues` reorders the queue so confusable cards surface **adjacently**. Per-deck toggle
  `contrast_scheduling` (+ `contrast_tag_prefix`). Evidence-refined this cycle: it runs on the
  **merged** queue so the new/review intersperser can't split a pair (C3); clusters are kept
  **within a single topic** (R28); and a **sibling-adjacency guard** keyed on `note_id` (C10). Pure
  reordering — no schema change, no new synced table.
- **Per-topic mastery / metrics RPCs** on `StatsService` — `TopicMastery`, `GetDashboard`,
  `GetConceptGraph`, each a single SQL pass (fast on large decks); exposed via the Python
  `Collection` wrapper (C14).

**Desktop UI — TypeScript/Svelte (`ts/routes/`):**

- **Three-gauge CFA dashboard** (Memory / Performance / Readiness) with **honest uncertainty**:
  Readiness **abstains by default** unless `≥300 graded reviews AND ≥70% topic coverage AND ≥50
  held-out probe items` (R1), and names the missing input; no point pass-probability on thin data
  (the auto-fail guard). A labelled `?readinessTest=1` dev mode exercises the pipeline without ever
  showing a "real" number. Gauges **abstain when FSRS is off** rather than use a lenient proxy (C11).
- **Versioned topic weights** `(min, max, midpoint)` + a configurable MPS band (R26/C12).
- **Concept-graph visualization** (D3 force graph); the FSRS-state legend was relabelled
  **"Mastered" → "High recall"** (honesty).

**Two apps, one engine + sync (R2):**

- Desktop and **AnkiDroid** run the **same Rust engine** (rsdroid `.aar` rebuilt from this fork), so
  the contrast change and toggles take effect on both.
- **Two-way sync** via a self-hosted Anki sync server (reviews, tags, deck config), using Anki's
  native conflict rule (append-only revlog + last-write-wins `mtime`) — no lost or double-counted reviews.

**Tests / build:** Rust unit + integration tests (contrast, merged-queue adjacency, sibling guard,
within-topic, mastery) plus Python `test_stats`; `just check` is green (the desktop **installer
packaging** test needs the Xcode/codesigning toolchain and is unrelated to these changes).

### ⬜ Planned — not yet implemented

**Phase 2 — the Performance layer:**

- **SPOV 2 fade ladder** — worked → faded (cloze) → solve (A/B/C MCQ), gated on **FSRS
  retrievability at the exam horizon** with hysteresis; promotion on **spaced-session count**;
  mandatory **feedback** after every rung.
- **Signed confusability gate** (moved here from Phase 1 — it needs a real signal, not manual
  labeling): computed from **behavioral confusion mining** (must beat BM25/dense) or curated at authoring time.
- **AI content pipeline** — validated item + misconception-distractor generation and
  retrieval-for-grounding (source-traceable, gold-set cutoff, beats a simpler baseline);
  authoring-time only, runtime stays AI-free.

**Phase 3 — Readiness & proof:**

- **Calibrated readiness** — Beta-Binomial band + Platt/Venn-Abers calibration, a second honest
  number (classification accuracy at the MPS), conformal backstop.
- **Held-out testing** — the 30×2 delayed paraphrase set (the memory→performance bridge proof), a
  one-command benchmark, and a leakage scan.
- **Three-arm ablation** (feature ON / OFF / vanilla Anki), readiness-optimization allocation
  (SPOV 4), and BYO/untagged-deck edge sourcing.

### Build & run (this fork)

- **Desktop:** `just run` (see [`CLAUDE.md`](./CLAUDE.md) / [`docs/`](./docs) for the toolchain).
- **Mobile (AnkiDroid):** rebuild the engine `.aar` from this fork and install — set
  `ANDROID_NDK_HOME`, then `./gradlew :rsdroid:assembleRelease` in `Anki-Android-Backend` and
  `./gradlew installPlayDebug` in `Anki-Android` (`local_backend=true`). See [`ANDROID_PORTING.md`](./ANDROID_PORTING.md).
- **Sync:** run a self-hosted sync server — `SYNC_USER1=user:pass SYNC_BASE=<dir> SYNC_PORT=<port>
  python -m anki.syncserver` — and point both apps at it (use a port **other than** the desktop's
  web-view port to avoid a collision).

---

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
