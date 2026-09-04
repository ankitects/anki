<!-- DO NOT MANUALLY EDIT THIS FILE -->
<!-- This file is copied from docs-site/developers/unit-testing.mdx automatically -->

# Unit Testing Specification and Guide

<!-- <<<cog
from cogdocs import get_file_contents
cog.out(get_file_contents("unit-testing"))
>>> -->

This document defines how maintainers, contributors, and coding assistants should
design unit and component tests for Anki. It is intentionally independent of any
particular feature, module, or bug. Use it together with the behavior being changed,
the surrounding production code, and nearby tests.

The words **must**, **should**, and **may** express requirements, recommendations,
and optional practices respectively.

## Quick workflow

1. Identify the behavior and the layer that owns it.
2. Define the observable contract.
3. Consider happy, unhappy/error, boundary, invariant, and regression scenarios.
4. Select the lowest test boundary that provides sufficient confidence.
5. Write a focused Arrange–Act–Assert test.
6. Control time, randomness, external services, and global state.
7. Confirm that the test fails when the behavior is broken.
8. Run the relevant `just` recipe and then `just check`.

## Purpose

A useful test gives fast, trustworthy evidence about behavior that matters. It also
acts as executable documentation: a reader should be able to understand the
scenario, action, and expected result without reverse-engineering the implementation.

A good unit test is:

- **Behavioral:** it verifies an observable result, state transition, error, or
  required interaction.
- **Focused:** it has one reason to fail. Multiple assertions are fine when they
  describe one behavior.
- **Deterministic:** the same code and inputs produce the same result regardless of
  test order, wall-clock time, locale, network access, or machine speed.
- **Isolated:** it does not depend on a user's profile, another test's state, or an
  unavailable external service.
- **Fast:** it is cheap enough to run repeatedly while editing.
- **Readable:** its name and test data explain the contract and the failure.
- **Sensitive:** a real regression in the behavior makes it fail; it does not keep
  passing when the contract is broken.
- **Stable:** a behavior-preserving refactor should usually leave it unchanged.

Code coverage is useful for finding untested code, but a covered line is not
necessarily a tested behavior. Do not add weak assertions merely to increase a
coverage number.

## Choose the smallest useful test boundary

Use the lowest-cost test that can prove the behavior:

| Boundary      | Use it for                                                                                                                  | Do not use it for                                       |
| ------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| Pure unit     | Calculations, parsing, validation, conversion, and deterministic state transitions                                          | Framework wiring or cross-process behavior              |
| Sociable unit | A small group of fast, in-process collaborators                                                                             | Network, real subprocesses, or a user's persistent data |
| UI component  | A Qt component's visible state, signals/events, and interaction; for Svelte, extracted logic and stores                     | Whole application workflows                             |
| Integration   | Database mappings, protobuf/language bridges, filesystem contracts, and framework integration that a unit test cannot prove | Repeating every unit-level branch                       |
| End-to-end    | A small number of critical workflows through a real Anki instance and browser                                               | Exhaustive validation and edge cases                    |

Most scenarios should be below the end-to-end layer. If a higher-level test discovers
a defect, add a lower-level regression test when that level can reproduce the defect.
Keep the higher-level test only when it proves an additional contract.

For Svelte specifically, "UI component" means extracted logic and stores: the
repository does not wire rendered-component tests into the unit suite (see
[Svelte, TypeScript, and JavaScript](https://anki.mintlify.app/unit-testing#svelte-typescript-and-javascript)).

Anki spans several implementation layers. Test a rule at the layer that owns it:

- Business rules implemented in Rust **should** normally be tested in Rust.
- Python library tests should cover Python-owned behavior, compatibility surfaces,
  orchestration, and adaptations instead of duplicating Rust assertions.
- Python/Qt tests should cover GUI-owned state, signals, callbacks, and backend
  orchestration. Move framework-independent logic into a form that can be tested
  without constructing the full GUI when practical.
- TypeScript/JavaScript tests should cover web-owned logic, stores, DOM helpers, and
  component behavior. Use end-to-end tests only when a real browser/Anki boundary is
  essential.
- Generated protobuf code does not need tests. Test custom conversion, validation,
  or bridge behavior at its owning layer; use an integration test if correctness
  depends on both sides of the bridge.

## Derive scenarios from the contract

Before writing test code, state the contract in plain English. Identify the inputs,
observable outputs, side effects, errors, and invariants. Then select the smallest
set of scenarios that distinguishes a correct implementation from a plausible bug.

Every meaningful behavior change should consider these categories:

| Category           | Question                                                                | Generic example                                                                    |
| ------------------ | ----------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Happy path         | Does valid, representative input produce the intended result?           | An accepted value is normalized and stored.                                        |
| Unhappy/error path | Is invalid input rejected or recovered from as specified?               | A malformed value returns the expected error and does not mutate state.            |
| Boundary           | What happens at empty, minimum, maximum, transition, and Unicode cases? | An empty label uses the defined fallback; the maximum allowed value remains valid. |
| State/invariant    | What must remain true before and after the action?                      | A failed update leaves the previous value and unrelated records unchanged.         |
| Regression         | What is the smallest input that reproduces a reported defect?           | The exact shape that once crashed now produces the specified result.               |

Do not assume that every function needs one test from every category. Include a category
when the contract has that behavior or the implementation has a credible failure
mode.

Use equivalence classes rather than enumerating inputs that exercise the same rule.
Use parameterization when several compact input/output pairs express one behavior;
use separate tests when cases require different setup or should produce distinct
failure explanations.

## Structure and naming

Prefer a visible Arrange–Act–Assert flow:

1. **Arrange** only the state relevant to the scenario.
2. **Act** once on the behavior under test.
3. **Assert** the observable contract, including important non-effects.

The phases do not require comments when the structure is already obvious. Avoid
hiding the act or the important assertion inside a helper.

Name a test after the unit under test and the expected behavior, not an
implementation method. Follow the convention of the tests already in that file;
newer tests in the codebase put the condition last:

```text
<unit>_<expected_outcome>[_when_<condition>]
```

Real examples from the codebase:

- Rust: `answer_easy_graduates_new_card_to_review_queue`,
  `bury_leaves_suspended_card_untouched`,
  `add_or_update_notetype_preserves_usn_when_flag_is_set`
- Python/Qt: `test_update_collection_skips_backend_when_unchanged`,
  `test_is_audio_file_rejects_no_extension`

Vitest names the behavior in the `test(...)` description string rather than in an
identifier, e.g. `test("event handler attributes are stripped")`.

Names such as `test_helper`, `works`, `basic`, or `case_1` do not explain the
contract.

Assertions should be precise enough to diagnose the defect. Prefer comparing the
meaningful value or structured result over a generic truthiness check. Assert an
error's type or variant and, when stable and user-relevant, its message. Avoid
asserting incidental formatting or a complete object when only one field defines the
behavior.

## Test data, setup, and cleanup

- Use the smallest realistic data and give it semantic names such as
  `existing_record` and `invalid_name`.
- Prefer builders, factories, and fixtures already used by nearby tests.
- Keep test-specific setup in the test. Extract a helper only when it removes noise
  without concealing the scenario.
- Use temporary directories, temporary collections/databases, and in-memory values.
  Never read or modify a user's Anki profile.
- Restore patched globals, environment variables, timers, DOM state, and callbacks.
  Prefer framework cleanup facilities that run even after an assertion fails.
- Tests **must** pass independently, in any order, and when repeated.

## Test doubles and boundaries

Prefer real, lightweight, deterministic collaborators. Use a stub, fake, spy, or mock
to control an awkward boundary such as time, randomness, network, subprocess,
clipboard, native dialog, or backend call, and patch it where the code looks it up.
Return realistic values and errors.

Assert interactions only when they are part of the contract; avoid incidental calls
and ordering. If every collaborator is mocked and only calls are asserted, the test
probably describes the implementation rather than useful behavior.

## Time, randomness, concurrency, and asynchronous behavior

- Inject or freeze time when the exact time affects behavior. Do not depend on the
  current date, local timezone, or rollover hour.
- Seed or replace randomness and assert invariants rather than one accidental random
  result.
- Never use an unconditional sleep to wait for correctness. Await the promise,
  callback, signal, task, or condition with a bounded safety timeout.
- Avoid exact performance timings in unit tests. Put performance claims in a
  benchmark with appropriate tolerance and environment control.
- Assume files and test cases may run in parallel. Do not share mutable globals,
  fixed ports, or fixed temporary paths.

## Regression tests

A bug fix **should** normally include a test that would have caught the bug.

1. Reduce the report to the smallest representative input and state.
2. Place the test at the lowest layer that reproduces the defect and proves the
   intended contract.
3. When practical, run it before the fix or against the buggy revision and confirm
   that it fails for the expected reason.
4. Apply the fix and confirm that the new test passes.
5. Run the relevant suite and check adjacent scenarios for the same class of defect.
6. Keep the regression test permanently. Name it after the behavior, not an issue
   number; add the issue link in a short comment only when it provides essential
   context that the test cannot express.

Do not encode the buggy implementation in the expected result. A regression test
should describe the durable contract so that future refactoring can preserve it.

## Test-first workflow

Writing the test before the implementation is encouraged when the expected behavior
is clear. A test-first cycle can help verify that the test is sensitive to the defect:

1. Write a focused test for the intended behavior.
2. Confirm that it fails for the expected reason.
3. Make the smallest production change that satisfies the contract.
4. Refactor while keeping the test suite green.

Test-first development is not mandatory. For exploratory work or behavior that is
not yet understood, first investigate and define the contract, then add the test. For
bug fixes, a failing regression test before the fix is strongly recommended whenever
practical.

## Additional risk areas

The scenario categories above apply to all behavior. Also consider risks specific to
Anki:

- serialization or conversion code written by the project;
- permission, path, escaping, and untrusted-input boundaries;
- callbacks, signals, events, and async completion when they are part of the API;
- compatibility behavior intentionally supported for add-ons or older data.

## What not to test

Do not spend test maintenance on:

- private implementation details with no observable contract;
- trivial code, compiler/type-checker guarantees, generated accessors, or third-party
  behavior;
- the same rule exhaustively at every language layer;
- snapshots so broad that reviewers cannot tell whether a change is correct;
- real network services, user profiles, home-directory files, or machine-specific
  executables;
- random input without a reproducible seed and reported failing case;
- internal call counts used only because mocking makes them easy to observe; or
- assertions that only prove the code ran, returned something truthy, or did not
  throw when a more specific result exists.

## UI testing scope and cost

Qt and Svelte tests have a higher setup and maintenance cost than tests for pure
logic. Do not attempt to test every widget, component, property, or markup detail. A
UI test **should** protect behavior whose value and risk justify that cost.

Prefer testing:

- logic, view models, stores, state transitions, and error handling;
- interactive states, signals, events, callbacks, and backend requests;
- accessibility roles, labels, focus, and keyboard behavior when they are part of
  the user contract; and
- regressions in stable, user-visible behavior.

Avoid unit tests for styling, exact geometry, incidental DOM structure or CSS/Sass
class names, animation timing, platform-native rendering, generated UI code,
framework internals, and large UI snapshots.

When possible, extract logic from the Qt widget or Svelte component and test it as
ordinary Python or TypeScript/JavaScript. Keep only a small wiring test at the UI
layer when it provides additional confidence.

Check presentation during UI review, or with visual regression if appearance is a
stable product contract and the project intentionally adopts such tooling. Volatile
code is not automatically exempt: test its stable contract when one exists. If both
implementation and expected presentation are temporary, omitting a brittle UI test
may be the better trade-off.

## Stack-specific guidance

The examples below are illustrative pseudocode. Names such as `State`, `Model`, and
`normalize_value` are not Anki APIs and **must not** be copied without inspecting the
actual code.

### Python library

Python library tests live under `pylib/tests/` and use `pytest`.

- Name files/functions `test_*` and use plain `assert` for diagnostic diffs.
- Use `pytest.raises()` for expected errors; match a stable message only when the
  message is part of the contract or improves precision.
- Use `pytest.mark.parametrize()` for compact equivalent cases.
- Prefer typed fixtures, `tmp_path`, `monkeypatch`, and existing collection helpers;
  close resources through fixtures or context managers.
- Mock the Rust backend only when testing Python-owned orchestration or an otherwise
  impractical failure. Do not mock it merely to make a domain-rule test appear unit
  sized.

Generic example:

```python
import pytest


@pytest.mark.parametrize(
    ("raw_value", "expected"),
    [(" valid ", "valid"), ("", "default")],
)
def test_normalization_returns_the_documented_value(
    raw_value: str, expected: str
) -> None:
    assert normalize_value(raw_value) == expected


def test_invalid_value_is_rejected_without_mutating_state() -> None:
    state = State(value="existing")

    with pytest.raises(ValueError, match="invalid value"):
        state.update("invalid")

    assert state.value == "existing"
```

### Python/Qt

Python/Qt tests live under `qt/tests/` and also use `pytest`.

- Test framework-independent decisions as ordinary Python first.
- Construct the smallest useful Qt object; do not launch a complete Anki window for
  a unit test.
- Trigger public actions or realistic input, then assert meaningful state, signals,
  callbacks, or backend requests. Use `QSignalSpy` or a callback recorder when signal
  arguments/count are the contract.
- Await the event loop through a condition or signal with a bounded timeout; avoid
  fixed sleeps.
- Replace native dialogs, clipboard access, web requests, audio, and backend
  operations at their boundary.
- Keep `QApplication`, main-window state, and global hooks isolated and cleaned up.

Generic example:

```python
from unittest.mock import MagicMock


def test_submit_with_invalid_input_shows_error_without_saving() -> None:
    save = MagicMock()
    dialog = make_minimal_dialog(save=save)
    dialog.form.value.setText("invalid")

    dialog.accept()

    assert dialog.form.error.isVisible()
    save.assert_not_called()
```

### Rust

Rust unit tests normally live beside the code in a `#[cfg(test)]` module. Follow the
nearby crate's existing helpers and conventions.

- Prefer inputs and assertions over extensive mocking.
- Use `assert_eq!`/`assert_ne!` for diagnostic diffs and include a message when the
  invariant is otherwise unclear.
- Match the expected error variant or structured value instead of only calling
  `is_err()` when the distinction matters.
- Cover variants and transitions with distinct behavior, not properties guaranteed
  by the type system.
- Use a collection only when persistence is part of the unit; use pure domain values
  otherwise.
- Use small table-driven loops for one contract and include the input in assertion
  messages.
- `unwrap()` and `expect()` **may** be used in setup and when an unexpected error
  should fail the test; do not use them to inspect the error path being tested.

Generic example:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_input_uses_the_default() {
        assert_eq!(normalize_value(""), "default");
    }

    #[test]
    fn invalid_input_preserves_existing_state() {
        let mut state = State::new("existing");

        let error = state.update("invalid").unwrap_err();

        assert!(matches!(error, UpdateError::InvalidValue));
        assert_eq!(state.value(), "existing");
    }
}
```

### Svelte, TypeScript, and JavaScript

Web unit tests use Vitest. The current configuration discovers colocated
`*.test.ts`, `*.spec.ts`, `*.test.js`, and `*.spec.js` files, excluding
`ts/tests/e2e/`.

- Test TypeScript/JavaScript logic and Svelte stores without a DOM unless rendering
  and interaction prove an additional contract.
- Add `// @vitest-environment jsdom` only when the test needs DOM APIs, and restore
  any document/global state after the test.
- Use `vi.fn()`/`vi.spyOn()` at external boundaries, fake timers for timer-driven
  behavior, and restore both after the test.
- Await promises/reactive updates instead of real time. For rendered behavior,
  interact through the public UI and query by accessible role, label, or visible text
  rather than internal state.
- Prefer focused value/DOM assertions. Use snapshots only for small, stable output
  whose entire structure is intentionally reviewed.
- The repository does not currently configure a dedicated Svelte component-rendering
  harness in the unit suite. If a behavior cannot be tested through extracted logic
  or the existing DOM setup, agree on the appropriate harness or place the essential
  browser contract in the existing Playwright suite rather than inventing a private
  test setup.

Generic TypeScript example:

```typescript
import { expect, test, vi } from "vitest";

test("invalid input reports the error without saving", async () => {
    const save = vi.fn();
    const model = new Model({ save });

    const result = await model.submit("invalid");

    expect(result).toEqual({ ok: false, reason: "invalid-value" });
    expect(save).not.toHaveBeenCalled();
});
```

For a Svelte component, the equivalent contract is expressed as a user action and a
visible/accessibility result: enter invalid input, activate Submit, observe the error,
and verify that saving did not occur. Do not call a component's private handler
directly.

## Running and validating tests

Use the repository's `just` recipes:

```sh
just test-rust  # Rust
just test-py    # Python library and Python/Qt
just test-ts    # TypeScript/JavaScript/Svelte unit tests
just test       # every stack (Rust, Python, TS)
just check      # required final formatting, build, lint, and test validation
```

During development, run the relevant stack frequently. Before considering a code
change complete, run `just check`. See [Testing and Coverage](https://anki.mintlify.app/developers/testing-coverage)
for coverage commands and [End-to-End Testing](https://anki.mintlify.app/developers/e2e-testing) when the
selected boundary requires Playwright.

## Review checklist

A test is ready when the answer to each applicable question is yes:

- Do its name and assertions clearly prove an observable contract and fail when that
  contract is broken?
- Were the relevant scenario categories considered at the lowest useful layer,
  without duplication?
- Is Arrange–Act–Assert clear, with minimal data and doubles only at meaningful
  boundaries?
- Is it deterministic, independent, cleaned up, and stable under behavior-preserving
  refactoring?
- For a bug fix, did the regression test fail before and pass after the fix when
  practical?
- Do the relevant `just` test recipe and `just check` pass?

## Instructions for coding assistants

When this document is supplied to a coding assistant, the assistant must also inspect
the production behavior, issue/diff, neighboring tests, available helpers, and test
runner configuration. This guide does not replace repository context.

Before editing, it should follow the quick workflow, summarize the contract, owning
layer, scenarios, and selected boundary, then follow local naming, fixtures, builders,
and file placement. It **must not** invent APIs, dependencies, fixtures, or runner
capabilities; change production visibility solely to reach private implementation;
or weaken an assertion to make it pass. It should finish by reporting what was
tested, intentionally omitted, and not validated.

## References and rationale

This specification adapts these references to Anki's architecture:

- General principles: [behavior over implementation](https://testing.googleblog.com/2013/08/testing-on-toilet-test-behavior-not.html),
  [test sizes](https://testing.googleblog.com/2010/12/test-sizes.html),
  [unit-test characteristics and Arrange–Act–Assert](https://learn.microsoft.com/en-us/dotnet/core/testing/unit-testing-best-practices),
  and the [practical test pyramid](https://martinfowler.com/articles/practical-test-pyramid.html).
- Python: pytest [fixtures](https://docs.pytest.org/en/stable/explanation/fixtures.html),
  [parameterization](https://docs.pytest.org/en/stable/how-to/parametrize.html), and
  [assertions](https://docs.pytest.org/en/stable/how-to/assert.html).
- Rust: [test organization in The Rust Book](https://doc.rust-lang.org/book/ch11-03-test-organization.html).
- Qt: [QtTest](https://doc.qt.io/qtforpython-6/PySide6/QtTest/index.html) and
  [QSignalSpy](https://doc.qt.io/qt-6/qsignalspy.html).
- Web: Vitest [mock functions](https://vitest.dev/guide/learn/mock-functions),
  [timers](https://vitest.dev/guide/mocking/timers), and
  [component testing](https://vitest.dev/guide/browser/component-testing), plus
  Testing Library's [accessible query priority](https://testing-library.com/docs/queries/about/).

<!-- <<<end>>> -->
