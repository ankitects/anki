/// <reference lib="webworker" />

import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

import type { DbRequest, DbResponse, DeckSummary, LocalCollectionInfo } from "./types";

type SqliteDb = {
  exec: (options: string | Record<string, unknown>) => unknown;
  filename: string;
};

let db: SqliteDb | null = null;
let initPromise: Promise<LocalCollectionInfo> | null = null;

const workerScope = self as unknown as DedicatedWorkerGlobalScope;

async function initialize(): Promise<LocalCollectionInfo> {
  if (initPromise) {
    return initPromise;
  }

  initPromise = (async () => {
    const sqlite3 = await sqlite3InitModule({
      print: (...args: unknown[]) => console.debug("[sqlite]", ...args),
      printErr: (...args: unknown[]) => console.error("[sqlite]", ...args)
    });

    const canUseOpfs =
      globalThis.crossOriginIsolated &&
      "opfs" in sqlite3 &&
      typeof sqlite3.oo1.OpfsDb === "function";

    db = canUseOpfs
      ? new sqlite3.oo1.OpfsDb("/anki-pwa.sqlite3")
      : new sqlite3.oo1.DB(":memory:", "c");

    createSchema(db);
    seedDefaultDeck(db);

    return {
      sqliteVersion: sqlite3.version.libVersion,
      persistent: canUseOpfs,
      crossOriginIsolated: globalThis.crossOriginIsolated
    };
  })();

  return initPromise;
}

function createSchema(database: SqliteDb) {
  database.exec(`
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS app_meta (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS decks (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL UNIQUE,
      parent_id INTEGER REFERENCES decks(id) ON DELETE CASCADE,
      created_at INTEGER NOT NULL,
      modified_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS notes (
      id INTEGER PRIMARY KEY,
      guid TEXT NOT NULL UNIQUE,
      note_type TEXT NOT NULL DEFAULT 'basic',
      fields_json TEXT NOT NULL,
      tags TEXT NOT NULL DEFAULT '',
      created_at INTEGER NOT NULL,
      modified_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS cards (
      id INTEGER PRIMARY KEY,
      note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
      deck_id INTEGER NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
      ordinal INTEGER NOT NULL DEFAULT 0,
      state TEXT NOT NULL DEFAULT 'new' CHECK(state IN ('new', 'learning', 'review', 'relearning', 'suspended', 'buried')),
      due INTEGER NOT NULL DEFAULT 0,
      interval INTEGER NOT NULL DEFAULT 0,
      ease_factor INTEGER NOT NULL DEFAULT 2500,
      reps INTEGER NOT NULL DEFAULT 0,
      lapses INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL,
      modified_at INTEGER NOT NULL,
      UNIQUE(note_id, ordinal)
    );

    CREATE TABLE IF NOT EXISTS review_log (
      id INTEGER PRIMARY KEY,
      card_id INTEGER NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      reviewed_at INTEGER NOT NULL,
      rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 4),
      previous_interval INTEGER NOT NULL,
      interval INTEGER NOT NULL,
      time_ms INTEGER NOT NULL DEFAULT 0
    );

    CREATE INDEX IF NOT EXISTS idx_cards_deck_state_due
      ON cards(deck_id, state, due);
    CREATE INDEX IF NOT EXISTS idx_review_log_card_time
      ON review_log(card_id, reviewed_at);
  `);
}

function seedDefaultDeck(database: SqliteDb) {
  const now = Date.now();
  database.exec({
    sql: `
      INSERT OR IGNORE INTO decks(id, name, parent_id, created_at, modified_at)
      VALUES (1, 'Default', NULL, ?, ?)
    `,
    bind: [now, now]
  });
}

async function listDecks(): Promise<DeckSummary[]> {
  await initialize();
  if (!db) throw new Error("Database failed to initialize");

  const rows = db.exec({
    sql: `
      SELECT
        d.id,
        d.name,
        SUM(CASE WHEN c.state = 'new' THEN 1 ELSE 0 END) AS new_count,
        SUM(CASE WHEN c.state IN ('learning', 'relearning') THEN 1 ELSE 0 END) AS learning_count,
        SUM(CASE WHEN c.state = 'review' THEN 1 ELSE 0 END) AS review_count,
        COUNT(c.id) AS total_cards
      FROM decks d
      LEFT JOIN cards c ON c.deck_id = d.id
      GROUP BY d.id, d.name
      ORDER BY d.name COLLATE NOCASE
    `,
    returnValue: "resultRows",
    rowMode: "object"
  }) as Array<Record<string, unknown>>;

  return rows.map((row) => ({
    id: Number(row.id),
    name: String(row.name),
    newCount: Number(row.new_count ?? 0),
    learningCount: Number(row.learning_count ?? 0),
    reviewCount: Number(row.review_count ?? 0),
    totalCards: Number(row.total_cards ?? 0)
  }));
}

workerScope.addEventListener("message", async (event: MessageEvent<DbRequest>) => {
  const request = event.data;

  try {
    let result: unknown;

    switch (request.type) {
      case "init":
        result = await initialize();
        break;
      case "listDecks":
        result = await listDecks();
        break;
      default:
        throw new Error(`Unknown database request: ${(request as DbRequest).type}`);
    }

    const response: DbResponse = { id: request.id, ok: true, result };
    workerScope.postMessage(response);
  } catch (error) {
    const response: DbResponse = {
      id: request.id,
      ok: false,
      error: error instanceof Error ? error.message : String(error)
    };
    workerScope.postMessage(response);
  }
});
