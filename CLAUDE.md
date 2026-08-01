# Claude Code Configuration

> **Note:** Every command you need — building, running, testing, linting,
> formatting — is defined as a recipe in the project `justfile`. Run
> `just --list` to see them. Do not invoke `./ninja`, `./run`, or scripts
> under `./tools` directly — use the `just` recipes instead.

## Project Overview

Anki is a spaced repetition flashcard program with a multi-layered architecture. Main components:

- Web frontend: Svelte/TypeScript in ts/
- PyQt GUI, which embeds the web components in aqt/
- Python library which wraps our rust Layer (pylib/, with Rust module in pylib/rsbridge)
- Core Rust layer in rslib/
- Protobuf definitions in proto/ that are used by the different layers to
  talk to each other.

## Fork-specific: revlog extensions

This fork's revlog has two added columns. Authoritative docs live in
`rslib/src/revlog/mod.rs`; read it before touching either.

- `reveal_millis` (nullable, schema 19) — question→reveal latency, separate
  from the capped `taken_millis` composite. Null means "not recorded", never
  zero.
- `data` (TEXT NOT NULL DEFAULT '', schema 20) — JSON blob mirroring the
  `cards.data` pattern, currently holding the probe `variant_id`. Put new
  per-review metadata here rather than adding another column.

Revlog rows sync as fixed-arity positional tuples
(`sync/collection/chunks.rs`), so **struct field order must match the SQL
column order** in `storage/revlog/{add,get}.sql` and `row_to_revlog_entry`.
Sync peers and non-legacy colpkg importers must run this fork's code; the
legacy (V11) export path drops both columns.

## Fork-specific: probes

A probe is an AI-generated reworded variant of a card, served instead of the
original when FSRS retrievability is high — see `rslib/src/probe/mod.rs` and
the substitution branch `Collection::maybe_probe_substitute` in
`rslib/src/scheduler/queue/mod.rs`. Probe outcomes must not feed back into
FSRS; the zero-rate arm has to stay bit-identical to stock scheduling
(`zero_rate_leaves_fsrs_scheduling_untouched` guards this).

Two open seams, deliberately unresolved:

- **Probe content does not sync.** The `probes` table is local; the chunked
  sync object lists are closed. Outcomes ride the revlog and do sync. How
  probe text reaches a second device (pre-generated packs, apkg, or
  per-device regeneration) is an open captain decision.
- **Probe generation is not implemented.** Probes arrive via the `AddProbe`
  rpc or apkg import only.

Deck config gotcha: probe settings live on `DeckConfig.Config`, which syncs
via schema11 JSON — a new proto field **silently drops on sync** unless it is
also added to `DeckConfSchema11`, both `From` impls, and
`RESERVED_DECKCONF_KEYS` in `rslib/src/deckconfig/schema11.rs`.

## Running Anki

To build and run Anki in development mode:

```
just run
```

This builds pylib and qt, then launches Anki with debugging enabled. Web
views are served at http://localhost:40000/_anki/pages/ (e.g.,
deckconfig.html). Use `just run-optimized` for a release-optimized build.
For live-reloading during web development, run `just web-watch` in a
separate terminal — it monitors ts/, sass/, and qt/aqt/data/web/ and
auto-rebuilds on changes (`just rebuild-web` triggers a one-off rebuild).

## Building/checking

`just check` will format the code and run the main build & checks.
Please do this as a final step before marking a task as completed.

Run `just` (or `just --list`) to see all available commands.

## Build environment (fork-specific)

See [docs/BUILD-NOTES.md](docs/BUILD-NOTES.md) for verified setup steps, build
times, and the Android/iOS status. The non-obvious parts:

- **Only `ninja` and `just` need installing** (`brew install ninja just`).
  Do **not** install protoc, Node, uv or Python for the build — it downloads
  pinned copies of all four into `out/` and ignores whatever is on `PATH`.
  Git submodules are checked out automatically too.
- **Use rustup, not Homebrew Rust.** `rust-toolchain.toml` pins 1.92.0, but only
  rustup honours it. On Homebrew Rust the pin is silently ignored: the build
  still succeeds, but `check:clippy` fails on upstream files with lints that
  postdate the pin. That is not a code defect — do not "fix" upstream to satisfy
  it.
- Before `just check`: put `~/.cargo/bin` on `PATH` (else `check:minilints` can't
  find `cargo-license`) and run
  `git submodule update --init qt/installer/mac-template` (else two
  `qt/tests/test_installer.py` tests fail). The build only auto-inits the two
  `ftl/` submodules.
- Cross-compiling to Android/iOS is impossible without rustup; Homebrew Rust
  ships only the host std and cannot add targets.
- `just run -b <dir>` runs against a throwaway collection. Use it — plain
  `just run` opens your real one.
- `http://localhost:40000/_anki/pages/*.html` 404s until the matching screen is
  opened. That is normal, not a broken build.

## Quick iteration

During development, you can build/check subsections of our code:

- Rust: `cargo check`
- Python: `just lint` (runs mypy/ruff), and if wheel-related, `just wheels`
- TypeScript/Svelte: `just lint` (includes check:svelte and check:typescript)

Language-specific tests are also available: `just test-rust`, `just test-py`,
`just test-ts`. Use `just fmt` / `just fix-fmt` for formatting and
`just fix-lint` to auto-fix lint issues.

TypeScript/Svelte browser e2e tests live in `ts/tests/e2e/` and run with
`just test-e2e`. The harness launches a temporary Anki instance and drives
mediasrv pages with Playwright's Chromium.

Be mindful that some changes (such as modifications to .proto files) may
need a full build with `just check` first.

## Build tooling

`just` recipes wrap our build system (implemented in build/), which takes
care of downloading required deps and invoking our build steps. See the
project `justfile` for the full set of recipes.

## Translations

ftl/ contains our Fluent translation files. We have scripts in rslib/i18n
to auto-generate an API for Rust, TypeScript and Python so that our code can
access the translations in a type-safe manner. Changes should be made to
ftl/core or ftl/qt. Except for features specific to our Qt interface, prefer
the core module. When adding new strings, confirm the appropriate ftl file
first, and try to match the existing style.

## Protobuf and IPC

Our build scripts use the .proto files to define our Rust library's
non-Rust API. pylib/rsbridge exposes that API, and \_backend.py exposes
snake_case methods for each protobuf RPC that call into the API.
Similar tooling creates a @generated/backend TypeScript module for
communicating with the Rust backend (which happens over POST requests).

## Fixing errors

When dealing with build errors or failing tests, invoke 'check' or one
of the quick iteration commands regularly. This helps verify your changes
are correct. To locate other instances of a problem, run the check again -
don't attempt to grep the codebase.

## Ignores

The files in out/ are auto-generated. Mostly you should ignore that folder,
though you may sometimes find it useful to view out/{pylib/anki,qt/\_aqt,ts/lib/generated} when dealing with cross-language communication or our other generated sourcecode.

## Installer

The code for our Briefcase-based installer is in qt/installer, with
separate templates for each platform (mac-template/, linux-template/,
windows-template/).

## Rust dependencies

Prefer adding to the root workspace, and using dep.workspace = true in the individual Rust project.

## Rust utilities

rslib/{process,io} contain some helpers for file and process operations,
which provide better error messages/context and some ergonomics. Use them
when possible.

## Rust error handling

in rslib, use error/mod.rs's AnkiError/Result and snafu. In our other Rust modules, prefer anyhow + additional context where appropriate. Unwrapping
in build scripts/tests is fine.

## Individual preferences

See @.claude/user.md

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.
