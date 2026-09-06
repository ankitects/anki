/// <reference lib="webworker" />

import sqlite3InitModule from "@sqlite.org/sqlite-wasm";
import type { Database, Sqlite3Static } from "@sqlite.org/sqlite-wasm";
import { Rating, State, default_w, fsrs } from "ts-fsrs";
import type { CardInput, Grade, RecordLogItem } from "ts-fsrs";

import { clozeOrdinals, renderAnkiCard } from "../anki/template";
import type { AnkiNotetype } from "../anki/template";
import { importApkg } from "../anki/import-apkg";
import type { ImportMediaStore } from "../anki/import-apkg";
import type {
  CardState,
  DbRequest,
  DbResponse,
  DeckSummary,
  LocalCollectionInfo,
  NoteTypeSummary,
  ReviewRating,
  StudyCard
} from "./types";

const SCHEMA_VERSION = 11;
const BASIC_NOTETYPE_ID = 1_600_000_000_000;
const CLOZE_NOTETYPE_ID = 1_600_000_000_001;
const DEFAULT_DECK_ID = 1;
const FIELD_SEPARATOR = "\u001f";
const LEARNING_STEPS = ["1m", "10m"] as const;
const RELEARNING_STEPS = ["10m"] as const;
const REQUEST_RETENTION = 0.9;

const scheduler = fsrs({
  request_retention: REQUEST_RETENTION,
  maximum_interval: 36_500,
  enable_fuzz: true,
  enable_short_term: true,
  learning_steps: LEARNING_STEPS,
  relearning_steps: RELEARNING_STEPS
});

type AnkiDeck = {
  id: number;
  name: string;
  mod: number;
  usn: number;
  dyn: number;
  conf: number;
  [key: string]: unknown;
};

type StoredCardData = {
  s?: number;
  d?: number;
  dr?: number;
  decay?: number;
  lrt?: number;
};

let db: Database | null = null;
let sqliteRuntime: Sqlite3Static | null = null;
let initPromise: Promise<LocalCollectionInfo> | null = null;
const memoryMedia = new Map<string, Uint8Array>();

const workerScope = self as unknown as DedicatedWorkerGlobalScope;

function disableProxyBasedOpfsVfses() {
  const sqliteGlobal = globalThis as typeof globalThis & {
    sqlite3ApiConfig?: { disable: { vfs: Record<string, boolean> } };
  };

  // Next/Webpack's URL shim drops query parameters added after construction.
  // The proxy VFSes require one on their nested worker, so use the bundler-safe
  // SAH-pool VFS instead.
  sqliteGlobal.sqlite3ApiConfig = {
    disable: { vfs: { opfs: true, "opfs-wl": true } }
  };
}

function nowSeconds() {
  return Math.floor(Date.now() / 1000);
}

function collectionCreationSeconds() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  return Math.floor(start.getTime() / 1000);
}

function defaultDeck(mod: number): AnkiDeck {
  return {
    id: DEFAULT_DECK_ID,
    name: "Default",
    mod,
    usn: -1,
    lrnToday: [0, 0],
    revToday: [0, 0],
    newToday: [0, 0],
    timeToday: [0, 0],
    collapsed: false,
    browserCollapsed: false,
    desc: "",
    dyn: 0,
    conf: 1,
    extendNew: 0,
    extendRev: 0
  };
}

function basicNotetype(mod: number): AnkiNotetype {
  return {
    id: BASIC_NOTETYPE_ID,
    name: "Basic",
    type: 0,
    mod,
    usn: -1,
    sortf: 0,
    did: null,
    tmpls: [
      {
        name: "Card 1",
        ord: 0,
        qfmt: "{{Front}}",
        afmt: "{{FrontSide}}\n\n<hr id=answer>\n\n{{Back}}",
        bqfmt: "",
        bafmt: "",
        did: null,
        bfont: "Arial",
        bsize: 12
      }
    ],
    flds: [
      { name: "Front", ord: 0, sticky: false, rtl: false, font: "Arial", size: 20 },
      { name: "Back", ord: 1, sticky: false, rtl: false, font: "Arial", size: 20 }
    ],
    css: ".card { font-family: Arial; font-size: 20px; text-align: center; color: black; background: white; }",
    latexPre: "",
    latexPost: "",
    latexsvg: false,
    req: [[0, "any", [0]]]
  };
}

function clozeNotetype(mod: number): AnkiNotetype {
  return {
    id: CLOZE_NOTETYPE_ID,
    name: "Cloze",
    type: 1,
    mod,
    usn: -1,
    sortf: 0,
    did: null,
    tmpls: [
      {
        name: "Cloze",
        ord: 0,
        qfmt: "{{cloze:Text}}",
        afmt: "{{cloze:Text}}<br>{{Back Extra}}",
        bqfmt: "",
        bafmt: "",
        did: null,
        bfont: "Arial",
        bsize: 12
      }
    ],
    flds: [
      { name: "Text", ord: 0, sticky: false, rtl: false, font: "Arial", size: 20 },
      { name: "Back Extra", ord: 1, sticky: false, rtl: false, font: "Arial", size: 20 }
    ],
    css: ".card { font-family: Arial; font-size: 20px; text-align: center; color: black; background: white; } .cloze { font-weight: bold; color: #1f6fd1; }",
    latexPre: "",
    latexPost: "",
    latexsvg: false,
    req: [[0, "any", [0]]]
  };
}

function defaultDeckConfig(mod: number) {
  return {
    1: {
      id: 1,
      mod,
      name: "Default",
      usn: -1,
      maxTaken: 60,
      autoplay: true,
      timer: 0,
      replayq: true,
      dyn: false,
      fsrsParams6: [...default_w],
      desiredRetention: REQUEST_RETENTION,
      new: { bury: false, delays: [1, 10], initialFactor: 2500, ints: [1, 4, 0], order: 1, perDay: 20 },
      rev: { bury: false, ease4: 1.3, ivlFct: 1, maxIvl: 36500, perDay: 200, hardFactor: 1.2 },
      lapse: { delays: [10], leechAction: 1, leechFails: 8, minInt: 1, mult: 0 }
    }
  };
}

async function initialize(): Promise<LocalCollectionInfo> {
  if (initPromise) return initPromise;

  initPromise = (async () => {
    disableProxyBasedOpfsVfses();
    const sqlite3 = await sqlite3InitModule();
    sqliteRuntime = sqlite3;

    let database: Database;
    let persistent = false;

    try {
      // V2 intentionally leaves the earlier prototype database intact. This
      // file uses Anki's collection schema instead of the prototype tables.
      const sahPool = await sqlite3.installOpfsSAHPoolVfs({ directory: ".anki-pwa-v2" });
      database = new sahPool.OpfsSAHPoolDb("/collection.anki2");
      persistent = true;
    } catch (error) {
      console.warn("[sqlite] OPFS storage is unavailable; using an in-memory collection", error);
      database = new sqlite3.oo1.DB(":memory:", "c");
    }

    db = database;
    createAnkiSchema(database);
    ensureFsrsConfiguration(database);

    return {
      sqliteVersion: sqlite3.version.libVersion,
      schemaVersion: SCHEMA_VERSION,
      persistent,
      crossOriginIsolated: globalThis.crossOriginIsolated
    };
  })();

  return initPromise;
}

function ensureFsrsConfiguration(database: Database) {
  const conf = JSON.parse(String(database.selectValue("SELECT conf FROM col WHERE id = 1") ?? "{}")) as Record<string, unknown>;
  const deckConfigs = JSON.parse(String(database.selectValue("SELECT dconf FROM col WHERE id = 1") ?? "{}")) as Record<string, Record<string, unknown>>;
  const models = JSON.parse(String(database.selectValue("SELECT models FROM col WHERE id = 1") ?? "{}")) as Record<string, AnkiNotetype>;
  const defaultConfig = deckConfigs["1"];
  let changed = false;

  if (conf.fsrs !== true) {
    conf.fsrs = true;
    changed = true;
  }
  if (conf.fsrsShortTermWithStepsEnabled !== true) {
    conf.fsrsShortTermWithStepsEnabled = true;
    changed = true;
  }
  if (defaultConfig && !Array.isArray(defaultConfig.fsrsParams6)) {
    defaultConfig.fsrsParams6 = [...default_w];
    defaultConfig.desiredRetention = REQUEST_RETENTION;
    changed = true;
  }
  if (!models[String(BASIC_NOTETYPE_ID)]) {
    models[String(BASIC_NOTETYPE_ID)] = basicNotetype(nowSeconds());
    changed = true;
  }
  if (!models[String(CLOZE_NOTETYPE_ID)]) {
    models[String(CLOZE_NOTETYPE_ID)] = clozeNotetype(nowSeconds());
    changed = true;
  }

  if (changed) {
    database.transaction("IMMEDIATE", (transaction) => {
      transaction.exec({
        sql: "UPDATE col SET conf = ?, dconf = ?, models = ?, mod = ?, usn = -1 WHERE id = 1",
        bind: [JSON.stringify(conf), JSON.stringify(deckConfigs), JSON.stringify(models), nowSeconds()]
      });
    });
  }
}

function createAnkiSchema(database: Database) {
  const exists = Number(
    database.selectValue("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'col'") ?? 0
  );
  if (exists) return;

  const mod = nowSeconds();
  const now = Date.now();
  const decks = { [DEFAULT_DECK_ID]: defaultDeck(mod) };
  const models = {
    [BASIC_NOTETYPE_ID]: basicNotetype(mod),
    [CLOZE_NOTETYPE_ID]: clozeNotetype(mod)
  };

  database.transaction("IMMEDIATE", (transaction) => {
    transaction.exec(`
      CREATE TABLE col (
        id integer PRIMARY KEY, crt integer NOT NULL, mod integer NOT NULL,
        scm integer NOT NULL, ver integer NOT NULL, dty integer NOT NULL,
        usn integer NOT NULL, ls integer NOT NULL, conf text NOT NULL,
        models text NOT NULL, decks text NOT NULL, dconf text NOT NULL, tags text NOT NULL
      );
      CREATE TABLE notes (
        id integer PRIMARY KEY, guid text NOT NULL, mid integer NOT NULL,
        mod integer NOT NULL, usn integer NOT NULL, tags text NOT NULL,
        flds text NOT NULL, sfld integer NOT NULL, csum integer NOT NULL,
        flags integer NOT NULL, data text NOT NULL
      );
      CREATE TABLE cards (
        id integer PRIMARY KEY, nid integer NOT NULL, did integer NOT NULL,
        ord integer NOT NULL, mod integer NOT NULL, usn integer NOT NULL,
        type integer NOT NULL, queue integer NOT NULL, due integer NOT NULL,
        ivl integer NOT NULL, factor integer NOT NULL, reps integer NOT NULL,
        lapses integer NOT NULL, left integer NOT NULL, odue integer NOT NULL,
        odid integer NOT NULL, flags integer NOT NULL, data text NOT NULL
      );
      CREATE TABLE revlog (
        id integer PRIMARY KEY, cid integer NOT NULL, usn integer NOT NULL,
        ease integer NOT NULL, ivl integer NOT NULL, lastIvl integer NOT NULL,
        factor integer NOT NULL, time integer NOT NULL, type integer NOT NULL
      );
      CREATE TABLE graves (usn integer NOT NULL, oid integer NOT NULL, type integer NOT NULL);
      CREATE INDEX ix_notes_usn ON notes (usn);
      CREATE INDEX ix_cards_usn ON cards (usn);
      CREATE INDEX ix_revlog_usn ON revlog (usn);
      CREATE INDEX ix_cards_nid ON cards (nid);
      CREATE INDEX ix_cards_sched ON cards (did, queue, due);
      CREATE INDEX ix_revlog_cid ON revlog (cid);
      CREATE INDEX ix_notes_csum ON notes (csum);
      PRAGMA user_version = 11;
    `);
    transaction.exec({
      sql: `INSERT INTO col
        (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags)
        VALUES (1, ?, ?, ?, ?, 0, -1, 0, ?, ?, ?, ?, '{}')`,
      bind: [
        collectionCreationSeconds(),
        mod,
        now,
        SCHEMA_VERSION,
        JSON.stringify({ fsrs: true, fsrsShortTermWithStepsEnabled: true }),
        JSON.stringify(models),
        JSON.stringify(decks),
        JSON.stringify(defaultDeckConfig(mod))
      ]
    });
  });
}

function collection(): Database {
  if (!db) throw new Error("Database failed to initialize");
  return db;
}

function readDecks(database = collection()): Record<string, AnkiDeck> {
  const json = String(database.selectValue("SELECT decks FROM col WHERE id = 1") ?? "{}");
  return JSON.parse(json) as Record<string, AnkiDeck>;
}

function readNotetypes(database = collection()): Record<string, AnkiNotetype> {
  const json = String(database.selectValue("SELECT models FROM col WHERE id = 1") ?? "{}");
  return JSON.parse(json) as Record<string, AnkiNotetype>;
}

function collectionDay(database = collection()) {
  const creation = Number(database.selectValue("SELECT crt FROM col WHERE id = 1") ?? collectionCreationSeconds());
  return Math.max(0, Math.floor((nowSeconds() - creation) / 86_400));
}

function touchCollection(database: Database) {
  database.exec({
    // scm tracks schema changes, not ordinary collection edits.
    sql: "UPDATE col SET mod = ?, usn = -1 WHERE id = 1",
    bind: [nowSeconds()]
  });
}

function deckSummary(deck: AnkiDeck, database = collection()): DeckSummary {
  const today = collectionDay(database);
  const now = nowSeconds();
  const counts = database.selectObject(
    `SELECT
       sum(CASE WHEN queue = 0 THEN 1 ELSE 0 END) AS new_count,
       sum(CASE WHEN (queue = 1 AND due <= ?) OR (queue = 3 AND due <= ?) THEN 1 ELSE 0 END) AS learning_count,
       sum(CASE WHEN queue = 2 AND due <= ? THEN 1 ELSE 0 END) AS review_count,
       count(*) AS total_cards
     FROM cards WHERE did = ?`,
    [now, today, today, deck.id]
  );

  return {
    id: deck.id,
    name: deck.name,
    newCount: Number(counts?.new_count ?? 0),
    learningCount: Number(counts?.learning_count ?? 0),
    reviewCount: Number(counts?.review_count ?? 0),
    totalCards: Number(counts?.total_cards ?? 0)
  };
}

async function listDecks(): Promise<DeckSummary[]> {
  await initialize();
  const database = collection();
  return Object.values(readDecks(database))
    .filter((deck) => deck.dyn === 0)
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" }))
    .map((deck) => deckSummary(deck, database));
}

async function listNotetypes(): Promise<NoteTypeSummary[]> {
  await initialize();
  return Object.values(readNotetypes())
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" }))
    .map((notetype) => ({
      id: notetype.id,
      name: notetype.name,
      kind: notetype.type === 1 ? "cloze" : "standard",
      fields: notetype.flds
        .map((field, index) => ({ name: field.name, ordinal: field.ord ?? index }))
        .sort((a, b) => a.ordinal - b.ordinal)
        .map((field) => field.name)
    }));
}

async function createDeck(nameInput: string): Promise<DeckSummary> {
  await initialize();
  const database = collection();
  const name = nameInput.trim();
  if (!name) throw new Error("Deck name cannot be empty");

  const decks = readDecks(database);
  if (Object.values(decks).some((deck) => deck.name.localeCompare(name, undefined, { sensitivity: "base" }) === 0)) {
    throw new Error("A deck with that name already exists");
  }

  let id = Date.now();
  while (decks[String(id)]) id += 1;
  const deck = { ...defaultDeck(nowSeconds()), id, name };
  decks[String(id)] = deck;

  database.transaction("IMMEDIATE", (transaction) => {
    transaction.exec({ sql: "UPDATE col SET decks = ? WHERE id = 1", bind: [JSON.stringify(decks)] });
    touchCollection(transaction);
  });
  return deckSummary(deck, database);
}

function plainText(html: string) {
  return html
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/gi, " ")
    .trim();
}

function safeMediaFilename(filename: string) {
  const basename = filename.normalize("NFC").split(/[\\/]/).pop()?.trim() ?? "";
  const safe = basename.replace(/[\u0000-\u001f\u007f"'<>]/g, "_");
  if (!safe || safe === "." || safe === "..") throw new Error("Invalid media filename");
  return safe;
}

async function mediaDirectory(create: boolean) {
  if (!navigator.storage?.getDirectory) return null;
  try {
    const root = await navigator.storage.getDirectory();
    return await root.getDirectoryHandle("anki-pwa-media-v2", { create });
  } catch {
    return null;
  }
}

async function storeMedia(filenameInput: string, bytes: ArrayBuffer) {
  const filename = safeMediaFilename(filenameInput);
  const directory = await mediaDirectory(true);
  if (directory) {
    const handle = await directory.getFileHandle(filename, { create: true });
    const writable = await handle.createWritable();
    await writable.write(bytes);
    await writable.close();
  } else {
    memoryMedia.set(filename, new Uint8Array(bytes.slice(0)));
  }
  return filename;
}

async function importMediaStore(persistent: boolean): Promise<ImportMediaStore> {
  if (!persistent) return {
    read: async (name) => memoryMedia.get(name),
    write: async (name, bytes) => { memoryMedia.set(name, bytes.slice()); },
    remove: async (name) => { memoryMedia.delete(name); }
  };
  // Do not silently fall back to memory when persistent media storage fails.
  const root = await navigator.storage.getDirectory();
  const directory = await root.getDirectoryHandle("anki-pwa-media-v2", { create: true });
  return {
    read: async (name) => {
      try {
        const file = await (await directory.getFileHandle(name)).getFile();
        return new Uint8Array(await file.arrayBuffer());
      } catch (error) {
        if (error instanceof DOMException && error.name === "NotFoundError") return undefined;
        throw error;
      }
    },
    write: async (name, bytes) => {
      const handle = await directory.getFileHandle(name, { create: true });
      const writable = await handle.createWritable();
      try {
        await writable.write(new Uint8Array(bytes));
        await writable.close();
      } catch (error) {
        await writable.abort().catch(() => {});
        throw error;
      }
    },
    remove: async (name) => {
      try { await directory.removeEntry(name); }
      catch (error) { if (!(error instanceof DOMException && error.name === "NotFoundError")) throw error; }
    }
  };
}

function mediaMimeType(filename: string) {
  const extension = filename.split(".").pop()?.toLowerCase();
  const types: Record<string, string> = {
    avif: "image/avif", gif: "image/gif", jpg: "image/jpeg", jpeg: "image/jpeg",
    png: "image/png", svg: "image/svg+xml", webp: "image/webp",
    mp3: "audio/mpeg", m4a: "audio/mp4", mp4: "video/mp4", oga: "audio/ogg",
    ogg: "audio/ogg", opus: "audio/ogg", wav: "audio/wav", webm: "audio/webm",
    woff: "font/woff", woff2: "font/woff2", ttf: "font/ttf", otf: "font/otf"
  };
  return types[extension ?? ""] ?? "application/octet-stream";
}

function base64(bytes: Uint8Array) {
  let binary = "";
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
  }
  return btoa(binary);
}

async function mediaDataUrl(filenameInput: string) {
  let filename: string;
  try {
    filename = safeMediaFilename(decodeURIComponent(filenameInput.replaceAll("&amp;", "&")));
  } catch {
    return null;
  }

  let bytes = memoryMedia.get(filename);
  if (!bytes) {
    const directory = await mediaDirectory(false);
    if (!directory) return null;
    try {
      const file = await (await directory.getFileHandle(filename)).getFile();
      bytes = new Uint8Array(await file.arrayBuffer());
    } catch {
      return null;
    }
  }
  return `data:${mediaMimeType(filename)};base64,${base64(bytes)}`;
}

async function inlineMedia(html: string, css: string) {
  const filenames = new Set<string>();
  for (const match of html.matchAll(/\[sound:([^\]]+)]/gi)) filenames.add(match[1]);
  for (const match of html.matchAll(/\b(?:src|poster)\s*=\s*["']([^"']+)["']/gi)) filenames.add(match[1]);
  for (const match of `${html}\n${css}`.matchAll(/url\(\s*["']?([^"')]+)["']?\s*\)/gi)) filenames.add(match[1].trim());

  const urls = new Map<string, string>();
  for (const filename of filenames) {
    if (/^(?:data:|blob:|https?:|\/|#)/i.test(filename)) continue;
    const url = await mediaDataUrl(filename);
    if (url) urls.set(filename, url);
  }

  const inlineCssUrls = (value: string) => value.replace(/url\(\s*(["']?)([^"')]+)\1\s*\)/gi, (original, _quote: string, filename: string) => {
    const url = urls.get(filename.trim());
    return url ? `url(${url})` : original;
  });
  const renderedHtml = inlineCssUrls(html)
    .replace(/\[sound:([^\]]+)]/gi, (marker, filename: string) => {
      const url = urls.get(filename);
      return url
        ? `<audio class="anki-audio" controls preload="metadata" src="${url}"></audio>`
        : marker;
    })
    .replace(/\b(src|poster)\s*=\s*(["'])([^"']+)\2/gi, (attribute, name: string, quote: string, filename: string) => {
      const url = urls.get(filename);
      return url ? `${name}=${quote}${url}${quote}` : attribute;
    });
  const renderedCss = inlineCssUrls(css);

  return { html: renderedHtml, css: renderedCss };
}

async function fieldChecksum(value: string) {
  const bytes = new TextEncoder().encode(plainText(value));
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-1", bytes));
  return digest.slice(0, 4).reduce((number, byte) => number * 256 + byte, 0);
}

function uniqueId(database: Database, table: "notes" | "cards" | "revlog", candidate = Date.now()) {
  const maximum = Number(database.selectValue(`SELECT max(id) FROM ${table}`) ?? 0);
  return Math.max(candidate, maximum + 1);
}

async function insertNote(deckId: number, notetypeId: number, fields: string[], ordinals: number[]): Promise<number[]> {
  await initialize();
  const database = collection();
  if (!fields[0]?.trim()) throw new Error("The first field cannot be empty");
  if (!readDecks(database)[String(deckId)]) throw new Error("Deck not found");
  if (!readNotetypes(database)[String(notetypeId)]) throw new Error("Note type not found");
  if (!ordinals.length) throw new Error("This note does not generate any cards");

  const checksum = await fieldChecksum(fields[0]);
  const noteId = uniqueId(database, "notes");
  const firstCardId = uniqueId(database, "cards", noteId + 1);
  const firstDue = Number(database.selectValue("SELECT coalesce(max(due), 0) + 1 FROM cards WHERE type = 0") ?? 1);
  const mod = nowSeconds();
  const guid = crypto.randomUUID().replaceAll("-", "").slice(0, 10);
  const cardIds = ordinals.map((_ordinal, index) => firstCardId + index);

  database.transaction("IMMEDIATE", (transaction) => {
    transaction.exec({
      sql: `INSERT INTO notes
        (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data)
        VALUES (?, ?, ?, ?, -1, '', ?, ?, ?, 0, '')`,
      bind: [noteId, guid, notetypeId, mod, fields.join(FIELD_SEPARATOR), plainText(fields[0]), checksum]
    });
    ordinals.forEach((ordinal, index) => {
      transaction.exec({
        sql: `INSERT INTO cards
          (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data)
          VALUES (?, ?, ?, ?, ?, -1, 0, 0, ?, 0, 0, 0, 0, 0, 0, 0, 0, '')`,
        bind: [cardIds[index], noteId, deckId, ordinal, mod, firstDue + index]
      });
    });
    touchCollection(transaction);
  });

  return cardIds;
}

async function addBasicNote(deckId: number, frontInput: string, backInput: string) {
  return insertNote(deckId, BASIC_NOTETYPE_ID, [frontInput.trim(), backInput.trim()], [0]);
}

async function addClozeNote(deckId: number, textInput: string, extraInput: string) {
  const text = textInput.trim();
  const ordinals = clozeOrdinals([text]);
  if (!ordinals.length) throw new Error("Add at least one cloze deletion, such as {{c1::answer}}");
  return insertNote(deckId, CLOZE_NOTETYPE_ID, [text, extraInput.trim()], ordinals);
}

function standardCardOrdinals(notetype: AnkiNotetype, fields: string[]) {
  return notetype.tmpls.flatMap((template, index) => {
    const ordinal = template.ord ?? index;
    const requirement = notetype.req?.find(([cardOrdinal]) => cardOrdinal === ordinal);
    if (requirement) {
      const [, kind, fieldOrdinals] = requirement;
      const present = fieldOrdinals.map((fieldOrdinal) => plainText(fields[fieldOrdinal] ?? "").trim().length > 0);
      const generates = kind === "all"
        ? present.every(Boolean)
        : kind === "none"
          ? present.every((value) => !value)
          : present.some(Boolean);
      return generates ? [ordinal] : [];
    }

    try {
      const question = renderAnkiCard(notetype, fields, ordinal).questionHtml;
      return plainText(question).trim() ? [ordinal] : [];
    } catch {
      return [];
    }
  });
}

async function addNoteForNotetype(deckId: number, notetypeId: number, fieldsInput: string[]) {
  await initialize();
  const notetype = readNotetypes()[String(notetypeId)];
  if (!notetype) throw new Error("Note type not found");
  const fields = notetype.flds.map((_field, index) => fieldsInput[index]?.trim() ?? "");
  const ordinals = notetype.type === 1 ? clozeOrdinals(fields) : standardCardOrdinals(notetype, fields);
  if (notetype.type === 1 && !ordinals.length) {
    throw new Error("Add at least one cloze deletion, such as {{c1::answer}}");
  }
  return insertNote(deckId, notetypeId, fields, ordinals);
}

function stateForCard(type: number, queue: number): CardState {
  if (queue === 0 || type === 0) return "new";
  if (type === 3) return "relearning";
  if (queue === 1 || queue === 3 || type === 1) return "learning";
  return "review";
}

function parseCardData(value: unknown): StoredCardData {
  try {
    const parsed = JSON.parse(String(value || "{}")) as StoredCardData;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function fsrsState(type: number) {
  if (type === 1) return State.Learning;
  if (type === 2) return State.Review;
  if (type === 3) return State.Relearning;
  return State.New;
}

function learningStepsCompleted(state: State, left: number) {
  const remaining = Math.max(0, left % 1000);
  if (state === State.Learning) return Math.max(0, LEARNING_STEPS.length - remaining);
  if (state === State.Relearning) return Math.max(0, RELEARNING_STEPS.length - remaining);
  return 0;
}

function dueDateForCard(row: Record<string, unknown>, database: Database) {
  const queue = Number(row.queue);
  const due = Number(row.due);
  if (queue === 1) return new Date(due * 1000);
  if (queue === 2 || queue === 3) {
    const creation = Number(database.selectValue("SELECT crt FROM col WHERE id = 1") ?? collectionCreationSeconds());
    return new Date((creation + due * 86_400) * 1000);
  }
  return new Date();
}

function toFsrsCard(row: Record<string, unknown>, database: Database, now: Date): CardInput {
  const data = parseCardData(row.data);
  const state = fsrsState(Number(row.type));
  const lastReviewSeconds = Number(data.lrt ?? row.last_review_seconds ?? 0);
  const lastReview = lastReviewSeconds > 0 ? new Date(lastReviewSeconds * 1000) : undefined;
  const interval = Math.max(0, Number(row.ivl));
  const elapsedDays = lastReview
    ? Math.max(0, Math.round((now.getTime() - lastReview.getTime()) / 86_400_000))
    : 0;

  return {
    due: dueDateForCard(row, database),
    stability: state === State.New ? 0 : Math.max(0.1, Number(data.s) || interval || 1),
    difficulty: state === State.New ? 0 : Math.min(10, Math.max(1, Number(data.d) || 5)),
    elapsed_days: elapsedDays,
    scheduled_days: interval,
    learning_steps: learningStepsCompleted(state, Number(row.left)),
    reps: Math.max(0, Number(row.reps)),
    lapses: Math.max(0, Number(row.lapses)),
    state,
    last_review: lastReview
  };
}

function previewForCard(row: Record<string, unknown>, database: Database, now = new Date()) {
  scheduler.seed = `${String(row.id)}:${String(row.reps)}`;
  return scheduler.repeat(toFsrsCard(row, database, now), now);
}

function intervalLabel(due: Date, now: Date) {
  const seconds = Math.max(1, Math.round((due.getTime() - now.getTime()) / 1000));
  if (seconds < 3_600) return `${Math.max(1, Math.round(seconds / 60))}m`;
  if (seconds < 86_400) return `${Math.max(1, Math.round(seconds / 3_600))}h`;

  const days = Math.max(1, Math.round(seconds / 86_400));
  if (days < 30) return `${days}d`;
  if (days < 365) return `${Math.max(1, Math.round(days / 30))}mo`;
  return `${Math.max(1, Math.round(days / 365))}y`;
}

function shiftedDifficulty(difficulty: number) {
  return Math.round((((difficulty - 1) / 9) + 0.1) * 1000);
}

function intervalForRevlog(result: RecordLogItem, now: Date) {
  if (result.card.state === State.Review) return result.card.scheduled_days;
  return -Math.max(1, Math.round((result.card.due.getTime() - now.getTime()) / 1000));
}

function remainingSteps(result: RecordLogItem) {
  if (result.card.state === State.Learning) {
    return Math.max(0, LEARNING_STEPS.length - result.card.learning_steps);
  }
  if (result.card.state === State.Relearning) {
    return Math.max(0, RELEARNING_STEPS.length - result.card.learning_steps);
  }
  return 0;
}

async function getNextCard(deckId: number): Promise<StudyCard | null> {
  await initialize();
  const database = collection();
  const deck = readDecks(database)[String(deckId)];
  if (!deck) throw new Error("Deck not found");

  const row = database.selectObject(
    `SELECT c.id, c.did, c.ord, c.type, c.queue, c.due, c.ivl, c.reps, c.lapses,
       c.left, c.data, n.mid, n.flds,
       (SELECT max(r.id) / 1000 FROM revlog r WHERE r.cid = c.id) AS last_review_seconds
     FROM cards c JOIN notes n ON n.id = c.nid
     WHERE c.did = ? AND (
       (c.queue = 1 AND c.due <= ?) OR
       (c.queue IN (2, 3) AND c.due <= ?) OR
       c.queue = 0
     )
     ORDER BY CASE c.queue WHEN 1 THEN 0 WHEN 3 THEN 0 WHEN 2 THEN 1 ELSE 2 END, c.due, c.id
     LIMIT 1`,
    [deckId, nowSeconds(), collectionDay(database)]
  );
  if (!row) return null;

  const notetype = readNotetypes(database)[String(row.mid)];
  if (!notetype) throw new Error(`Note type ${String(row.mid)} is missing`);
  const rendered = renderAnkiCard(notetype, String(row.flds).split(FIELD_SEPARATOR), Number(row.ord));
  const question = await inlineMedia(rendered.questionHtml, rendered.css);
  const answer = await inlineMedia(rendered.answerHtml, question.css);
  const now = new Date();
  const preview = previewForCard(row, database, now);
  return {
    id: Number(row.id),
    deckId: Number(row.did),
    deckName: deck.name,
    questionHtml: question.html,
    answerHtml: answer.html,
    cardCss: answer.css,
    state: stateForCard(Number(row.type), Number(row.queue)),
    intervalDays: Number(row.ivl),
    answerOptions: ([Rating.Again, Rating.Hard, Rating.Good, Rating.Easy] as const).map((rating) => ({
      rating: rating as ReviewRating,
      intervalLabel: intervalLabel(preview[rating].card.due, now)
    }))
  };
}

async function answerCard(cardId: number, rating: ReviewRating, timeMsInput: number): Promise<void> {
  await initialize();
  const database = collection();
  const card = database.selectObject(
    `SELECT c.id, c.type, c.queue, c.due, c.ivl, c.factor, c.reps, c.lapses,
       c.left, c.data,
       (SELECT max(r.id) / 1000 FROM revlog r WHERE r.cid = c.id) AS last_review_seconds
     FROM cards c WHERE c.id = ?`,
    [cardId]
  );
  if (!card) throw new Error("Card not found");

  const previousType = Number(card.type);
  const previousQueue = Number(card.queue);
  const previousInterval = Number(card.ivl);
  const now = new Date();
  const nowSecs = Math.floor(now.getTime() / 1000);
  const today = collectionDay(database);
  scheduler.seed = `${String(card.id)}:${String(card.reps)}`;
  const result = scheduler.next(toFsrsCard(card, database, now), now, rating as Grade);
  const type = result.card.state;
  const queue = result.card.state === State.Review ? 2 : 1;
  const due = result.card.state === State.Review
    ? today + result.card.scheduled_days
    : Math.floor(result.card.due.getTime() / 1000);
  const interval = result.card.state === State.Review
    ? result.card.scheduled_days
    : previousInterval;
  const factor = Number(card.factor) || 2500;
  const left = remainingSteps(result);
  const loggedInterval = intervalForRevlog(result, now);
  const data: StoredCardData = {
    s: Number(result.card.stability.toFixed(4)),
    d: Number(result.card.difficulty.toFixed(3)),
    dr: REQUEST_RETENTION,
    lrt: nowSecs
  };

  const reviewKind = previousType === 2 ? 1 : previousType === 3 ? 2 : 0;
  const reviewId = uniqueId(database, "revlog");
  const timeMs = Math.max(0, Math.min(60_000, Math.round(timeMsInput)));
  const lastInterval = previousQueue === 1
    ? -Math.max(1, Number(card.due) - Number(parseCardData(card.data).lrt ?? card.last_review_seconds ?? nowSecs))
    : previousInterval;

  database.transaction("IMMEDIATE", (transaction) => {
    transaction.exec({
      sql: `UPDATE cards SET
        mod = ?, usn = -1, type = ?, queue = ?, due = ?, ivl = ?, factor = ?,
        reps = ?, lapses = ?, left = ?, data = ? WHERE id = ?`,
      bind: [
        nowSecs,
        type,
        queue,
        due,
        interval,
        factor,
        result.card.reps,
        result.card.lapses,
        left,
        JSON.stringify(data),
        cardId
      ]
    });
    transaction.exec({
      sql: `INSERT INTO revlog
        (id, cid, usn, ease, ivl, lastIvl, factor, time, type)
        VALUES (?, ?, -1, ?, ?, ?, ?, ?, ?)`,
      bind: [
        reviewId,
        cardId,
        rating,
        loggedInterval,
        lastInterval,
        shiftedDifficulty(result.card.difficulty),
        timeMs,
        reviewKind
      ]
    });
    touchCollection(transaction);
  });
}

async function handleRequest(request: DbRequest) {
  try {
    let result: unknown;

    switch (request.type) {
      case "init":
        result = await initialize();
        break;
      case "listDecks":
        result = await listDecks();
        break;
      case "listNotetypes":
        result = await listNotetypes();
        break;
      case "createDeck":
        result = await createDeck(request.name);
        break;
      case "addBasicNote":
        result = await addBasicNote(request.deckId, request.front, request.back);
        break;
      case "addNote":
        result = await addNoteForNotetype(request.deckId, request.notetypeId, request.fields);
        break;
      case "addClozeNote":
        result = await addClozeNote(request.deckId, request.text, request.extra);
        break;
      case "storeMedia":
        result = await storeMedia(request.filename, request.bytes);
        break;
      case "importApkg": {
        const info = await initialize();
        result = await importApkg(sqliteRuntime!, collection(), new Uint8Array(request.bytes),
          await importMediaStore(info.persistent), request.keepScheduling, (progress) => {
            workerScope.postMessage({ id: request.id, ok: true, progress } satisfies DbResponse);
          });
        break;
      }
      case "getNextCard":
        result = await getNextCard(request.deckId);
        break;
      case "answerCard":
        result = await answerCard(request.cardId, request.rating, request.timeMs);
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
}

// An import awaits media I/O. Serialize RPCs so other operations cannot change
// IDs, filenames, or collection metadata between import planning and commit.
let requestQueue = Promise.resolve();
workerScope.addEventListener("message", (event: MessageEvent<DbRequest>) => {
  requestQueue = requestQueue.then(() => handleRequest(event.data));
});
