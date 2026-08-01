# Ascent

**Ascent is a study app for the MCAT** (the Medical College Admission Test),
built as a fork of [Anki](https://github.com/ankitects/anki), the open-source
spaced repetition flashcard program. This is an academic project; the exam it
targets is the MCAT, stated here up front because everything below serves it.

## The thesis

Every study tool measures whether you remember the card — whether the exact
prompt you've seen before still triggers the exact answer you rehearsed. None
of them measure whether you know the thing the card is about. Ascent is built
to measure that gap: to distinguish recognition of a memorized surface from
retrieval of the underlying concept, and to schedule study around the
difference. The full argument lives in the project's accompanying write-up —
see [docs/Brainlift.md](docs/Brainlift.md) (a draft in progress); this
repository holds the engine work that makes it measurable.

## Licence and credit

This is a fork of [ankitects/anki](https://github.com/ankitects/anki). All
credit for the underlying application — the scheduler, the sync protocol, the
desktop app, the whole architecture this project stands on — belongs to
[Damien Elmes and the Anki contributors](https://github.com/ankitects/anki/blob/main/CONTRIBUTORS).
Ascent is a thin layer of changes on top of an excellent, mature codebase.

Like upstream, this fork is licensed under the
**GNU Affero General Public License, version 3 or later**, with portions
contributed by Anki users under the BSD-3 licence — see [LICENSE](LICENSE) and
[CONTRIBUTORS](CONTRIBUTORS), both preserved from upstream. The source of
this fork is public, as the AGPL requires.

## Architecture

One Rust engine, multiple frontends. All scheduling, storage, sync, and card
rendering live in `rslib/`; every frontend talks to it through the same
protobuf RPC API defined in `proto/anki/`.

```
               rslib/  (Rust core: scheduler, storage, sync, rendering)
                  │  protobuf API defined in proto/anki/
      ┌───────────┴──────────────────────┐
      │                                  │
pylib/rsbridge (PyO3)          Anki-Android-Backend ("rsdroid")
      │                          JNI cdylib + Kotlin AAR wrapping
aqt/ + ts/ (desktop:             the same rslib via 3 JNI calls
PyQt shell + Svelte web UI)              │
      │                          Anki-Android (the Android app,
      ▼                          all-Kotlin UI; every scheduling/
   Desktop app                   storage decision is a backend RPC)
                                         │
                                         ▼
                                     Android app
```

The Android path involves two further repositories:
[Anki-Android-Backend](https://github.com/ankidroid/Anki-Android-Backend)
carries this repo as a git submodule and cross-compiles `rslib` into an AAR
exposing exactly three JNI functions (open/close/`runMethodRaw`, protobuf
bytes in and out); [Anki-Android](https://github.com/ankidroid/Anki-Android)
consumes that AAR. Because the whole backend surface is generated from the
proto files, an engine feature added in `rslib/` and exposed as a proto RPC
reaches Android through generated Kotlin with no hand-written bridge code.

This pipeline has been verified end to end for this fork: the engine was
cross-compiled, packaged through a locally-built AAR, and run in an Android
emulator, with a one-line marker change in `rslib` appearing on the device
screen (edit→device loop ≈3–4 minutes). The fork's self-hosted sync server
(`anki-sync-server`, part of `rslib`) builds and runs, so fork desktop ↔
fork Android sync needs no third party.

## What changed in the engine, and why it's in Rust

**Landed: the split review timer.** Upstream Anki records one number per
review — `taken_millis`, the total question-to-answer time, silently capped
at the deck's answer time limit. Ascent's core measurement needs the two
phases separated: how long from seeing the question to revealing the answer
(the retrieval attempt) versus the total. This fork adds a nullable
`reveal_millis` column to the review log (collection schema 19) recording
question→reveal latency, uncapped, with null meaning "not recorded" — never
zero — so old entries and non-reporting clients stay distinguishable from a
genuine instant reveal. Landed in
[PR #2](https://github.com/AdamRoch/speedrun/pull/2); the authoritative
documentation is in [`rslib/src/revlog/mod.rs`](rslib/src/revlog/mod.rs).

It belongs in Rust, not in a frontend, precisely because of the architecture
above: the review log's schema, its sync serialization, and its
import/export all live in `rslib`, and every client — desktop and Android —
writes reviews through the same engine answer path. Recording the timing in
one frontend would produce data only that frontend has and sync would drop;
recording it in the engine means every present and future client gets it for
free through the generated bindings. One compatibility consequence: the new
revlog field changes the sync wire format, so **sync peers and non-legacy
colpkg importers must run this fork's code** (fork↔fork sync works; the
legacy V11 export path drops the column).

**Landed: probes.** A probe is a reworded variant of a card — same fact,
deliberately unfamiliar surface — served instead of the original when FSRS
retrievability is already high, so a review that would have carried almost no
information measures transfer instead. Storage, the scheduler substitution
branch, and outcome logging landed in
[PR #4](https://github.com/AdamRoch/speedrun/pull/4); the authoritative
documentation is [`rslib/src/probe/mod.rs`](rslib/src/probe/mod.rs). Probe
outcomes never feed back into FSRS, and the zero-rate arm is bit-identical to
stock scheduling so the three-arm experiment stays clean.

**Landed: probe generation.** The probes themselves are written offline by
[`pylib/anki/probe_gen.py`](pylib/anki/probe_gen.py) and stored through the
existing `AddProbe` rpc — AI accelerates the measurement but is never a
runtime dependency of the app.

```bash
just probe-gen ~/collection.anki2 --deck Biochem --dry-run   # Claude, nothing stored
just probe-gen ~/collection.anki2 --deck Biochem             # Claude, stored
just probe-gen ~/collection.anki2 --deck Biochem --baseline  # no API key needed
```

Two generators. `claude` (default, `claude-sonnet-5`, requires
`ANTHROPIC_API_KEY`) rewrites the card into a scenario framing. `--baseline`
is a deterministic question/answer inversion with no network access at all —
it is the no-AI fallback the rubric requires, and the comparison arm that
makes the AI probes' value measurable rather than asserted.

Every candidate from either generator passes a quality gate before it is
stored: it must not contain its own answer, must not be a near-copy of the
source question, and — for the AI path — must survive a second model call
that independently judges whether it tests _exactly_ the card's fact. Failures
are rejected, not repaired. On a 40-card mixed-subject deck the AI path
measured a **12.5% rejection rate** (1 answer leak, 4 same-fact rejections);
the baseline measured 0%, which says only that mechanical inversion is
structurally valid, not that it is a good probe. Every stored probe records
its generator, model, date, prompt hash, and the verifier's verdict in the
schema's provenance field.

## Building

### Desktop

Verified on Apple Silicon macOS. Only two tools need installing; the build
fetches its own pinned protoc, Node, uv, and Python into `out/` — do not
install those by hand.

```bash
brew install ninja just
git clone https://github.com/AdamRoch/speedrun && cd speedrun
just build                  # ~2m20s cold
just run -b /tmp/ankibase   # launch against a throwaway collection
```

Use rustup (not Homebrew Rust) so the `rust-toolchain.toml` pin (1.92.0) is
honoured — clippy fails on newer toolchains, and mobile cross-compilation is
impossible without rustup. Full verified setup, measured build times, and the
`just check` gotchas: [docs/BUILD-NOTES.md](docs/BUILD-NOTES.md).

### Android

The Android app is not in this repository; it is built from forks of the two
ankidroid repos above, with this repo wired in as the backend's `anki`
submodule. The pipeline is verified working end to end. Toolchain, in brief
(all installable without accounts or GUI setup):

- JDK 21 (Temurin), rustup with Rust 1.92.0 plus the `aarch64-linux-android`
  std target, Android SDK platform 36, NDK 29.0.14206865, `cargo-ndk`.
- Backend: clone `Anki-Android-Backend`, point its `anki` submodule at this
  fork, run `./build.sh` — produces `rsdroid-release.aar`.
- App: in a sibling `Anki-Android` checkout, set `local_backend=true` in
  `local.properties`, then `./gradlew :AnkiDroid:assemblePlayDebug`.

The definitive, command-exact Android build documentation lives with the
Ascent Android forks, which are being prepared under a separate task; this
section will link to them when they are published.

## Files touched vs upstream

Generated from the actual diff against the upstream base commit
(`4a8673634`). Everything else in the tree is unmodified upstream Anki.

| Path                                                                          | Change                                                          |
| ----------------------------------------------------------------------------- | --------------------------------------------------------------- |
| `rslib/src/revlog/mod.rs`                                                     | `RevlogEntry.reveal_millis` field, docs, serde back-compat test |
| `rslib/src/storage/upgrades/mod.rs`                                           | Schema 18→19 migration; `SCHEMA_MAX_VERSION` bump; tests        |
| `rslib/src/storage/upgrades/schema19_upgrade.sql`                             | Adds nullable `reveal_millis` column to revlog                  |
| `rslib/src/storage/upgrades/schema19_downgrade.sql`                           | Drops the column on downgrade                                   |
| `rslib/src/storage/revlog/add.sql`, `get.sql`, `mod.rs`                       | Read/write the new column                                       |
| `rslib/src/scheduler/answering/mod.rs`                                        | Carry reveal time through the answer path; tests                |
| `rslib/src/scheduler/answering/revlog.rs`, `preview.rs`                       | Populate the field on logged entries                            |
| `rslib/src/scheduler/service/mod.rs`, `answering.rs`                          | Map the proto field to the internal answer                      |
| `rslib/src/scheduler/reviews.rs`, `fsrs/params.rs`, `rslib/src/stats/card.rs` | Construction sites updated for the new field                    |
| `proto/anki/scheduler.proto`                                                  | `CardAnswer.milliseconds_to_reveal` (optional, tag 7)           |
| `proto/anki/stats.proto`                                                      | Expose reveal time in review-log stats                          |
| `pylib/anki/cards.py`, `pylib/anki/scheduler/v3.py`                           | Python API: note the reveal moment, send it on answer           |
| `pylib/anki/exporting.py`, `pylib/anki/importing/anki2.py`                    | Legacy colpkg export/import handling of the column              |
| `pylib/tests/test_schedv3.py`                                                 | Tests for reveal-time recording                                 |
| `qt/aqt/reviewer.py`                                                          | Desktop reviewer stamps the reveal moment                       |
| `ts/routes/card-info/Revlog.svelte`                                           | Show reveal time in the card-info review log                    |
| `ftl/core/card-stats.ftl`                                                     | "Reveal" column translation string                              |
| `docs/BUILD-NOTES.md`                                                         | Verified build setup and mobile-path assessment (new)           |
| `CLAUDE.md`                                                                   | Agent/project instructions for this fork (new)                  |
| `CONTRIBUTORS`                                                                | Fork contributor entry                                          |
| `README.md`                                                                   | This file (replaces upstream's README)                          |
