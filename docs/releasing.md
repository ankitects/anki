# Releasing

Releases are managed by two GitHub Actions workflows under `.github/workflows/`:

1. **`prepare-release.yml`** — Run first. Validates the version, checks that CI
   passed, updates `.version`, and pushes everything to the dispatching branch
   in a single commit. CI then runs automatically on the resulting commit (for
   `release/**` branches). The CI check can be skipped with `skip-ci-check`.

2. **`release.yml`** — Run after CI passes on the prepared commit. Builds
   installers and wheels for all platforms (Linux x86/ARM, macOS Intel/ARM,
   Windows), and can optionally sign macOS/Windows artifacts, create a draft
   GitHub release, publish wheels to TestPyPI, and publish wheels to PyPI.

Both workflows are `workflow_dispatch` and share a `release` concurrency group so
they cannot run simultaneously.

## Version format

Versions follow calendar versioning with PEP 440: `YY.MM` for stable releases
(e.g. `26.04`), with optional `.patch` (e.g. `26.04.1`) and pre-release
suffixes (`b1`, `rc1`, `a1`). Months must be zero-padded.

Examples: `26.05b1` (beta), `26.05rc1` (release candidate), `26.05` (stable),
`26.05.1` (patch).

## Release branch workflow

All releases are cut from a `release/YY.MM` branch. The branch name uses only
the major version (`YY.MM`), not the full pre-release suffix — betas, release
candidates, and the stable release all come from the same branch.

### Standard release

1. Create a release branch from `main`:
   ```
   git checkout -b release/26.05 main
   git push origin release/26.05
   ```

2. CI runs automatically on push to `release/**` branches.

3. Prepare the release (updates `.version` on the branch):
   ```
   just release::prepare --version 26.05b1 --ref release/26.05
   ```

4. Pull the version bump commit:
   ```
   git pull origin release/26.05
   ```

5. Verify on TestPyPI:
   ```
   just release::testpypi --ref release/26.05
   ```

6. Publish the full release:
   ```
   just release::public --ref release/26.05
   ```

7. For subsequent pre-releases or the stable release from the same cycle,
   repeat steps 3-6 with the new version (e.g. `26.05b2`, `26.05rc1`, `26.05`).

8. After the stable release, merge the release branch back to `main` to pick up
   the `.version` bump and any cherry-picked fixes:
   ```
   git checkout main
   git merge release/26.05
   git push origin main
   ```

9. Delete the release branch after the stable release is published.

### Security and hotfix releases

For security fixes, an admin should first create a
[security advisory](https://github.com/ankitects/anki/security/advisories)
with a temporary private fork. Work on the fix in the private fork via the
normal PR workflow. Do not open a public PR or publish the advisory until
the fix is ready for release.

Once the fix is ready:

1. Create a release branch from the latest release tag:
   ```
   git checkout -b release/26.05 26.05
   ```

2. Cherry-pick the fix onto the release branch.

3. Push the branch and wait for CI:
   ```
   git push origin release/26.05
   ```

4. Prepare and publish:
   ```
   just release::prepare --version 26.05.1 --ref release/26.05
   just release::public --ref release/26.05
   ```

5. Merge the release branch back to `main`.

6. For security patches, publish the advisory and credit the reporter if
   applicable.

## Release process overview

```{mermaid}
flowchart LR
    A["<b>prepare-release.yml</b><br/>validate version<br/>check CI<br/>check duplicate tag<br/>update .version<br/>push to branch"] --> B["<b>CI (ci.yml)</b><br/>runs automatically<br/>on release/** branches"]
    B --> C["<b>release.yml</b><br/>build all platforms<br/>optionally sign macOS/Windows<br/>optionally create draft GitHub release<br/>optionally publish to TestPyPI/PyPI"]

    style A fill:#2d333b,stroke:#539bf5,color:#adbac7
    style B fill:#2d333b,stroke:#e5c07b,color:#adbac7
    style C fill:#2d333b,stroke:#7ee787,color:#adbac7
```

## Release workflow jobs

```{mermaid}
flowchart TD
    prepare[prepare<br/><i>validate version,<br/>check CI, check duplicates</i>]

    prepare --> mac["build-and-sign-mac<br/>ARM"]
    prepare --> macint["build-and-sign-mac-intel<br/>Intel"]
    prepare --> win["build-and-sign-windows"]
    prepare --> lin[build-linux-x86<br/><i>installer + wheels</i>]
    prepare --> linarmw[build-linux-arm-wheels]
    prepare --> linarmi[build-linux-arm-installer]

    mac --> release
    macint --> release
    win --> release
    lin --> release
    linarmw --> release
    linarmi --> release

    release["release<br/>draft GitHub release<br/><i>if draft-release</i>"]

    mac --> testpypi
    macint --> testpypi
    win --> testpypi
    lin --> testpypi
    linarmw --> testpypi
    linarmi --> testpypi

    testpypi["publish-testpypi<br/>TestPyPI<br/><i>if publish-testpypi or publish-pypi</i>"]

    release --> pypi
    testpypi --> pypi

    pypi["publish-pypi<br/>PyPI<br/><i>if publish-pypi</i>"]

    style prepare fill:#2d333b,stroke:#539bf5,color:#adbac7
    style mac fill:#2d333b,stroke:#e5c07b,color:#adbac7
    style macint fill:#2d333b,stroke:#e5c07b,color:#adbac7
    style win fill:#2d333b,stroke:#e5c07b,color:#adbac7
    style lin fill:#2d333b,stroke:#e5c07b,color:#adbac7
    style linarmw fill:#2d333b,stroke:#e5c07b,color:#adbac7
    style linarmi fill:#2d333b,stroke:#e5c07b,color:#adbac7
    style release fill:#2d333b,stroke:#7ee787,color:#adbac7
    style testpypi fill:#2d333b,stroke:#7ee787,color:#adbac7
    style pypi fill:#2d333b,stroke:#7ee787,color:#adbac7
```

## Workflow inputs

**prepare-release:** takes a `version` string and an optional `skip-ci-check`
boolean (default `false`).

**release:** takes a `version` (must match `.version` for public release
operations) and five boolean inputs:

- `sign` signs macOS and Windows artifacts.
- `draft-release` creates the draft GitHub release.
- `publish-testpypi` publishes wheels to TestPyPI.
- `publish-pypi` publishes wheels to PyPI.
- `skip-ci-check` skips the CI status check.

All booleans default to `false`. Non-release runs use the `.version`
already in the repo, so builds work without a prepare step.

For a normal public release, enable the first four booleans: `sign=true`,
`draft-release=true`, `publish-testpypi=true`, and `publish-pypi=true`.

## Environment gates

The release workflow uses GitHub
[environments](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
as manual approval gates. Jobs that access signing credentials or publish
artifacts require a reviewer to approve the deployment before they run:

- **`release`** — Required when `sign`, `draft-release`, `publish-testpypi`, or
  `publish-pypi` is enabled. Protects code-signing secrets, the release token,
  and PyPI/TestPyPI trusted publishing/OIDC.

When `sign` is disabled, the macOS and Windows build jobs run without the
`release` environment so they do not require approval and cannot access signing
secrets.

## Workflow input behavior

The `release.yml` workflow uses independent boolean inputs to control what gets
signed and published:

| Input              | Effect                                                                                                                                                                                                                                            |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sign`             | Signs macOS and Windows artifacts. Requires the `release` environment. When false, those jobs upload unsigned artifacts and do not access signing secrets.                                                                                        |
| `draft-release`    | Creates a draft GitHub release with generated release notes and installer artifacts. Requires `sign=true`, the `release` environment, passing CI (unless skipped), no duplicate tag/release, and `version` matching `.version`.                   |
| `publish-testpypi` | Publishes wheels to TestPyPI. Requires the `release` environment.                                                                                                                                                                                 |
| `publish-pypi`     | Publishes wheels to PyPI. Requires the `release` environment, passing CI (unless skipped), and `version` matching `.version`. It also runs and waits for the TestPyPI publish job first. It does not require signing unless `draft-release=true`. |
| `skip-ci-check`    | Skips the CI status check. Useful for hotfix releases.                                                                                                                                                                                            |
| `version`          | For `draft-release` or `publish-pypi`: must match `.version`. For build-only, signed-only, or TestPyPI-only runs: ignored (`.version` from the branch is used automatically).                                                                     |

## Dispatching with just

Release workflows can be dispatched via `just` using the `release` module
defined in `release.just`. All recipes require an explicit `--ref` argument
pointing to the release branch.

Run `just --list --list-submodules` to see all available recipes and their
arguments.

## Testing the release workflow from a feature branch

`release.yml` can be dispatched from any branch for testing. The release guards
only apply when `draft-release` or `publish-pypi` is enabled. To run a test
build:

1. Dispatch `release.yml` from your branch with all boolean inputs left false:
   ```
   just release::build --ref <your-branch>
   ```
2. The workflow reads `.version` from the branch as-is (the version input is
   ignored for non-release runs), so no prepare step is needed.
3. All release guards (CI check, duplicate tag check) are skipped.
4. Artifacts are uploaded to the workflow run but nothing is published or tagged.

### Testing with code signing

To test the signing flow from a feature branch:

1. In the repo's Settings → Environments → `release`, temporarily add your
   branch to the allowed deployment branches.
2. Dispatch the workflow:
   ```
   just release::sign --ref <your-branch>
   ```
3. Approve the environment deployment when prompted.
4. After testing, remove your branch from the environment's allowed branches.

> **Note:** `workflow_dispatch` workflows only appear in the GitHub Actions UI
> if the workflow file exists on the default branch. If `release.yml` is new or
> modified on your branch, use `gh workflow run` to trigger it — the UI
> dropdown won't show it until it's merged to main.

## Important notes

- The release workflow builds the exact commit at `github.sha`. It does not
  write `.version` — that is done by the prepare workflow. If you dispatch
  release before prepare's commit has propagated, the build will use whatever
  `.version` was HEAD at dispatch time.
- `draft-release=true` with `sign=false` is rejected — draft releases must use
  signed installer artifacts.
- When `publish-pypi=true`, wheels are published to TestPyPI first, then to
  PyPI after the TestPyPI job succeeds. If `draft-release=true` is also set,
  PyPI publishing waits for the draft GitHub release to succeed too.
- Pre-release versions (e.g. `26.05b1`) are automatically marked as
  pre-releases on the GitHub draft release.

## Announcements

- Once a GitHub release draft is created, modify the generated changelog if necessary then click **Publish release**.
- Create a forum topic on the [Beta Testing](https://forums.ankiweb.net/c/anki/beta-testing/13) category. For stable releases, lock the topic and ask users to report issues on a new topic.
- For stable releases, update the version in [ankitects/anki-landing-page](https://github.com/ankitects/anki-landing-page) (See [example](https://github.com/ankitects/anki-landing-page/commit/2362eb2202f174df2aad1dc5336e1b5195a7af85)).
