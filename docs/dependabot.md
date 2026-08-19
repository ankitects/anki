<!-- DO NOT MANUALLY EDIT THIS FILE -->
<!-- This file is copied from docs-site/developers/dependabot.mdx automatically -->

# Dependabot updates

<!-- <<<cog
from cogdocs import get_file_contents
cog.out(get_file_contents("dependabot"))
>>> -->

Config lives in [`.github/dependabot.yml`](https://github.com/ankitects/anki/blob/main/.github/dependabot.yml).
This page is for maintainers: how PRs are grouped, which ones to merge,
and how to clear Rust advisories that Dependabot cannot fix on its own.

## Goals

Version updates were accumulating faster than anyone reviewed them. Our solution is to fix grouping and slow the schedule, not turn updates off.

Security updates stay **individual**. Each advisory needs its own review (false positives are common, so we need to know which CVE actually matters). GitHub also does not apply `open-pull-requests-limit` to security PRs.

## What gets opened

Four ecosystems: Cargo, npm (Yarn), Python (`uv`), GitHub Actions.

| Kind           | When                                                           | Shape                                          | What to do                                                                                       |
| -------------- | -------------------------------------------------------------- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Security       | As soon as GitHub publishes an advisory. Ignores the schedule. | One PR per advisory                            | Review and land promptly, or close if it does not apply                                          |
| Minor / patch  | Quarterly                                                      | One grouped PR per ecosystem (`*-minor-patch`) | Merge if CI is green                                                                             |
| Major          | Quarterly, after a 30-day cooldown                             | One grouped PR per ecosystem (`*-major`)       | **Do not merge.** Treat as a notification. If a bump is wanted, open a human PR for that package |
| GitHub Actions | Quarterly                                                      | One grouped PR (`actions`)                     | Merge if CI is green                                                                             |

`open-pull-requests-limit` is 2 for Cargo / npm / Python (one slot for minor/patch, one for major) and 1 for Actions. That limit applies only to **version** updates. Security PRs are unlimited.

Cooldown is 7 days for ordinary version bumps (a short supply-chain window) and 30 days for majors (`semver-major-days`). Security updates skip cooldown.

Dependabot PRs are exempt from the linked-issue requirement.

## Reviewing a PR

1. Read the title / group name and put it in the table above;
2. CI must be green. Dependency PRs do not need new tests, but `format`, `cargo deny`, and the existing suites still run;
3. **npm:** if CI fails with `YN0028` / `yarn install --immutable`, Dependabot updated `package.json` and left `yarn.lock` stale. Check out the branch, run Yarn so the lockfile matches, and push;
4. Close older individual version PRs that a newer grouped PR already covers. Dependabot often does this itself when it opens a replacement.

### Security PRs

Land these first. Confirm the advisory actually affects this repo: Dependabot matches on version, not on whether we call the vulnerable API.

If the bump is a major, or CI fails, do not force the Dependabot PR through. Open a normal PR with whatever code or lockfile changes are required.

If we cannot bump yet, say so in the PR and follow the Rust ignore path below when `cargo-deny` is the failing check.

### Minor / patch grouped PRs

Low risk. Merge when CI is green. If one crate in the group breaks the build, do not merge the bundle, we should split or skip that crate.

### Major grouped PRs

Majors often need code changes. The grouped PR exists so we are not flooded with one PR per package. _It is not meant to be merged as-is_. Pick the package we actually want, open a dedicated PR, and leave the rest.

### GitHub Actions

Actions are pinned by SHA. Dependabot **security alerts are not generated for SHA-pinned actions** but Dependabot still opens the quarterly grouped version PR and bumps those SHAs to the latest release. So there is no immediate security PR for a vulnerable action. That is an accepted trade-off: SHA pins stop tag-rewrite supply-chain attacks, at the cost of losing action alerts.

## Rust advisories (`cargo-deny`)

CI runs `cargo deny check` on pushes and on PRs that touch `Cargo.lock`, `Cargo.toml`, or `.deny.toml`. Failures often look like Dependabot work but are not something Dependabot can finish, especially when the crate sits behind a git dependency.

Worked example: [RUSTSEC-2026-0258](https://github.com/ankitects/anki/issues/5364) needed a `reqwest` bump inside the [ankitects/linkcheck](https://github.com/ankitects/linkcheck) fork, not a one-line change in this repo.

1. Open the `RUSTSEC-…` advisory and note the crate and patched versions.
2. See whether Dependabot already opened a security PR that bumps a crate we depend on directly. If CI is green, merge it.
3. If the crate is **transitive through a git dep** (`linkcheck`, `percent-encoding-iri`, …):
    - Land the bump in the fork.
    - Point `Cargo.toml` at the new `rev`.
    - Run `cargo update -p <crate>` (and any other crates the advisory names) so `Cargo.lock` moves.
4. Run `cargo deny check` locally before pushing.
5. Only if a bump is not feasible, add the id to `.deny.toml` `[advisories] ignore` with a comment stating why and what the alternative is. Existing ignores follow that pattern. Do not silence an advisory without a comment.

Dependabot cannot update git `rev` pins. Those bumps are always manual.

## Changing the config

- Do not group security updates;
- Do not put majors in the minor/patch group;
- `open-pull-requests-limit` cannot cap security PRs;
- Cooldown keys are `default-days` and `semver-major-days`.

<!-- <<<end>>> -->
