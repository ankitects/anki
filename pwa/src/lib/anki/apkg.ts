import type { Database, Sqlite3Static, SqlValue } from "@sqlite.org/sqlite-wasm";
import { unzipSync } from "fflate";
import { Decompress } from "fzstd";

import { Proto } from "./protobuf";
import type { AnkiNotetype } from "./template";

export const MAX_APKG_BYTES = 128 * 1024 * 1024;
const MAX_EXPANDED_BYTES = 512 * 1024 * 1024;
const MAX_MEDIA_BYTES = 64 * 1024 * 1024;
const MAX_FILES = 50_000;
type Row = Record<string, SqlValue>;
export type ImportedDeck = { id: number; name: string; dyn?: number; [key: string]: unknown };
export type PackageMedia = { name: string; bytes: Uint8Array; sha1?: Uint8Array };
export type PackageData = {
  creation: number;
  models: AnkiNotetype[];
  decks: ImportedDeck[];
  notes: Row[];
  cards: Row[];
  reviews: Row[];
  media: PackageMedia[];
};

function decompress(bytes: Uint8Array, limit: number) {
  const chunks: Uint8Array[] = [];
  let size = 0;
  const stream = new Decompress((chunk) => {
    size += chunk.length;
    if (size > limit) throw new Error("Uncompressed package data exceeds the browser import limit");
    chunks.push(chunk.slice());
  });
  stream.push(bytes, true);
  const output = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { output.set(chunk, offset); offset += chunk.length; }
  return output;
}

function json(bytes: Uint8Array): unknown {
  return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
}

function dictionary(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid package JSON metadata");
  return value as Record<string, unknown>;
}

function config(row: Row, column = "config") {
  const bytes = row[column];
  if (!(bytes instanceof Uint8Array)) throw new Error("Invalid Anki configuration data");
  return new Proto(bytes);
}

function modernModels(db: Database): AnkiNotetype[] {
  const fields = db.selectObjects("SELECT ntid, ord, name, config FROM fields NOT INDEXED ORDER BY ntid, ord");
  const templates = db.selectObjects("SELECT ntid, ord, name, config FROM templates NOT INDEXED ORDER BY ntid, ord");
  const group = (rows: Row[]) => {
    const grouped = new Map<number, Row[]>();
    for (const row of rows) {
      const id = Number(row.ntid);
      const entries = grouped.get(id) ?? [];
      entries.push(row);
      grouped.set(id, entries);
    }
    return grouped;
  };
  const groupedFields = group(fields);
  const groupedTemplates = group(templates);
  return db.selectObjects("SELECT id, name, config FROM notetypes NOT INDEXED").map((row) => {
    const c = config(row);
    return {
      id: Number(row.id), name: String(row.name), type: c.number(1), sortf: c.number(2), css: c.text(3),
      latexPre: c.text(5), latexPost: c.text(6), latexsvg: Boolean(c.number(7)),
      flds: (groupedFields.get(Number(row.id)) ?? []).map((field) => {
        const f = config(field);
        return { name: String(field.name), ord: Number(field.ord), sticky: Boolean(f.number(1)),
          rtl: Boolean(f.number(2)), font: f.text(3), size: f.number(4), description: f.text(5) };
      }),
      tmpls: (groupedTemplates.get(Number(row.id)) ?? []).map((template) => {
        const t = config(template);
        return { name: String(template.name), ord: Number(template.ord), qfmt: t.text(1), afmt: t.text(2),
          bqfmt: t.text(3), bafmt: t.text(4), did: t.number(5) || null, bfont: t.text(6), bsize: t.number(7) };
      }),
      req: c.messages(8).map((r) => [r.number(1), (["none", "any", "all"] as const)[r.number(2)], r.numbers(3)])
    };
  });
}

function modernDecks(db: Database): ImportedDeck[] {
  return db.selectObjects("SELECT id, name, common, kind FROM decks NOT INDEXED").map((row) => {
    const kind = config(row, "kind");
    const normal = kind.messages(1)[0];
    const common = config(row, "common");
    return { id: Number(row.id), name: String(row.name).replaceAll("\u001f", "::"), dyn: kind.has(2) ? 1 : 0,
      desc: normal?.text(4) ?? "", md: Boolean(normal?.number(5)),
      collapsed: Boolean(common.number(1)), browserCollapsed: Boolean(common.number(2)) };
  });
}

function assertTable(db: Database, name: string) {
  if (db.selectValue("SELECT type FROM sqlite_schema WHERE name = ?", [name]) !== "table") {
    throw new Error(`Invalid Anki database: missing ${name} table`);
  }
}

function readDatabase(sqlite: Sqlite3Static, bytes: Uint8Array, keepScheduling: boolean): Omit<PackageData, "media"> {
  if (new TextDecoder().decode(bytes.subarray(0, 16)) !== "SQLite format 3\0") {
    throw new Error("The package does not contain a valid Anki SQLite database");
  }
  const source = new sqlite.oo1.DB(":memory:", "c");
  let pointer = 0;
  try {
    pointer = sqlite.wasm.allocFromTypedArray(bytes);
    // Own the buffer ourselves: READONLY, without FREEONCLOSE or RESIZEABLE.
    const result = sqlite.capi.sqlite3_deserialize(source.pointer!, "main", pointer, bytes.length, bytes.length, 4);
    if (result !== 0) throw new Error("Could not open the packaged Anki database");
    // Modern WITHOUT ROWID tables require this declared collation even for
    // scans. We never use its name indexes or write through it: imports only
    // scan rows and order by numeric IDs/ordinals, avoiding case-folding drift.
    const decoder = new TextDecoder();
    const collation = sqlite.capi.sqlite3_create_collation_v2(source.pointer!, "unicase", sqlite.capi.SQLITE_UTF8, 0,
      (_context, lengthA, pointerA, lengthB, pointerB) => {
        const heap = sqlite.wasm.heap8u();
        const a = decoder.decode(heap.subarray(pointerA, pointerA + lengthA)).toLowerCase();
        const b = decoder.decode(heap.subarray(pointerB, pointerB + lengthB)).toLowerCase();
        return a < b ? -1 : a > b ? 1 : 0;
      }, 0);
    if (collation !== 0) throw new Error("Could not read Anki's database collation");
    source.exec("PRAGMA trusted_schema = OFF; PRAGMA query_only = ON");
    for (const table of ["col", "notes", "cards", "revlog"]) assertTable(source, table);
    const col = source.selectObject("SELECT crt, ver, models, decks FROM col");
    if (!col || source.selectValue("SELECT count(*) FROM col") !== 1 || ![11, 14, 15, 16, 17, 18].includes(Number(col.ver))) {
      throw new Error("This Anki database version is not supported. Export a compatible .apkg from Anki.");
    }
    const modern = Number(col.ver) >= 15;
    if (modern) for (const table of ["notetypes", "fields", "templates", "decks"]) assertTable(source, table);
    const models = modern ? modernModels(source) : Object.values(dictionary(JSON.parse(String(col.models)))) as AnkiNotetype[];
    const decks = modern ? modernDecks(source) : Object.values(dictionary(JSON.parse(String(col.decks)))) as ImportedDeck[];
    for (const model of models) {
      // Older Anki exports serialized metadata IDs as decimal strings.
      model.id = Number(model.id);
      if (!Number.isSafeInteger(model.id) || model.id <= 0 || typeof model.name !== "string" || ![0, 1].includes(model.type)
        || !Array.isArray(model.flds) || !model.flds.length || !Array.isArray(model.tmpls) || !model.tmpls.length
        || typeof model.css !== "string"
        || model.flds.some((field, index) => typeof field.name !== "string" || (field.ord ?? index) !== index)
        || model.tmpls.some((template, index) => typeof template.qfmt !== "string" || typeof template.afmt !== "string"
          || (template.ord ?? index) !== index)) throw new Error("Invalid or unsupported note type in this package");
      if (model.req && (!Array.isArray(model.req) || model.req.some((requirement) => !Array.isArray(requirement)
        || !Number.isSafeInteger(requirement[0]) || !["none", "any", "all"].includes(requirement[1])
        || !Array.isArray(requirement[2]) || requirement[2].some((ordinal) => !Number.isSafeInteger(ordinal) || ordinal < 0 || ordinal >= model.flds.length)))) {
        throw new Error("Invalid card-generation requirements in package");
      }
    }
    for (const deck of decks) deck.id = Number(deck.id);
    if (decks.some((deck) => !Number.isSafeInteger(deck.id) || deck.id <= 0 || typeof deck.name !== "string"
      || deck.name.split("::").some((part) => !part.trim()))) {
      throw new Error("Invalid deck metadata in this package");
    }
    const notes = source.selectObjects("SELECT id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data FROM notes NOT INDEXED");
    const cards = source.selectObjects("SELECT id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data FROM cards NOT INDEXED");
    const reviews = keepScheduling
      ? source.selectObjects("SELECT id, cid, usn, ease, ivl, lastIvl, factor, time, type FROM revlog NOT INDEXED") : [];
    const creation = Number(col.crt);
    if (!Number.isSafeInteger(creation)) throw new Error("Invalid collection creation time");
    return { creation, models, decks, notes, cards, reviews };
  } finally {
    source.close();
    if (pointer) sqlite.wasm.dealloc(pointer);
  }
}

export function readApkg(sqlite: Sqlite3Static, bytes: Uint8Array, keepScheduling: boolean): PackageData {
  if (!bytes.length || bytes.length > MAX_APKG_BYTES) throw new Error("Choose an .apkg file no larger than 128 MiB");
  let expanded = 0;
  let count = 0;
  const names = new Set<string>();
  const zip = unzipSync(bytes, { filter: (file) => {
    expanded += file.originalSize;
    if (++count > MAX_FILES || expanded > MAX_EXPANDED_BYTES) throw new Error("This package is too large for browser import");
    if (names.has(file.name)) throw new Error("The package has duplicate ZIP entries");
    names.add(file.name);
    return /^(?:meta|media|collection\.anki(?:2|21|21b)|\d+)$/.test(file.name);
  } });
  const version = zip.meta ? new Proto(zip.meta).number(1) : zip["collection.anki21"] ? 2 : 1;
  if (![1, 2, 3].includes(version)) throw new Error("Unsupported Anki package version. Please export a compatible .apkg.");
  const databaseName = version === 3 ? "collection.anki21b" : version === 2 ? "collection.anki21" : "collection.anki2";
  const stored = zip[databaseName];
  if (!stored || !zip.media) throw new Error("Invalid .apkg: the collection or media manifest is missing");
  const databaseBytes = version === 3 ? decompress(stored, MAX_APKG_BYTES) : stored;
  if (databaseBytes.length > MAX_APKG_BYTES) throw new Error("The uncompressed collection exceeds 128 MiB");
  const manifest = version === 3 ? decompress(zip.media, 16 * 1024 * 1024) : zip.media;
  const entries = version === 3
    ? new Proto(manifest).messages(1).map((entry, index) => ({
      name: entry.text(1), key: String(entry.number(255, index)), size: entry.number(2), sha1: entry.bytes(3)
    }))
    : Object.entries(dictionary(json(manifest))).map(([key, name]) => ({ name, key, size: undefined, sha1: undefined }));
  if (entries.length > MAX_FILES) throw new Error("This package contains too many media files");
  const mediaNames = new Set<string>();
  let decodedSize = databaseBytes.length + manifest.length;
  const media = entries.map((entry): PackageMedia => {
    if (typeof entry.name !== "string" || !entry.name || entry.name === "." || entry.name === ".."
      || /[\\/\u0000-\u001f\u007f]/.test(entry.name) || !/^\d+$/.test(entry.key)) throw new Error("Invalid media filename in package");
    if (mediaNames.has(entry.name.normalize("NFC"))) throw new Error("Duplicate media filename in package");
    mediaNames.add(entry.name.normalize("NFC"));
    const storedMedia = zip[entry.key];
    if (!storedMedia) throw new Error(`Missing packaged media: ${entry.name}`);
    const content = version === 3 ? decompress(storedMedia, Math.min(MAX_MEDIA_BYTES, MAX_EXPANDED_BYTES - decodedSize)) : storedMedia;
    decodedSize += content.length;
    if (content.length > MAX_MEDIA_BYTES || decodedSize > MAX_EXPANDED_BYTES) throw new Error("Uncompressed media exceeds the browser import limit");
    if (version === 3 && (content.length !== entry.size || entry.sha1?.length !== 20)) throw new Error(`Invalid packaged media: ${entry.name}`);
    return { name: entry.name, bytes: content, sha1: entry.sha1 };
  });
  return { ...readDatabase(sqlite, databaseBytes, keepScheduling), media };
}
