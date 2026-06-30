# MCAT Study App (Anki fork)

> An independent, AGPL-3.0-or-later fork of [Anki](https://apps.ankiweb.net),
> being turned into an all-in-one **MCAT** study app.
> This fork is **not** intended to be merged upstream. Full credit to Anki below.

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

## What this is (and isn't) — Phase 1 scope

**Phase 1 headline: "the core works on both screens, no AI."**

Phase 1 is deliberately narrow. It proves the foundation is real:

- A forked Anki that **builds from source** on desktop (and a phone build, deferred — see below).
- **One substantive change inside the Rust engine** — a concept-aware review queue + a
  per-concept Need-to-Review (NTR) signal + a mastery query — layered on top of Anki's
  FSRS scheduler.
- A **review loop** running on a real MCAT deck (the community **AnKing MCAT** deck).
- An honest **Memory** score (point estimate + range) with a written give-up rule, plus a
  topic **coverage map**.
- A **desktop installer** that runs on a clean machine.

**Explicitly out of scope for Phase 1 (no AI before Friday):**

- No AI of any kind — no model calls, no generated cards, no chatbot.
- No Performance or Readiness scores yet (those come later and must stay separate from Memory).
- No two-way sync yet, no lessons viewer, no question bank, no onboarding, no reimagined dashboard.

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
