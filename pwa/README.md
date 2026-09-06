# Anki PWA

A frontend-only Next.js PWA with an on-device Anki-format collection. No hosted backend or paid service is needed for local study and imports.

## Current milestone

- Next.js App Router PWA shell
- installable web app manifest
- offline service worker
- SQLite WASM in a dedicated worker
- persistent Origin Private File System (OPFS) collection storage when supported
- initial decks / notes / cards / review-log schema
- deck browser wired to the local collection
- deck/note creation with dynamic note-type fields
- Basic, Cloze and custom-template review with local images/audio
- FSRS scheduling and local review history
- local `.apkg` import with legacy and modern (Zstd/protobuf) package support

## Import a deck

On the Decks screen, select **Import**, choose an `.apkg`, then select **Import deck**.

- Notes, cards, tags, templates, CSS, and packaged media are imported locally.
- Scheduling/history are kept by default. Uncheck the option to start imported cards as new.
- Notes already present with the same Anki GUID are skipped, including changed notes. Re-importing is not an update/merge operation.
- Existing notes, cards and presets are not overwritten. Conflicting IDs are remapped; conflicting media filenames are renamed and their references updated.
- Cards return to their original decks when imported from filtered decks. Day-based due dates are adjusted to the local collection's creation date.
- Imports are limited to 128 MiB per package/database, 64 MiB per media file, 512 MiB expanded data and 50,000 ZIP entries. Larger decks should be split/exported in smaller batches in Anki.
- Database changes are transactional. A failed import rolls them back and removes newly written media. Closing the tab mid-import can leave unused media, but cannot partially commit notes/cards. Keep the tab open until completion.

This is not full Anki feature parity: deck-option presets, add-ons, collection backups (`.colpkg`), AnkiWeb sync, and script-dependent templates are not supported. Imported cards use this PWA's existing FSRS settings. Some advanced template filters and MathJax are not rendered. Keep your original Anki collection/export as your backup.

When the app reports **Temporary storage fallback**, imported data will not survive a reload. Browser/site-data clearing can also remove persistent OPFS data.

## Run

Use Node.js 22.15+ and install dependencies with `npm --prefix pwa ci`.

From the repository root:

```bash
just pwa-dev
just pwa-check
just pwa-build
```

If `just` is unavailable, the equivalent commands are `npm --prefix pwa run dev`, `npm --prefix pwa run typecheck`, `npm --prefix pwa test`, and `npm --prefix pwa run build`.

Open `http://localhost:3000`.

For a production-like PWA test:

```bash
just pwa-build
npm --prefix pwa start
```

The SQLite worker needs the COOP/COEP response headers configured in `next.config.mjs`. They are required for the cross-origin-isolated browser environment used by SQLite's OPFS implementation.

## Architecture

```text
Next.js UI
   |
   v
Typed worker RPC
   |
   v
SQLite WASM worker
   |
   v
OPFS .anki-pwa-v2 (collection) + anki-pwa-media-v2 (media)
```

The browser worker owns the database. React components do not run SQLite queries directly.

Import tests use the repository's original `.apkg` fixtures plus generated schema-18 packages. They cover scheduling, duplicate handling, ID/media collisions, corrupted archives, failed media writes and database rollback.

For the browser smoke test, start a production PWA on a dedicated test origin and open it in a **fresh Chrome profile** with remote debugging enabled. Run `just pwa-test-browser 9230 http://127.0.0.1:3002/ legacy` (or `modern`). Without `just`, use `npm --prefix pwa run test:browser -- 9230 http://127.0.0.1:3002/ modern`. The test adds a small synthetic deck, imports it twice, checks invalid-package handling, then reloads and reviews offline. Never point it at your real collection.

## Next milestones

1. Browse/search, edit and delete existing notes/cards.
2. Local collection export/backup.
3. Statistics and configurable deck options.
4. More complete template rendering.
5. Shared-deck discovery and investigation of AnkiWeb interoperability.
