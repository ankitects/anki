# Workflows

GitHub Actions is used to automate parts of our development workflow.

See https://docs.github.com/en/actions for more information.

## Quality Checks

We run checks on all Pull Requests (PRs). We recommend that when appropriate, developers run the 
following scripts before submitting a PR.

Alternately, you may run the actions on your fork of `Anki-Android`.

| **Job**                                                                                                    | **Command**                                                          | **Comments**                                              |
|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|-----------------------------------------------------------|
| [Lint (Kotlin)](https://github.com/ankidroid/Anki-Android/blob/main/.github/workflows/lint.yml)            | `./gradlew lintAll ktLintCheck lint-rules:test --daemon`             | Android lint rules, formatting and tests for lint rules   |
| [Lint (JavaScript)](https://github.com/ankidroid/Anki-Android/blob/main/.github/workflows/lint.yml)        | See script                                                           | Prettier, lint & code formatting                          |
| [Unit Tests](https://github.com/ankidroid/Anki-Android/blob/main/.github/workflows/tests_unit.yml)         | `./gradlew jacocoUnitTestReport --daemon`                            | Unit tests for the Android Project                        |
| [Emulator Tests](https://github.com/ankidroid/Anki-Android/blob/main/.github/workflows/tests_emulator.yml) | `TEST_RELEASE_BUILD=true ./gradlew jacocoAndroidTestReport --daemon` | Emulator tests for the Android Project                    |
| [CodeQL](https://github.com/ankidroid/Anki-Android/blob/main/.github/workflows/codeql.yml)                 | N/A                                                                  | GitHub-only check.<br/>[Docs](https://codeql.github.com/) |

## Other Workflows

These are typically run by maintainers. See the [Maintenance guide](https://github.com/ankidroid/Anki-Android/wiki/Maintenance-guide)