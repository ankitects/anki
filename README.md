# Anki Whimsified

Anki Whimsified is a two-client learning application built on Anki's shared
Rust backend.

## Applications

- [`web/`](web/) contains the desktop Anki fork (Rust core, Python/Qt desktop
  client, and web views).
- [`Anki-Android/`](Anki-Android/) contains the Android companion application.

Both applications must consume the same Rust behavior and preserve compatible
review and sync data. Product research and project documentation live in the
parent `superbuilder` workspace rather than this source repository.

## Development

Follow the instructions in each application directory:

- Desktop: [`web/README.md`](web/README.md)
- Android: [`Anki-Android/README.md`](Anki-Android/README.md)

Upstream desktop release workflows are preserved under
`web/.github/workflows-upstream/`. They are intentionally inactive until they
are adapted and authorized for this combined repository.

The workflows inherited inside `Anki-Android/.github/workflows/` are also
reference copies: GitHub only executes workflows from the repository-root
`.github/workflows/` directory. Promote individual application workflows to
the root deliberately as the combined CI strategy is established.

## Licensing

The desktop fork retains Anki's licensing and attribution in `web/`. The
Android application retains AnkiDroid's licensing and attribution in
`Anki-Android/`. Changes must remain compatible with those licenses.
