---
name: anki-brownfield-development
description: >-
  Use when making changes to the existing Anki codebase (a mature brownfield
  project) — adding features, fixing bugs, or refactoring within rslib/, pylib/,
  qt/, ts/, or proto/. Emphasizes preserving backward compatibility (collection
  DB schema, sync protocol, protobuf wire format, public/add-on API,
  translations), conforming to established conventions, keeping diffs minimal and
  upstream-quality, and verifying with Anki's own checks. Use whenever editing
  Anki source rather than starting something new.
---

# Anki Brownfield Development

## Overview

Anki is a **mature, established codebase** whose changes ripple into real user
collections and a wider ecosystem (AnkiWeb sync, AnkiDroid, AnkiMobile, and
thousands of community add-ons). The prime directive for brownfield work here:

> **Integrate, don't reinvent. Preserve compatibility. Make the smallest,
> most conventional diff that solves the problem.**

You are a guest in an existing system. A change that "works" but breaks a data
format, a sync round-trip, an add-on API, or a convention is a regression — even
if all local tests pass.

This skill is the **discipline** layer. For the mechanics of exploring/searching
this repo, use the `navigating-large-codebases` skill. For build commands and
i18n, see `CLAUDE.md` and `.cursor/rules/`.

## Golden Rules

1. **Understand before you touch.** Find how the existing code solves similar
   problems and follow that pattern.
2. **Smallest viable diff.** No drive-by reformatting, renaming, or "while I'm
   here" cleanups. Unrelated churn obscures the real change and breaks `git blame`.
3. **Never break compatibility surfaces** (see below) without an explicit
   migration/deprecation path.
4. **Match local conventions** over your personal preferences or generic
   best-practice.
5. **Changes should look upstream-mergeable** — write as if submitting a PR to
   Anki proper.
6. **Verify with the repo's own checks**, not by inspection.

## Compatibility Surfaces — Do Not Break These

These are the high-risk seams where a careless change corrupts data or breaks
other software. Treat each as an append-only / versioned contract.

| Surface                  | Where                                           | Rule                                                                                                                                                                                  |
| ------------------------ | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Collection DB schema** | `rslib/src/storage/` + `upgrades/schemaN_*.sql` | Add a **new** versioned upgrade (+ downgrade). **Never edit a shipped migration** or repurpose existing columns.                                                                      |
| **Sync protocol**        | `rslib/sync/`, `rslib/src/storage/sync*.rs`     | Must stay compatible with AnkiWeb and other clients. Don't change semantics of existing sync fields/endpoints.                                                                        |
| **Protobuf wire format** | `proto/anki/*.proto`                            | **Only add** fields with new numbers. Never renumber, reuse, remove, or change the type of an existing field.                                                                         |
| **Public / add-on API**  | `pylib/anki/`, `qt/aqt/`                        | Add-ons monkeypatch and call these. Don't rename/remove public symbols — alias + deprecate via `anki._legacy` (`DeprecatedNamesMixin`, `@deprecated`, `register_deprecated_aliases`). |
| **Translations (ftl)**   | `ftl/` (add to `rslib/core`)                    | Don't delete or repurpose existing strings. Deprecate with `just ftl-deprecate`; sync with `just ftl-sync`. (See `.cursor/rules/i18n.md`.)                                            |
| **Config & defaults**    | `rslib/src/config/`, deck/notetype configs      | Changing defaults changes behavior for existing users — justify and prefer additive, opt-in changes.                                                                                  |

When a change _must_ cross one of these, the order is: **extend the contract
additively → migrate/deprecate the old form → update each layer → keep the old
path working until it's safe to remove.**

## Conform to Established Conventions

- **Layering:** put logic in the right layer. Core behavior belongs in `rslib`
  (Rust); `pylib`/`qt` are increasingly thin shells; `ts` is the web UI. Don't
  reimplement core logic in Python/TS that belongs in Rust.
- **Rust errors:** in `rslib`, use `error/mod.rs`'s `AnkiError`/`Result` + snafu.
  In other Rust crates, use `anyhow` + context. Match the file you're in.
- **Rust utilities:** prefer the helpers in `rslib/{io,process}` over raw std
  file/process calls (better error context).
- **Rust deps:** add to the root workspace `Cargo.toml`, then `dep.workspace =
  true` in the member crate. Don't add a dependency casually — prefer existing ones.
- **Generated code is off-limits:** never hand-edit anything in `out/` (or other
  generated bindings). Change the `.proto`/source/template and regenerate.
- **New files need the license header** (verbatim), e.g. Rust:

```rust
// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
```

(Use `#` comment form for Python; `just minilints` enforces this.)

- **New contributors** must be added to `CONTRIBUTORS` (also checked by minilints).

## Change Workflow

```
- [ ] 1. Locate the existing pattern for this kind of change; mirror it
- [ ] 2. Identify which compatibility surfaces (if any) the change touches
- [ ] 3. If it crosses a contract, change the contract additively FIRST
- [ ] 4. Implement the smallest vertical slice, matching neighboring code
- [ ] 5. Add headers to new files; update CONTRIBUTORS / ftl if needed
- [ ] 6. Verify with narrow checks, then the full check
```

- **`.proto` changes require a full build** (`just check`) so codegen
  regenerates bindings across Rust/Python/TS before you wire up each layer.
- Add or update tests next to the code you changed (`rslib/.../tests`,
  `pylib/tests`, `qt/tests`, `ts/**/*.test.ts`).
- Keep the diff reviewable: if a refactor is genuinely needed, do it as a
  separate, clearly-scoped change rather than bundling it with a feature.

## Verify (Anki's own checks)

Use the narrowest check while iterating, then run the full check last. Don't
invoke `./ninja`, `./run`, or `tools/` scripts directly — use `just` recipes.

| Scope                                   | Command                                            |
| --------------------------------------- | -------------------------------------------------- |
| Full format + lint + test (final gate)  | `just check` (or `./check`)                        |
| Rust quick check                        | `cargo check`                                      |
| Lint + type-check (mypy/ruff/svelte/ts) | `just lint`                                        |
| Per-language tests                      | `just test-rust` / `just test-py` / `just test-ts` |
| Format / autofix                        | `just fmt` · `just fix-fmt` · `just fix-lint`      |
| Headers, contributors, licenses         | `just minilints`                                   |

Also fix any linter errors you introduced in edited files before declaring done.

## Common Anki Gotchas

| Pitfall                                        | Do instead                                        |
| ---------------------------------------------- | ------------------------------------------------- |
| Editing a shipped `schemaN_*.sql` migration    | Add a new versioned upgrade (+ downgrade)         |
| Renumbering/removing a protobuf field          | Add a new field number; leave old ones intact     |
| Renaming/deleting a public `aqt`/`anki` symbol | Alias it and deprecate via `anki._legacy`         |
| Deleting an unused ftl string                  | Deprecate it (`just ftl-deprecate`)               |
| Hardcoding a user-facing string                | Add to `rslib/core` ftl; use generated `tr.*` API |
| Editing files under `out/`                     | Edit source/proto/template; regenerate            |
| Reimplementing core logic in Python/TS         | Put it in `rslib`, expose via proto               |
| Drive-by reformatting unrelated code           | Keep the diff scoped to the change                |
| Skipping the license header on a new file      | Add header; run `just minilints`                  |
| "Tests pass locally" as proof of safety        | Also reason about schema/sync/API/add-on impact   |

## Quick Reference

```
MINDSET     guest in a mature system → integrate, don't reinvent
SCOPE       smallest conventional diff; no drive-by churn
PROTECT     DB schema · sync · proto wire · add-on API · ftl strings · config defaults
CONTRACTS   extend additively → deprecate/migrate → keep old path working
CONVENTIONS right layer · AnkiError/snafu · io/process helpers · workspace deps
HYGIENE     license header on new files · CONTRIBUTORS · never edit out/
VERIFY      narrow check while iterating → just check + just minilints at the end
```

## Related Guidance

- `navigating-large-codebases` skill — how to explore/search/trace this repo.
- `CLAUDE.md` / `AGENTS.md` — architecture overview and build tooling.
- `.cursor/rules/building.md` — `./check` builds, formats, lints, tests.
- `.cursor/rules/i18n.md` — fluent translation system and generated `tr` API.
