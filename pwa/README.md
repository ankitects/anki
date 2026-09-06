# Anki PWA

A frontend-only Next.js PWA foundation for an Anki-like mobile experience.

## Current milestone

- Next.js App Router PWA shell
- installable web app manifest
- offline service worker
- SQLite WASM in a dedicated worker
- persistent Origin Private File System (OPFS) collection storage when supported
- initial decks / notes / cards / review-log schema
- deck browser wired to the local collection

## Run

```bash
cd pwa
npm install
npm run dev
```

Open `http://localhost:3000`.

For a production-like PWA test:

```bash
npm run build
npm start
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
OPFS /anki-pwa.sqlite3
```

The browser worker owns the database. React components do not run SQLite queries directly.

## Next milestones

1. Add/edit/delete decks and notes.
2. Render Basic and Cloze card templates.
3. Add FSRS scheduling and review queues.
4. Add browser/search and statistics.
5. Import `.apkg` decks and media locally.
6. Add AnkiWeb shared-deck discovery.
7. Investigate direct AnkiWeb sync from the PWA subject to browser CORS restrictions.
