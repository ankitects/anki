# Documentation Website

This directory contains [Mintlify](https://www.mintlify.com/) docs served at https://anki.mintlify.app/

## Build and validation

Run Mintlify commands from `docs-site/`:

- `mint dev --no-open`: build and serve a local preview.
- `mint validate`: validate the site.
- `mint broken-links`: check internal links.
- `mint a11y --skip-contrast`: check accessibility, as in CI.

CI runs validation and accessibility checks; see
[`docs-site.yml`](../.github/workflows/docs-site.yml).
The root `just docs` and `just docs-serve` recipes build the separate Sphinx docs,
not this site.

## Deployment

Mintlify deploys the `mintlify-docs` branch.

1. Open a PR to `main` and merge it through the normal review process.
2. For an update you want to deploy immediately, create a hotfix branch from
   the current `origin/mintlify-docs`. Cherry-pick only the merged fix with
   `git cherry-pick -x`, validate, push the hotfix branch, and open a PR
   targeting `mintlify-docs`. Do not push directly to `mintlify-docs`.
3. Merging that PR publishes the site. Keep the `-x` source reference in the
   merged commit message. Cherry-picking changes the commit hash; matching
   hashes are not required.
4. Otherwise, stable releases run `just docs-release` and replace the deploy
   branch from the release commit; see
   [`sync-release.yml`](../.github/workflows/sync-release.yml).

Do not publish all of `main`: it may document unreleased features. Never make a
fix only on the deploy branch: the next release will overwrite it. If a pending
release branch predates the fix, ensure it includes the fix too.

## License

Unless stated otherwise, any documentation changes you submit will be licensed under the [CC BY-SA 4 license](https://creativecommons.org/licenses/by-sa/4.0/).

## Translations

To help translate the website to your language, please see
[Translating the Documentation](./translators/anki/manual.mdx).
