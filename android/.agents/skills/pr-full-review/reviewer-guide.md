# AnkiDroid PR Reviewer Guide

## How to use this guide

This is the **standards corpus** for reviewing AnkiDroid changes - *what* to check, not
*how* to run a review. It distills the project's documented standards into an actionable
checklist so a reviewer (human or agent) can work without opening ten other files.

Two rules of engagement:

1. **The linked canonical sources are authoritative.** Where this guide and a source
   disagree, the source wins. Load all the [Canonical sources](#canonical-sources) into context.
2. **Reviewing is subjective.** As the wiki puts it: "these guidelines are ONLY guidelines,
   use your best judgment." Prefer the implementer's choice when a decision is a genuine
   toss-up.

## Tone

From the [Code-review guide](https://github.com/ankidroid/Anki-Android/wiki/Code-review-guide):

- **Establish contributor status first.** Check for the `New Contributor` label and count the
  author's PRs, merged and unmerged (`gh pr list --author <login> --state all`), to gauge how
  established and experienced they are - it sets the tone bar below and the AI-use policy applied.
- **Be kind.** Note the good things in the PR and say thank you. Terse critique reads as
  hostile in text.
- **Relax for first-timers.** On PRs labelled `New Contributor`, lower the bar to make a
  good first impression — get them landed, mentor later.
- **Prefix non-blocking feedback with `nit:`** so the author knows it's optional.
- **Defer to the implementer** when you're uncertain a change is actually an improvement.
- Avoid demanding large changes unless they're a meaningful long-term improvement; suggest
  splitting big PRs into smaller ones.

## Blocking gates

Request changes if any of these fail — they're table stakes before deeper review:

- **CI is green.** Lint, unit, emulator and CodeQL must pass. Don't just glance at the rollup
  status (`gh pr checks <number> --repo ankidroid/Anki-Android`): if a check is **pending**, say
  the gate is unverified; if a check is **failing**, read its log (`gh run view <run-id>
  --repo ankidroid/Anki-Android --log-failed`) and report the actual cause, not just "red".
  For new contributors, explain how to find and read the failure themselves — and consider pasting
  the relevant CI output into the review so they don't have to dig for it. Refer the submitter to
  [`.github/workflows/README.md`](../../../.github/workflows/README.md).
- **PR template is filled in** (Purpose / Approach / How tested) and the PR is **linked to
  an issue** (`Fixes #`) where one applies.
- **Commit hygiene:** no merge commits in the history (rebase and force push, don't merge); 
  each commit compiles and does one thing. Commit titles should not be longer than 80 chars. 
  There is a suggestion for a <= 50 char title. Only flag this if you provide a reworded title.
- **New source files carry an licensing header:** see [Licensing](#licensing).

## What to check

### Correctness & clarity
- The codebase is better after the change than before.
- Edge cases and exceptions are handled.
- Names are understandable; hard-to-follow code is commented.

### Bug fixes 
- Bug fix commits must contain a confirmation that the author reproduced the bug, unless the bug 
  is obvious, or the submitter has specifically stated why they were unable to reproduce it.
- Trace the code path and the values it reads to confirm the claimed trigger occurs.

### Tests
- Significant new logic ships with tests, **or** is annotated
  [`@NeedsTest("reason")`](../../../common/src/main/java/com/ichi2/anki/common/annotations/NeedsTest.kt)
  explaining why the test is deferred. The annotation exists so we signal that we care about
  testing without blocking a contributor's first commits.
- **Bug fixes need a regression test.** The expectation (see [`CLAUDE.md`](../../../CLAUDE.md))
  is write-the-failing-test-first, confirm it fails, then fix.
- **A test must exercise the changed production code and fail without the fix.** A contributor
  should not copy production code into tests to ensure correctness.

### Scope
- Each commit must be focused. Refactors should be split from functional changes. 
- Flag unnecessary whitespace churn. Flag if a PR unnecessarily affects more than one concern.

### Commit messages
- Flag if a 'refactor:' commit title is used for a functional change.

### GitHub
- For new contributors, flag commits whose `user.email` isn't linked to a GitHub account, as
  they won't receive attribution on their GitHub heatmap.

### Licensing
- **Never remove an existing copyright header** unless it is your own. See
  [`docs/contributing/copyright-headers.md`](../../../docs/contributing/copyright-headers.md).
- New external dependencies/resources: ensure the PR fills the **Licenses** table in the
  template, and apply the `Licenses` label / update the licenses wiki on merge.

### AI-use policy
Per [`AI_POLICY.md`](../../../AI_POLICY.md):
- Use the current documentation and determine if the user is a new contributor. Ensure that AI-use
  restrictions are appropriately applied.

### UI changes
- A Roborazzi test of large UI changes is optional, but greatly appreciated. 
- Screenshots of **all** affected screens (especially new/changed strings).
- Large changed are tested with the Google Accessibility Scanner.

### Compose
For code under `com.ichi2.anki.ui.compose.*` (see
[`docs/development/compose.md`](../../../docs/development/compose.md)):
- Pure "migrate this XML screen to Compose" PRs aren't accepted — there must be another
  reason to touch the screen.
- No `Anki` prefix on component names; let the package path namespace them.
- Wrap top-level composables in the project `Theme { }`; don't define parallel color tokens
  in Kotlin (XML themes stay the source of truth).
- Host Compose via a `ComposeView` returned from a Fragment's `onCreateView`.
- Use existing drawables via `painterResource(R.drawable.…)`; don't add the
  material-icons artifacts.
- A rewrite adds a **Roborazzi screenshot test of the existing screen first** (ideally a
  separate PR), then the rewrite. One concern per migration — no simultaneous state/nav
  rework.

## Canonical sources

Defer to these authoritative references over the distillation above:

- [Code-review guide (wiki)](https://github.com/ankidroid/Anki-Android/wiki/Code-review-guide) — tone, process, when to skip second approval.
- [`CONTRIBUTING.md`](../../../CONTRIBUTING.md) — contribution workflow, commits, PR labels.
- [`.github/pull_request_template.md`](../../../.github/pull_request_template.md) — required PR sections and checklist.
- [`.github/workflows/README.md`](../../../.github/workflows/README.md) — the exact CI jobs and local commands.
- [`lint-rules/.../IssueRegistry.kt`](../../../lint-rules/src/main/java/com/ichi2/anki/lint/IssueRegistry.kt) — the full custom lint-rule list.
- [`common/.../annotations/NeedsTest.kt`](../../../common/src/main/java/com/ichi2/anki/common/annotations/NeedsTest.kt) — the `@NeedsTest` contract.
- [`docs/contributing/copyright-headers.md`](../../../docs/contributing/copyright-headers.md) — SPDX / copyright rules.
- [`AI_POLICY.md`](../../../AI_POLICY.md) — AI tool-use and disclosure policy.
- [`docs/development/compose.md`](../../../docs/development/compose.md) — Compose conventions.
- [`CLAUDE.md`](../../../CLAUDE.md) — scope discipline and regression-test-first.
