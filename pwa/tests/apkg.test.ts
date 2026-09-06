import assert from "node:assert/strict";
import { before, test } from "node:test";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { zstdCompressSync } from "node:zlib";
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";
import type { Database, Sqlite3Static } from "@sqlite.org/sqlite-wasm";
import { strToU8, unzipSync, zipSync } from "fflate";

import { readApkg } from "../src/lib/anki/apkg";
import { importApkg, rewriteMediaReferences } from "../src/lib/anki/import-apkg";
import type { ImportMediaStore } from "../src/lib/anki/import-apkg";
import { Proto } from "../src/lib/anki/protobuf";
import { renderAnkiCard } from "../src/lib/anki/template";

let sqlite: Sqlite3Static;
before(async () => { sqlite = await sqlite3InitModule(); });
const schema = readFileSync(new URL("../../rslib/src/storage/schema11.sql", import.meta.url), "utf8");
const model = { id: 100, name: "Custom basic", type: 0, sortf: 0,
  flds: [{ name: "Front", ord: 0 }, { name: "Back", ord: 1 }],
  tmpls: [{ name: "Card 1", ord: 0, qfmt: "{{Front}}", afmt: "{{FrontSide}}<hr>{{Back}}" }],
  css: '.card { color: red; background-image: url("image.png"); }' };
const deck = { id: 200, name: "Languages::Spanish", dyn: 0, conf: 1 };
const creation = Math.floor(Date.now() / 1000) - 100 * 86_400;

function database() {
  const db = new sqlite.oo1.DB(":memory:", "c");
  db.exec(schema);
  db.exec({ sql: "UPDATE col SET crt = ?, ver = 11, models = ?, decks = ?",
    bind: [creation, JSON.stringify({ 100: model }), JSON.stringify({ 200: deck })] });
  return db;
}

function addNote(db: Database, guid = "note-guid", fields = 'Hola\u001f<img src="image.png">[sound:voice.mp3]') {
  db.exec({ sql: "INSERT INTO notes VALUES (10, ?, 100, 1, -1, ' spanish ', ?, 'Hola', 1, 0, '')", bind: [guid, fields] });
  db.exec("INSERT INTO cards VALUES (20, 10, 200, 0, 1, -1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, '')");
}

function exportDb(db: Database) { return sqlite.capi.sqlite3_js_db_export(db.pointer!); }

function legacy(db: Database, media: Record<string, Uint8Array> = {}) {
  const entries: Record<string, Uint8Array> = { "collection.anki21": exportDb(db) };
  const manifest: Record<string, string> = {};
  Object.entries(media).forEach(([name, bytes], index) => { manifest[String(index)] = name; entries[String(index)] = bytes; });
  entries.media = strToU8(JSON.stringify(manifest));
  return zipSync(entries);
}

function memoryStore(initial: Record<string, Uint8Array> = {}): ImportMediaStore & { files: Map<string, Uint8Array> } {
  const files = new Map(Object.entries(initial));
  return { files, read: async (name) => files.get(name),
    write: async (name, bytes) => { files.set(name, bytes.slice()); }, remove: async (name) => { files.delete(name); } };
}

function snapshot(db: Database) {
  return JSON.stringify(["col", "notes", "cards", "revlog"].map((table) => db.selectObjects(`SELECT * FROM ${table} ORDER BY id`)));
}

function variable(value: number) {
  const bytes = [];
  do { const next = value % 128; value = Math.floor(value / 128); bytes.push(next | (value ? 128 : 0)); } while (value);
  return Uint8Array.from(bytes);
}
function concat(...arrays: Uint8Array[]) { return new Uint8Array(Buffer.concat(arrays)); }
function uint(field: number, value: number) { return concat(variable(field * 8), variable(value)); }
function binary(field: number, value: Uint8Array) { return concat(variable(field * 8 + 2), variable(value.length), value); }
function string(field: number, value: string) { return binary(field, strToU8(value)); }

function modern(db: Database, mediaName = "image.png", content = strToU8("modern media")) {
  // Exact table shapes/config field numbers from schema15 and proto/anki/.
  const compare = sqlite.wasm.installFunction((_context: number, lengthA: number, pointerA: number, lengthB: number, pointerB: number) => {
    const heap = sqlite.wasm.heap8u();
    return new TextDecoder().decode(heap.subarray(pointerA, pointerA + lengthA)).toLowerCase()
      .localeCompare(new TextDecoder().decode(heap.subarray(pointerB, pointerB + lengthB)).toLowerCase());
  }, "ipipip");
  assert.equal(sqlite.capi.sqlite3_create_collation_v2(db.pointer!, "unicase", sqlite.capi.SQLITE_UTF8, 0, compare, 0), 0);
  db.exec(`
    UPDATE col SET ver = 18, models = '', decks = '';
    CREATE TABLE notetypes (id INTEGER PRIMARY KEY, name TEXT COLLATE unicase, mtime_secs INTEGER, usn INTEGER, config BLOB);
    CREATE UNIQUE INDEX idx_notetypes_name ON notetypes(name);
    CREATE TABLE fields (ntid INTEGER, ord INTEGER, name TEXT COLLATE unicase, config BLOB, PRIMARY KEY(ntid, ord)) WITHOUT ROWID;
    CREATE TABLE templates (ntid INTEGER, ord INTEGER, name TEXT COLLATE unicase, mtime_secs INTEGER, usn INTEGER, config BLOB, PRIMARY KEY(ntid, ord)) WITHOUT ROWID;
    CREATE TABLE decks (id INTEGER PRIMARY KEY, name TEXT COLLATE unicase, mtime_secs INTEGER, usn INTEGER, common BLOB, kind BLOB);
    CREATE UNIQUE INDEX idx_decks_name ON decks(name);
  `);
  const requirement = concat(uint(1, 0), uint(2, 1), binary(3, variable(0)));
  db.exec({ sql: "INSERT INTO notetypes VALUES (100, 'Modern basic', 1, -1, ?)",
    bind: [concat(string(3, model.css), binary(8, requirement))] });
  for (const [index, name] of ["Front", "Back"].entries()) {
    db.exec({ sql: "INSERT INTO fields VALUES (100, ?, ?, ?)", bind: [index, name, concat(string(3, "Arial"), uint(4, 20))] });
  }
  db.exec({ sql: "INSERT INTO templates VALUES (100, 0, 'Card 1', 1, -1, ?)",
    bind: [concat(string(1, model.tmpls[0].qfmt), string(2, model.tmpls[0].afmt))] });
  db.exec({ sql: "INSERT INTO decks VALUES (200, ?, 1, -1, ?, ?)",
    bind: ["Languages\u001fSpanish", uint(1, 1), binary(1, concat(uint(1, 1), string(4, "Imported description")))] });
  const manifest = binary(1, concat(string(1, mediaName), uint(2, content.length), binary(3, createHash("sha1").update(content).digest())));
  const bytes = zipSync({ meta: uint(1, 3), "collection.anki21b": zstdCompressSync(exportDb(db)),
    "collection.anki2": strToU8("Do not import this compatibility placeholder"),
    media: zstdCompressSync(manifest), "0": zstdCompressSync(content) }, { level: 0 });
  sqlite.capi.sqlite3_create_collation_v2(db.pointer!, "unicase", sqlite.capi.SQLITE_UTF8, 0, 0, 0);
  sqlite.wasm.uninstallFunction(compare);
  return bytes;
}

test("imports the repository's real legacy fixtures", async () => {
  for (const name of ["media", "update1", "update2", "diffmodels2-1", "diffmodels2-2", "diffmodeltemplates-1", "diffmodeltemplates-2"]) {
    const bytes = readFileSync(new URL(`../../pylib/tests/support/${name}.apkg`, import.meta.url));
    const target = database();
    try {
      const result = await importApkg(sqlite, target, bytes, memoryStore(), true);
      assert.ok(result.notes > 0, name);
      assert.ok(result.cards > 0, name);
      assert.equal(target.selectValue("SELECT count(*) FROM notes"), result.notes);
    } finally { target.close(); }
  }
});

test("imports fields, templates, tags and media, resets only imported cards, and skips duplicates", async () => {
  const source = database(); const target = database(); const store = memoryStore();
  try {
    addNote(source);
    source.exec("UPDATE cards SET type = 2, queue = 2, reps = 5, due = 120, ivl = 20");
    source.exec("INSERT INTO revlog VALUES (1000, 20, -1, 3, 20, 10, 2500, 5000, 1)");
    const bytes = legacy(source, { "image.png": strToU8("image"), "voice.mp3": strToU8("voice") });
    const result = await importApkg(sqlite, target, bytes, store, false);
    assert.equal(result.notes, 1); assert.equal(result.cards, 1); assert.equal(result.media, 2);
    assert.equal(target.selectValue("SELECT tags FROM notes"), " spanish ");
    assert.equal(target.selectValue("SELECT type FROM cards"), 0);
    assert.equal(target.selectValue("SELECT reps FROM cards"), 0);
    assert.equal(target.selectValue("SELECT count(*) FROM revlog"), 0);
    const before = snapshot(target);
    const duplicate = await importApkg(sqlite, target, bytes, store, true);
    assert.equal(duplicate.skippedNotes, 1); assert.equal(duplicate.cards, 0); assert.equal(duplicate.media, 0);
    assert.equal(snapshot(target), before);
  } finally { source.close(); target.close(); }
});

test("preserves existing notes/cards/models and remaps colliding IDs and media references", async () => {
  const source = database(); const target = database();
  const store = memoryStore({ "image.png": strToU8("existing image") });
  try {
    addNote(source); addNote(target, "existing-guid", "Existing\u001fDo not edit");
    target.exec("UPDATE cards SET type = 2, queue = 2, reps = 99, due = 800");
    const existingNote = target.selectObject("SELECT * FROM notes WHERE id = 10");
    const existingCard = target.selectObject("SELECT * FROM cards WHERE id = 20");
    const existingModel = String(target.selectValue("SELECT models FROM col"));
    const bytes = legacy(source, { "image.png": strToU8("imported image") });
    await importApkg(sqlite, target, bytes, store, true);
    assert.deepEqual(target.selectObject("SELECT * FROM notes WHERE id = 10"), existingNote);
    assert.deepEqual(target.selectObject("SELECT * FROM cards WHERE id = 20"), existingCard);
    const models = JSON.parse(String(target.selectValue("SELECT models FROM col")));
    assert.deepEqual(models["100"], JSON.parse(existingModel)["100"]);
    const note = target.selectObject("SELECT * FROM notes WHERE guid = 'note-guid'")!;
    assert.notEqual(note.id, 10); assert.notEqual(note.mid, 100);
    assert.match(String(note.flds), /image-[a-f\d]+\.png/);
    assert.match(models[String(note.mid)].css, /image-[a-f\d]+\.png/);
    assert.deepEqual(store.files.get("image.png"), strToU8("existing image"));
    assert.equal(store.files.size, 2);
  } finally { source.close(); target.close(); }
});

test("reads modern Zstd packages, protobuf models/media and hierarchical decks", async () => {
  const source = database(); const target = database(); const store = memoryStore();
  try {
    addNote(source);
    target.exec("UPDATE col SET decks = '{}', models = '{}'");
    const bytes = modern(source);
    const parsed = readApkg(sqlite, bytes, true);
    assert.equal(parsed.models[0].name, "Modern basic");
    assert.deepEqual(parsed.models[0].req, [[0, "any", [0]]]);
    assert.equal(parsed.models[0].flds[0].font, "Arial");
    assert.equal(parsed.decks[0].name, "Languages::Spanish");
    await importApkg(sqlite, target, bytes, store, true);
    assert.equal(target.selectValue("SELECT count(*) FROM cards"), 1);
    const decks = JSON.parse(String(target.selectValue("SELECT decks FROM col")));
    assert.deepEqual(Object.values(decks as Record<string, { name: string }>).map((deck) => deck.name).sort(), ["Languages", "Languages::Spanish"]);
    const models = JSON.parse(String(target.selectValue("SELECT models FROM col")));
    const note = target.selectObject("SELECT * FROM notes")!;
    const rendered = renderAnkiCard(models[String(note.mid)], String(note.flds).split("\u001f"), 0);
    assert.equal(rendered.questionHtml, "Hola");
    assert.match(rendered.answerHtml, /image\.png/);
    assert.deepEqual(store.files.get("image.png"), strToU8("modern media"));
  } finally { source.close(); target.close(); }
});

test("keeps imported scheduling and history with due dates shifted to the destination epoch", async () => {
  const source = database(); const target = database();
  try {
    addNote(source);
    source.exec("UPDATE cards SET type = 2, queue = 2, due = 120, ivl = 20, reps = 4, data = '{\"s\":20,\"d\":5}'");
    source.exec("INSERT INTO revlog VALUES (1000, 20, -1, 3, 20, 10, 2500, 5000, 1)");
    target.exec({ sql: "UPDATE col SET crt = ?", bind: [creation + 90 * 86_400] });
    await importApkg(sqlite, target, legacy(source), memoryStore(), true);
    const card = target.selectObject("SELECT * FROM cards")!;
    assert.equal(card.due, 30); assert.equal(card.ivl, 20); assert.equal(card.reps, 4);
    assert.equal(card.data, '{"s":20,"d":5}');
    assert.equal(target.selectValue("SELECT cid FROM revlog"), card.id);
    assert.equal(target.selectValue("SELECT id FROM revlog"), 1000);
  } finally { source.close(); target.close(); }
});

test("shifts day-learning due dates, but not timestamp-learning due dates", async () => {
  for (const [queue, due, expected] of [[3, 103, 13], [1, 1_800_000_000, 1_800_000_000], [-1, 103, 13]]) {
    const source = database(); const target = database();
    try {
      addNote(source);
      source.exec({ sql: "UPDATE cards SET type = 1, queue = ?, due = ?", bind: [queue, due] });
      target.exec({ sql: "UPDATE col SET crt = ?", bind: [creation + 90 * 86_400] });
      await importApkg(sqlite, target, legacy(source), memoryStore(), true);
      assert.equal(target.selectValue("SELECT due FROM cards"), expected);
      assert.equal(target.selectValue("SELECT queue FROM cards"), queue);
    } finally { source.close(); target.close(); }
  }
});

test("returns filtered cards to their original deck while keeping suspended cards suspended", async () => {
  const source = database(); const target = database();
  try {
    addNote(source);
    source.exec("UPDATE cards SET type = 2, queue = -1, did = 999, odid = 200, due = -10, odue = 120, ivl = 20");
    await importApkg(sqlite, target, legacy(source), memoryStore(), true);
    const card = target.selectObject("SELECT * FROM cards")!;
    assert.equal(card.did, 200); assert.equal(card.odid, 0); assert.equal(card.odue, 0);
    assert.equal(card.queue, -1); assert.equal(card.due, 120);
  } finally { source.close(); target.close(); }
});

test("a changed package cannot overwrite an already imported note or its reviews", async () => {
  const source = database(); const target = database(); const store = memoryStore();
  try {
    addNote(source);
    await importApkg(sqlite, target, legacy(source), store, true);
    target.exec("UPDATE cards SET reps = 10, due = 100, queue = 2, type = 2");
    target.exec("INSERT INTO revlog VALUES (1000, 20, -1, 3, 20, 10, 2500, 5000, 1)");
    source.exec("UPDATE notes SET flds = 'Changed answer' || char(31) || 'Changed front', mod = 999999999");
    const before = snapshot(target);
    const result = await importApkg(sqlite, target, legacy(source), store, false);
    assert.equal(result.skippedNotes, 1); assert.equal(snapshot(target), before);
  } finally { source.close(); target.close(); }
});

test("imports cloze card ordinals and renders their deletions", async () => {
  const source = database(); const target = database();
  try {
    const cloze = { ...model, type: 1, name: "Imported cloze",
      tmpls: [{ name: "Cloze", ord: 0, qfmt: "{{cloze:Front}}", afmt: "{{cloze:Front}}<hr>{{Back}}" }] };
    source.exec({ sql: "UPDATE col SET models = ?", bind: [JSON.stringify({ 100: cloze })] });
    addNote(source, "cloze-guid", "{{c1::Paris}} and {{c2::Rome}}\u001fExtra");
    source.exec("INSERT INTO cards SELECT 21, nid, did, 1, mod, usn, type, queue, 2, ivl, factor, reps, lapses, left, odue, odid, flags, data FROM cards WHERE id = 20");
    const result = await importApkg(sqlite, target, legacy(source), memoryStore(), true);
    assert.equal(result.cards, 2);
    const note = target.selectObject("SELECT * FROM notes")!;
    const models = JSON.parse(String(target.selectValue("SELECT models FROM col")));
    const rendered = renderAnkiCard(models[String(note.mid)], String(note.flds).split("\u001f"), 1);
    assert.match(rendered.questionHtml, /Paris/); assert.doesNotMatch(rendered.questionHtml, /Rome/);
    assert.match(rendered.answerHtml, /Rome/);
  } finally { source.close(); target.close(); }
});

test("missing media, unknown versions and invalid databases leave the collection unchanged", async () => {
  const source = database(); const target = database(); const store = memoryStore();
  try {
    addNote(source);
    const zip = unzipSync(legacy(source));
    const before = snapshot(target);
    for (const broken of [
      strToU8("not a zip"),
      zipSync({ ...zip, meta: uint(1, 99) }),
      zipSync({ ...zip, media: strToU8('{"0":"missing.png"}') }),
      zipSync({ ...zip, "collection.anki21": strToU8("invalid database") }),
      zipSync({ ...zip, media: strToU8('{"0":"../escape.png"}'), "0": strToU8("bad") })
    ]) {
      await assert.rejects(importApkg(sqlite, target, broken, store, true));
      assert.equal(snapshot(target), before); assert.equal(store.files.size, 0);
    }
  } finally { source.close(); target.close(); }
});

test("verifies modern media checksums before writing anything", async () => {
  const source = database(); const target = database(); const store = memoryStore();
  try {
    addNote(source);
    const zip = unzipSync(modern(source));
    zip["0"] = zstdCompressSync(strToU8("broken media"));
    const before = snapshot(target);
    await assert.rejects(importApkg(sqlite, target, zipSync(zip), store, true), /Corrupt media/);
    assert.equal(snapshot(target), before); assert.equal(store.files.size, 0);
  } finally { source.close(); target.close(); }
});

test("media write errors clean up new files without deleting existing files", async () => {
  const source = database(); const target = database(); const store = memoryStore({ "existing.png": strToU8("safe") });
  try {
    addNote(source);
    const write = store.write;
    store.write = async (name, bytes) => { await write(name, bytes); if (name === "voice.mp3") throw new Error("Quota exceeded"); };
    const before = snapshot(target);
    await assert.rejects(importApkg(sqlite, target, legacy(source, { "image.png": strToU8("image"), "voice.mp3": strToU8("voice") }), store, true), /Quota/);
    assert.equal(snapshot(target), before);
    assert.deepEqual([...store.files.keys()], ["existing.png"]);
  } finally { source.close(); target.close(); }
});

test("database errors roll back all notes, cards and newly saved media", async () => {
  const source = database(); const target = database(); const store = memoryStore();
  try {
    addNote(source);
    target.exec("CREATE TRIGGER reject_import BEFORE INSERT ON cards BEGIN SELECT RAISE(ABORT, 'test failure'); END");
    const before = snapshot(target);
    await assert.rejects(importApkg(sqlite, target, legacy(source, { "image.png": strToU8("image") }), store, true), /test failure/);
    assert.equal(snapshot(target), before); assert.equal(store.files.size, 0);
  } finally { source.close(); target.close(); }
});

test("rewrites quoted/unquoted, URI-encoded, audio and CSS references without changing prose or remote URLs", () => {
  const resolve = (name: string) => name === "a b.png" ? "safe.png" : undefined;
  assert.equal(rewriteMediaReferences('a b.png <img src="a%20b.png"><img src=\'a b.png\'>[sound:a b.png] url(a%20b.png) <img src=https://example.com/a%20b.png>', resolve),
    'a b.png <img src="safe.png"><img src="safe.png">[sound:safe.png] url(safe.png) <img src=https://example.com/a%20b.png>');
  assert.equal(rewriteMediaReferences('<div style="background:url(\'a b.png\')"></div>', resolve),
    '<div style="background:url(safe.png)"></div>');
});

test("protobuf parser rejects truncated data and reads packed field ordinals", () => {
  assert.throws(() => new Proto(Uint8Array.of(10, 255)), /truncated/i);
  assert.throws(() => new Proto(Uint8Array.of(0)), /field/i);
  assert.deepEqual(new Proto(binary(3, concat(variable(0), variable(127), variable(128)))).numbers(3), [0, 127, 128]);
});
