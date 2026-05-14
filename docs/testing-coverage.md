# Testing and Coverage

CI runs Rust, Python, and TypeScript tests plus lint/type checks. Coverage is
orchestrated in `justfile` with direct CLI calls, using ninja only to prepare
generated build artifacts that the test commands need.

## Quick reference

```sh
just test                        # run all tests (no coverage)
just test --coverage             # run all tests + enforce coverage thresholds
just test --coverage --html      # same + generate HTML reports under out/coverage/

just test-rust                   # Rust only
just test-rust --coverage
just test-rust --coverage --html

just test-py                     # Python (pylib + qt) only
just test-py --coverage
just test-py --coverage --html

just test-ts                     # TypeScript/Svelte Vitest only
just test-ts --coverage
just test-ts --coverage --html
```

HTML reports are written under `out/coverage/` (gitignored).

## Coverage tools and thresholds

| Stack               | Test runner                          | Coverage tool    | Minimum |
| ------------------- | ------------------------------------ | ---------------- | ------: |
| Rust workspace      | `cargo nextest` via `cargo-llvm-cov` | `cargo-llvm-cov` |     60% |
| Python `pylib/anki` | `pytest pylib/tests`                 | `coverage.py`    |     65% |
| Python `qt/aqt`     | `pytest qt/tests`                    | `coverage.py`    |     20% |
| TypeScript/Svelte   | `vitest run`                         | Vitest V8        |      5% |

Linux pull requests run `just test --coverage` in CI. macOS and Windows
jobs run `just test` (no coverage enforcement) for now.

## Notes

- **Rust** — `cargo-llvm-cov` is installed on demand into `out/bin/` to avoid
  polluting the global cargo install. Coverage runs rebuild the workspace with
  instrumentation, so they are slower than plain `just test-rust`.
- **Python** — coverage is split across two suites (`pylib` and `qt`) because
  they have different `PYTHONPATH` setups and test folders.
- **TypeScript** — coverage is measured only over code reachable through
  Vitest's module graph. Svelte component rendering behavior is not covered.

## Gaps and future improvements

- Raise thresholds gradually as the test suite grows and CI timings stabilise.
- Exclude generated files from coverage denominators where appropriate.
- Publish `out/coverage/` as a CI artifact so reviewers can browse HTML
  reports directly from a PR.
- Consider diff/changed-file coverage once baselines are stable — it is a
  better enforcement mechanism for incremental improvement than whole-repo
  thresholds.
- Add component or browser tests for Svelte UI surfaces if Svelte coverage
  is intended to cover rendered component behaviour.
