# SonarCloud: internal and fork pull requests

SonarCloud is informative, not a required merge check. Scanner failures remain
visible; a failed Quality Gate does not fail the workflow. Automatic Analysis
must remain disabled for this CI-analyzed Sonar project.

## Flow

1. `CI` runs for pull requests targeting `main`, including forks, and pushes to
   `main`/`release/**`. Build, tests, lint and coverage checks run without using
   `SONAR_TOKEN`.
2. Only a successful **whole CI run** triggers analysis. Missing coverage
   baselines or coverage regressions fail CI and therefore prevent scanning.
3. `sonar.yml` loads its tooling and settings from the same default-branch commit
   as the trusted `workflow_run` workflow. It validates the PR number, head SHA,
   head repository/branch, target repository/branch and open state using GitHub's
   API. If the webhook's PR list is empty, the context artifact supplies only a
   candidate number. Invalid or stale context fails closed, never as a branch scan.
4. Reports are downloaded from that CI run as ZIP bytes. The helper accepts only
   the exact expected regular files, with size limits; it never extracts arbitrary
   archive paths. Downloads occur outside the source checkout.
5. `actions/checkout` explicitly permits the upstream `refs/pull/<number>/head`
   checkout, including forks. The helper checks its SHA again to catch updates
   between validation and checkout. The upstream checkout also provides the
   target branch and full Git history for PR comparison.
6. A fresh `scan/` snapshot contains only tracked regular source files in the
   configured source/test directories, plus checkout-created Git metadata.
   Symlinks, hidden paths, executable permissions, dependency trees, manifests,
   build outputs, scanner caches and `*.config.*` files are not copied. Supported
   snapshot extensions are listed in `tools/sonar.py`; adding a language requires
   reviewing its analyzer's execution behavior first.
7. Coverage filenames are normalized to the snapshot, including Vitest paths
   relative to `ts/`; external paths and XML entity declarations are rejected.
   The scanner gets trusted Sonar properties and a minimal TypeScript config, not
   the PR's configuration. PR references are encoded as Java property values,
   not interpolated into shell/CLI arguments.
8. Only the scanner step receives `SONAR_TOKEN`. It analyzes source as data and
   imports coverage reports. No PR scripts, dependency installations or builds
   run here. `sonar.rust.clippy.enable=false` and `sonar.sca.enabled=false`
   disable automatic invocations of build tools; Clippy remains enforced by CI.
   Results are attached to the PR before merge; pushes update the corresponding
   branch analysis.

## Security boundaries and tradeoffs

- Keep `allow-unsafe-pr-checkout` tied to this data-only flow. Never add `just
  build`, `cargo`, `npm`, `pip`, local Actions from the PR, or PR-provided scanner
  settings to the privileged workflow. Use only the **trusted** justfile/helper.
- Use GitHub-hosted ephemeral runners. Do not restore CI caches in this workflow.
- Restrict the Sonar credential to analysis of this project using the least
  privileges supported by the organization's plan. Never provide it to fork CI.
- Treat coverage content as untrusted even when it belongs to the correct run: a
  contributor can change both tests and workflow. These are review signals, not
  proof that a contribution is safe or honestly measured.
- This protects against executing project code/configuration by design. It is
  not a guarantee against vulnerabilities in GitHub Actions, Git, archive/XML
  parsers or Sonar analyzers. Review dependency updates at this trust boundary.
- The TypeScript snapshot has no installed dependencies or generated SvelteKit
  config. Type-aware results may be less complete than the CI's dedicated checks.
  Configuration files excluded from the snapshot are not analyzed by Sonar.
- `tools/tests/test_sonar.py` covers context validation, malicious archives, path
  traversal, XML entities and snapshot isolation via `just test-py`/`just check`.

## Rollout verification

`workflow_run` uses the default branch, so a PR changing this integration cannot
exercise its new privileged workflow until the trusted change is available there.
After landing it, run fresh CI for an internal PR and a fork PR (including one
whose workflow-run PR list is empty). Confirm:

- the analyzed SHA and PR number are correct;
- the analysis appears on the open PR, with coverage and Rust metrics;
- the scanner imports coverage and does not launch Cargo or dependency resolution;
- a failed CI does not scan and a failed Quality Gate does not block merging;
- a `main` push produces a branch analysis, not a PR analysis.

Do not test hostile code with the real token. Local adversarial tests use fixtures
and no credentials. Keep the `main` analysis current for accurate new-issue diffs.

References: [GitHub's checkout trust boundary](https://docs.github.com/en/actions/reference/security/securely-using-pull_request_target),
[Sonar Rust analysis](https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/languages/rust),
[Sonar dependency analysis](https://docs.sonarsource.com/sonarqube-cloud/advanced-security/analyzing-projects-for-dependencies-sca).
