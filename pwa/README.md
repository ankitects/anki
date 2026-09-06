# Anki PWA

A frontend-only Next.js PWA with an on-device Anki-format collection. No hosted database, backend, or paid service is needed for local study and imports.

## Current milestone

- Next.js App Router PWA shell
- installable web app manifest
- offline service worker
- SQLite WASM in a dedicated worker
- persistent Origin Private File System (OPFS) collection storage when supported
- initial decks / notes / cards / review-log schema
- deck browser wired to the local collection
- deck management with rename/delete, hierarchical subdecks, parent-deck study/counts, and moving cards between decks
- browse/search by text, tag or deck; edit/delete notes and tags; suspend and bury cards
- deck/note creation with dynamic note-type fields
- Basic, Cloze and custom-template review with local images/audio
- all six stock Anki note types: Basic, reversed, optional reversed, type-in-answer, Cloze, and Image Occlusion
- FSRS scheduling and local review history
- local `.apkg` import with legacy and modern (Zstd/protobuf) package support
- AnkiWeb shared-deck search launcher and local `.apkg` import
- downloadable `.colpkg` collection backups with scheduling history and media

## Import a deck

On the Decks screen, select **Import**, choose an `.apkg`, then select **Import deck**.

To find a public deck, select **Shared**, enter a search, and choose **Search AnkiWeb**. Results open on AnkiWeb so your AnkiWeb login remains private and its anonymous-search limit is not shared by a proxy server. Download the `.apkg`, return to this PWA, and choose **Open Import**. The package goes directly from AnkiWeb to your device.

- Notes, cards, tags, templates, CSS, and packaged media are imported locally.
- Scheduling/history are kept by default. Uncheck the option to start imported cards as new.
- Notes already present with the same Anki GUID are skipped, including changed notes. Re-importing is not an update/merge operation.
- Existing notes, cards and presets are not overwritten. Conflicting IDs are remapped; conflicting media filenames are renamed and their references updated.
- Cards return to their original decks when imported from filtered decks. Day-based due dates are adjusted to the local collection's creation date.
- Imports are limited to 128 MiB per package/database, 64 MiB per media file, 512 MiB expanded data and 50,000 ZIP entries. Larger decks should be split/exported in smaller batches in Anki.
- Database changes are transactional. A failed import rolls them back and removes newly written media. Closing the tab mid-import can leave unused media, but cannot partially commit notes/cards. Keep the tab open until completion.

Image Occlusion creation supports Anki-compatible rectangular masks, including “Hide all, guess one”. Imported rectangle, ellipse, and polygon masks are rendered during review. Anki's advanced polygon/text mask editor is not available yet.

Collection backups can be imported into the official Anki desktop app. Restoring a `.colpkg` directly into this PWA is not supported yet. This is not full Anki feature parity: custom note-type/template management, deck-option presets, add-ons, AnkiWeb sync, and script-dependent templates are not supported. Imported cards use this PWA's existing FSRS settings. Some advanced template filters and MathJax are not rendered. Keep an external backup of important collections.

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

The browse smoke test covers note/tag editing, card status changes, card moves, subdeck creation/rename, and recursive deck deletion. The shared-deck UI smoke test verifies that the search form targets AnkiWeb directly and does not consume AnkiWeb's anonymous search limit.

## Next milestones

1. Statistics and configurable deck options.
2. More complete template rendering.
3. Further investigation of AnkiWeb interoperability. Shared-deck discovery links to AnkiWeb; account sync is not supported.
