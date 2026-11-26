---
name: docs
description: Edit, preview, and validate Anki's Mintlify documentation in docs-site/. Use for website docs changes.
---

# Documentation website

- Source: `docs-site/`; configuration and navigation: `docs-site/docs.json`.
- Platform: Mintlify. Follow nearby MDX conventions and keep changes minimal.
- Vendor docs: https://www.mintlify.com/docs/llms.txt.

## Preview and validate

Run Mintlify commands directly from `docs-site/` (an exception to the root
just-only rule). `just docs` and `just docs-serve` build the separate Sphinx docs.

- `mint instructions`: current vendor guidance. If unsupported, use
  `npx mint@latest instructions` after approval to download the newer CLI, or
  read the vendor docs above. Repo deployment guidance in `docs-site/README.md` takes precedence.
- `mint dev --no-open`: local preview.
- `mint validate`: validate the site.
- `mint broken-links`: check internal links.
- `mint a11y --skip-contrast`: accessibility check, as in CI.

CI installs `mint` and checks validation and accessibility; see
`.github/workflows/docs-site.yml`.
Do not run site-wide formatting or install integrations for a small content fix.

After editing `docs-site/developers/`, run `just fix-fmt` to regenerate the
`docs/*.md` copies, then `just fmt` to check them. Commit the generated changes
with their source edits; do not edit the copies by hand.

For build and deployment, see `docs-site/README.md`.
