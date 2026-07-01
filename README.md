# MCAT Study Kernel (Anki fork)

> An independent, AGPL-3.0-or-later fork of [Anki](https://apps.ankiweb.net),
> turned into the **kernel** of an MCAT study tool: Anki's spaced-repetition
> engine, plus the ability to **ingest your past practice tests** and get
> **predictions and review recommendations** back.
> This fork is **not** intended to be merged upstream. Full credit to Anki below.

## The idea: a kernel, not an all-in-one app

MCAT prep is fragmented across understanding (Khan Academy), memorizing (Anki),
and applying (UWorld/AAMC practice tests), and students burn time stitching them
together by hand. A common, evidence-backed workflow is *"do UWorld first, then
add your weak points to Anki."* This fork does **not** try to be an all-in-one
app that replaces all of those. Instead it builds the small, reusable **kernel**
that automates that stitch:

1. **Anki stays Anki** — spaced-repetition review is untouched and is still the
   core of the app.
2. **You ingest the practice tests you already took** — for each question you
   tag the MCAT concept it tested and whether you got it right or wrong
   (Tools → *MCAT: Ingest Practice Test*). No AI, no built-in question bank; the
   data is your own real tests.
3. **The engine re-prioritises review and predicts a score** — a change inside
   Anki's Rust core turns those annotations into a per-concept **Need-to-Review
   (NTR)** signal that surfaces weak concepts sooner, and a
   prediction/recommendations panel (Tools → *MCAT: Prediction & Review Plan*)
   shows a projected MCAT score, an honest Memory score, and what to study next.

Deliberately **not** in this fork: an in-app quizzing section, a lessons viewer,
and an all-in-one dashboard/home screen. Those are the parts of the "all-in-one"
vision this kernel intentionally leaves out.

## Exam: MCAT

This app is built for **one exam: the MCAT (Medical College Admission Test)**, on
its real, current scoring scale.

| Property            | Value                                                                                                                                                                                                                                                        |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Total score**     | **472–528**                                                                                                                                                                                                                                                  |
| **Sections**        | **4**, each scored **118–132**                                                                                                                                                                                                                               |
| **Section names**   | Chemical & Physical Foundations of Biological Systems (Chem/Phys); Critical Analysis & Reasoning Skills (CARS); Biological & Biochemical Foundations of Living Systems (Bio/Biochem); Psychological, Social & Biological Foundations of Behavior (Psych/Soc) |
| **Total test time** | ~7.5 hours seated (~6h 15m of scored testing)                                                                                                                                                                                                                |
| **Section timing**  | Chem/Phys 95 min (59 Q); CARS 90 min (53 Q); Bio/Biochem 95 min (59 Q); Psych/Soc 95 min (59 Q)                                                                                                                                                              |

The hard part of the MCAT is **coverage**: a huge fact base across four sections plus
reading-passage reasoning. This fork leans into that — every card is annotated with the
MCAT concept(s) it covers so the engine can reason per-concept (see Phase 1 scope below).

> The MCAT scale and section structure above are stated here per the project spec's
> "pick one exam / state it at the top of your README" rule.

## What the kernel does (and deliberately doesn't)

**Headline: "the core works, no AI." The kernel is deterministic.**

What it delivers:

- A forked Anki that **builds from source** on desktop (and a phone build, deferred — see below).
- **One substantive change inside the Rust engine** — a concept-aware review queue + a
  per-concept Need-to-Review (NTR) signal + a mastery query — layered on top of Anki's
  FSRS scheduler.
- A **review loop** running on a real MCAT deck (the community **AnKing MCAT** deck),
  unchanged from Anki.
- **Practice-test ingestion** — upload/annotate your past tests (concept + right/wrong);
  those results feed NTR and re-prioritise review toward your weak concepts.
- A **prediction & review-plan panel**: a projected MCAT score (**Readiness**) from your
  ingested tests, an honest **Memory** (card-recall) score with a written give-up rule and a
  topic **coverage map**, and the per-concept **NTR** recommendations chart. The three
  scores are shown **separately and never blended**.
- A **desktop installer** that runs on a clean machine.

**Deliberately out of scope (the "all-in-one" parts this kernel does not build):**

- No AI of any kind — no model calls, no generated cards, no chatbot. Every number is a
  deterministic function of your review history and ingested test results.
- No in-app **quizzing section** and no built-in question bank — you ingest your *own* real
  practice tests instead.
- No lessons/understanding viewer, no onboarding flow, and no all-in-one **dashboard**
  home screen. Anki opens normally to its deck list.
- No two-way sync yet.
- The Readiness projection is an explicit, **unvalidated** heuristic (see the panel's
  disclaimer), not a calibrated score model yet.

See [`speedrun_plans/PRD-PHASE1.md`](speedrun_plans/PRD-PHASE1.md) for the full Phase 1 PRD
and [`speedrun_plans/speedrun_spec.txt`](speedrun_plans/speedrun_spec.txt) for the project spec.

## Building the desktop app from source

The desktop app builds with Anki's existing build system, driven by the
[`just`](https://just.systems) command runner. **Use the `just` recipes** — do not invoke
`./ninja`, `./run`, or the scripts under `./tools/` directly.

### Prerequisites (all platforms)

- **Rustup** — https://rustup.rs/ . The Rust version pinned in `rust-toolchain.toml`
  downloads automatically.
- **N2 or Ninja** — install N2 with `tools/install-n2` (on Windows: `bash tools\install-n2`,
  or `C:\msys64\usr\bin\bash.exe tools/install-n2` if WSL conflicts with MSYS2 bash).
- **just** (command runner) — `uv tool install just` or `brew install just`.
- Keep the checkout path **short and free of spaces**, especially on Windows.

Platform-specific setup (MSVC build tools, MSYS2, PATH, etc.):

- Windows: [`docs/windows.md`](docs/windows.md)
- macOS: [`docs/mac.md`](docs/mac.md)
- Linux: [`docs/linux.md`](docs/linux.md)

### Build & run

```sh
just run            # build pylib + qt and launch (development build)
just run-optimized  # release-optimized build (slower to compile, faster to run)
```

The first build downloads and compiles many dependencies and will take a while. Web views
are served at `http://localhost:40000/_anki/pages/` during development.

### Check / test before committing

```sh
just check          # format + main build + checks (run this before marking work done)
just test-rust      # Rust unit tests
just test-py        # Python tests
just test-ts        # TypeScript/Svelte tests
```

Run `just --list` for the full set of recipes.

### Desktop installer (clean-machine build)

A Briefcase-based installer is configured under [`qt/installer/`](qt/installer/). To build it
(produces an MSI on Windows, a `.dmg` on macOS, a tarball on Linux, under
`out/installer/dist/`):

```sh
# Windows
tools\build-installer.bat
# macOS / Linux
tools/build-installer
```

There is currently **no `just` recipe** for the installer; the wrapper above runs
`tools/ninja installer` with `RELEASE=2`. See
[`qt/installer/MCAT_FORK_NOTES.md`](qt/installer/MCAT_FORK_NOTES.md) for branding notes and
clean-machine acceptance steps.

## Phone build (deferred follow-up)

A phone companion that runs review sessions on the **same shared Rust engine** is required by
the project spec, but in this fork it is a **deferred follow-up**, planned as an
**AnkiDroid-based** build (AnkiDroid already embeds Anki's Rust backend, so the engine is
shared rather than re-implemented). Phase 1's mandatory Rust change is written so it also
functions on the phone build. The flashcards-only mobile review session and (later) two-way
sync are tracked in the PRD; a React Native / Expo companion is a Phase 2 item.

## License & credit

This project is licensed under the **GNU Affero General Public License, version 3 or later
(AGPL-3.0-or-later)**, the same license as upstream Anki.

It is an **independent fork of [Anki](https://apps.ankiweb.net)** by Ankitects Pty Ltd and
contributors. Some portions of the upstream code use other licenses (BSD-3-Clause, MIT,
Apache-2.0, CC BY 4.0, etc.). Full attribution, the upstream credit, and the preserved
license notes live in:

- [`LICENSE`](LICENSE) — the upstream AGPL/BSD license notice (unchanged).
- [`ATTRIBUTION.md`](ATTRIBUTION.md) — fork attribution: credit to Anki, the BSD-3-Clause
  portions, and the AnKing MCAT deck source.
- [`CONTRIBUTORS`](CONTRIBUTORS) — upstream contributor list (BSD-licensed contributions).

The MCAT content is based on the community **AnKing MCAT** deck; see `ATTRIBUTION.md`.

---

### About upstream Anki

Anki is a spaced-repetition flashcard program. The upstream project lives at
https://github.com/ankitects/anki and https://apps.ankiweb.net.

- Upstream development docs: [`docs/development.md`](docs/development.md)
- Upstream contribution guidelines: [`docs/contributing.md`](docs/contributing.md)
- Upstream dev docs site: https://dev-docs.ankiweb.net

This fork preserves upstream's source layout: Rust core in `rslib/`, Python library in
`pylib/`, PyQt GUI in `qt/aqt/`, web frontend in `ts/`, and protobuf definitions in `proto/`.
