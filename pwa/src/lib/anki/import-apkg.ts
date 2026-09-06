import type { Database, Sqlite3Static } from "@sqlite.org/sqlite-wasm";

import { readApkg } from "./apkg";
import type { ImportedDeck, PackageData } from "./apkg";
import type { AnkiNotetype } from "./template";
import type { ApkgImportResult } from "../db/types";

export interface ImportMediaStore {
  read(name: string): Promise<Uint8Array | undefined>;
  write(name: string, bytes: Uint8Array): Promise<void>;
  remove(name: string): Promise<void>;
}

function sameBytes(a: Uint8Array, b: Uint8Array) {
  return a.length === b.length && a.every((byte, index) => byte === b[index]);
}

async function digest(bytes: Uint8Array, algorithm = "SHA-1") {
  return new Uint8Array(await crypto.subtle.digest(algorithm, new Uint8Array(bytes)));
}

function decodedName(value: string) {
  const unescaped = value.replaceAll("&amp;", "&").replaceAll("&quot;", '"').replaceAll("&#39;", "'");
  try { return decodeURIComponent(unescaped).normalize("NFC"); }
  catch { return unescaped.normalize("NFC"); }
}

// Apply only to actual media references, never ordinary text containing a filename.
export function rewriteMediaReferences(text: string, resolve: (name: string) => string | undefined): string {
  const lookup = (name: string) => /^(?:[a-z][a-z\d+.-]*:|\/|#)/i.test(name) ? undefined
    : resolve(name.normalize("NFC")) ?? resolve(decodedName(name));
  const url = (name: string) => encodeURIComponent(name).replace(/[!'()*]/g, (character) => `%${character.charCodeAt(0).toString(16)}`);
  return text
    .replace(/\[sound:([^\]]+)]/gi, (match, name: string) => {
      const replacement = lookup(name);
      return replacement ? `[sound:${replacement}]` : match;
    })
    .replace(/\b(src|poster)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/gi,
      (match, attribute: string, double: string | undefined, single: string | undefined, bare: string | undefined) => {
        const replacement = lookup(double ?? single ?? bare ?? "");
        return replacement ? `${attribute}="${url(replacement)}"` : match;
      })
    .replace(/url\(\s*(?:"([^"]*)"|'([^']*)'|([^)'"\s]+))\s*\)/gi,
      (match, double: string | undefined, single: string | undefined, bare: string | undefined) => {
        const replacement = lookup(double ?? single ?? bare ?? "");
        // Unquoted, escaped URLs also work inside either kind of HTML style attribute.
        return replacement ? `url(${url(replacement)})` : match;
      });
}

async function planMedia(media: PackageData["media"], store: ImportMediaStore) {
  const names = new Map<string, string>();
  const writes = new Map<string, Uint8Array>();
  for (const file of media) {
    if (file.sha1 && !sameBytes(await digest(file.bytes), file.sha1)) throw new Error(`Corrupt media file: ${file.name}`);
    const safe = file.name.normalize("NFC").trim().replace(/["'<>&:\[\]?*|]/g, "_");
    if (!safe || safe === "." || safe === "..") throw new Error("Invalid media filename");
    let name = safe;
    let existing = writes.get(name) ?? await store.read(name);
    if (existing && !sameBytes(existing, file.bytes)) {
      const hash = [...await digest(file.bytes, "SHA-256")].slice(0, 10).map((byte) => byte.toString(16).padStart(2, "0")).join("");
      const dot = safe.lastIndexOf(".");
      const stem = dot > 0 ? safe.slice(0, dot) : safe;
      const extension = dot > 0 ? safe.slice(dot) : "";
      let counter = 0;
      do {
        name = `${stem}-${hash}${counter ? `-${counter}` : ""}${extension}`;
        existing = writes.get(name) ?? await store.read(name);
        counter += 1;
      } while (existing && !sameBytes(existing, file.bytes));
    }
    names.set(file.name.normalize("NFC"), name);
    if (!existing) writes.set(name, file.bytes);
  }
  return { names, writes };
}

function integer(value: unknown, description: string): number {
  const result = typeof value === "number" ? value : NaN;
  if (!Number.isSafeInteger(result)) throw new Error(`Invalid ${description} in package`);
  return result;
}

function allocateIds(existing: number[]) {
  const used = new Set(existing);
  let next = Date.now();
  return (preferred: number) => {
    let id = preferred;
    if (used.has(id)) {
      while (used.has(next)) next += 1;
      id = next++;
    }
    used.add(id);
    return id;
  };
}

function modelKey(model: AnkiNotetype) {
  return JSON.stringify({ type: model.type, fields: model.flds.map((field) => field.name),
    templates: model.tmpls.map((template) => [template.name, template.qfmt, template.afmt]), css: model.css, req: model.req });
}

function renamedModel(name: string, models: AnkiNotetype[]) {
  const names = new Set(models.map((model) => model.name.toLowerCase()));
  let result = name;
  let suffix = 1;
  while (names.has(result.toLowerCase())) result = `${name} (imported${suffix++ === 1 ? "" : ` ${suffix - 1}`})`;
  return result;
}

function validateRows(data: PackageData) {
  const models = new Map(data.models.map((model) => [model.id, model]));
  const decks = new Set(data.decks.map((deck) => deck.id));
  const notes = new Map(data.notes.map((note) => [note.id, note]));
  const cards = new Set(data.cards.map((card) => card.id));
  if (models.size !== data.models.length || decks.size !== data.decks.length
    || notes.size !== data.notes.length || cards.size !== data.cards.length) throw new Error("Duplicate IDs in package");
  const guids = new Set<string>();
  for (const note of data.notes) {
    for (const key of ["id", "mid", "mod", "csum", "flags"]) integer(note[key], `note ${key}`);
    const model = models.get(Number(note.mid));
    if (!model || typeof note.guid !== "string" || !note.guid || guids.has(note.guid)
      || typeof note.flds !== "string" || note.flds.split("\u001f").length !== model.flds.length
      || typeof note.tags !== "string" || typeof note.data !== "string") throw new Error("Invalid note or missing note type in package");
    guids.add(note.guid);
  }
  for (const card of data.cards) {
    for (const key of ["id", "nid", "did", "ord", "mod", "type", "queue", "due", "ivl", "factor", "reps", "lapses", "left", "odue", "odid", "flags"]) {
      integer(card[key], `card ${key}`);
    }
    const note = notes.get(card.nid);
    const model = note && models.get(Number(note.mid));
    if (!model || !decks.has(Number(card.odid || card.did)) || Number(card.ord) < 0
      || (model.type === 0 && Number(card.ord) >= model.tmpls.length)
      || ![0, 1, 2, 3].includes(Number(card.type)) || ![-3, -2, -1, 0, 1, 2, 3, 4].includes(Number(card.queue))
      || typeof card.data !== "string") throw new Error("Invalid card or missing deck in package");
  }
  for (const review of data.reviews) {
    for (const key of ["id", "cid", "ease", "ivl", "lastIvl", "factor", "time", "type"]) integer(review[key], `review ${key}`);
    if (!cards.has(review.cid)) throw new Error("Review history refers to a missing card");
  }
}

function plainText(html: string) {
  return html.replace(/<style[\s\S]*?<\/style>|<script[\s\S]*?<\/script>/gi, "")
    .replace(/<[^>]+>/g, "").replace(/&nbsp;/gi, " ").trim();
}

export async function importApkg(
  sqlite: Sqlite3Static,
  target: Database,
  bytes: Uint8Array,
  store: ImportMediaStore,
  keepScheduling: boolean,
  progress: (message: string) => void = () => {}
): Promise<ApkgImportResult> {
  progress("Reading package…");
  const data = readApkg(sqlite, bytes, keepScheduling);
  validateRows(data);
  progress("Checking notes and media…");
  const existingGuids = new Set(target.selectObjects("SELECT guid FROM notes").map((note) => String(note.guid)));
  const notes = data.notes.filter((note) => !existingGuids.has(String(note.guid)));
  const result: ApkgImportResult = { notes: notes.length, cards: 0, media: 0,
    skippedNotes: data.notes.length - notes.length, decks: [], keptScheduling: keepScheduling };
  // Re-importing must not update existing note types, scheduling, or media.
  if (!notes.length) return result;
  const media = await planMedia(data.media, store);
  const rewrite = (text: string) => rewriteMediaReferences(text, (name) => media.names.get(name));
  const col = target.selectObject("SELECT crt, models, decks FROM col WHERE id = 1")!;
  const models = JSON.parse(String(col.models)) as Record<string, AnkiNotetype>;
  const decks = JSON.parse(String(col.decks)) as Record<string, ImportedDeck>;
  const modelIds = allocateIds(Object.values(models).map((model) => model.id));
  const deckIds = allocateIds(Object.values(decks).map((deck) => deck.id));
  const noteIds = allocateIds(target.selectObjects("SELECT id FROM notes").map((row) => Number(row.id)));
  const cardIds = allocateIds(target.selectObjects("SELECT id FROM cards").map((row) => Number(row.id)));
  const noteMap = new Map<number, number>();
  const cardMap = new Map<number, number>();
  const modelMap = new Map<number, number>();
  const deckMap = new Map<number, number>();
  const importedSourceNotes = new Set(notes.map((note) => note.id));
  const cards = data.cards.filter((card) => importedSourceNotes.has(card.nid));
  const usedDecks = new Set(cards.map((card) => Number(card.odid || card.did)));
  const mod = Math.floor(Date.now() / 1000);
  const ensureDeck = (name: string, preferred: number, original: ImportedDeck) => {
    const existing = Object.values(decks).find((deck) => deck.name.toLowerCase() === name.toLowerCase() && !deck.dyn);
    if (existing) return existing.id;
    const id = deckIds(preferred);
    // The PWA currently has one FSRS preset. Do not overwrite the user's preset.
    decks[String(id)] = { ...original, id, name, dyn: 0, conf: 1, mod, usn: -1,
      newToday: [0, 0], revToday: [0, 0], lrnToday: [0, 0], timeToday: [0, 0] };
    return id;
  };
  for (const deck of data.decks) {
    if (!usedDecks.has(deck.id)) continue;
    const parts = deck.name.split("::");
    for (let depth = 1; depth < parts.length; depth++) ensureDeck(parts.slice(0, depth).join("::"), Date.now(), deck);
    const id = ensureDeck(deck.name, deck.id, deck);
    deckMap.set(deck.id, id);
    result.decks.push(decks[String(id)].name);
  }
  const usedModels = new Set(notes.map((note) => Number(note.mid)));
  for (const original of data.models) {
    if (!usedModels.has(original.id)) continue;
    const model: AnkiNotetype = { ...original, css: rewrite(original.css), mod, usn: -1,
      did: deckMap.get(Number(original.did)) ?? null,
      tmpls: original.tmpls.map((template) => ({ ...template, qfmt: rewrite(template.qfmt), afmt: rewrite(template.afmt),
        bqfmt: rewrite(String(template.bqfmt ?? "")), bafmt: rewrite(String(template.bafmt ?? "")),
        did: deckMap.get(Number(template.did)) ?? null })) };
    const key = modelKey(model);
    const existing = Object.values(models).find((candidate) => modelKey(candidate) === key);
    if (existing) { modelMap.set(original.id, existing.id); continue; }
    model.id = modelIds(original.id);
    model.name = renamedModel(model.name, Object.values(models));
    models[String(model.id)] = model;
    modelMap.set(original.id, model.id);
  }
  for (const note of notes) {
    const sourceId = Number(note.id);
    note.id = noteIds(sourceId);
    noteMap.set(sourceId, Number(note.id));
    note.mid = modelMap.get(Number(note.mid))!;
    const rewritten = rewrite(String(note.flds));
    if (rewritten !== note.flds) {
      const fields = rewritten.split("\u001f");
      note.sfld = plainText(fields[Number(models[String(note.mid)].sortf ?? 0)] ?? "");
      note.csum = [...(await digest(new TextEncoder().encode(plainText(fields[0])))).slice(0, 4)]
        .reduce((number, byte) => number * 256 + byte, 0);
      note.flds = rewritten;
    }
    note.mod = mod;
    note.usn = -1;
  }
  const today = (creation: number) => Math.max(0, Math.floor((mod - creation) / 86_400));
  const dayOffset = today(Number(col.crt)) - today(data.creation);
  let newPosition = Number(target.selectValue("SELECT coalesce(max(due), 0) FROM cards WHERE type = 0")) + 1;
  for (const card of cards) {
    const sourceId = Number(card.id);
    card.id = cardIds(sourceId);
    cardMap.set(sourceId, Number(card.id));
    card.nid = noteMap.get(Number(card.nid))!;
    card.did = deckMap.get(Number(card.odid || card.did))!;
    if (!keepScheduling) {
      for (const key of ["type", "queue", "ivl", "factor", "reps", "lapses", "left"]) card[key] = 0;
      card.data = "";
    } else if (card.odid || card.queue === 4) {
      card.due = card.odue || card.due;
      if (Number(card.queue) >= 0) card.queue = card.type === 2 ? 2 : card.type === 0 ? 0 : Number(card.due) > 1_000_000_000 ? 1 : 3;
    }
    if (card.type === 0) card.due = newPosition++;
    else if (card.queue === 2 || card.queue === 3 || (Number(card.queue) < 0 && (card.type === 2 || Number(card.due) < 1_000_000_000))) {
      card.due = Number(card.due) + dayOffset;
    }
    card.odid = 0; card.odue = 0; card.mod = mod; card.usn = -1;
  }
  const reviews = data.reviews.filter((review) => cardMap.has(Number(review.cid)));
  const reviewIds = new Set(target.selectObjects("SELECT id FROM revlog").map((row) => Number(row.id)));
  for (const review of reviews) {
    let id = Number(review.id);
    while (reviewIds.has(id)) id += 1;
    reviewIds.add(id);
    review.id = id; review.cid = cardMap.get(Number(review.cid))!; review.usn = -1;
  }
  const written: string[] = [];
  try {
    for (const [name, content] of media.writes) {
      progress(`Saving media ${written.length + 1} of ${media.writes.size}…`);
      written.push(name); // Clean up partially created files if a write fails.
      await store.write(name, content);
    }
    progress("Adding cards to your collection…");
    target.transaction("IMMEDIATE", (transaction) => {
      const insert = (table: string, rows: PackageData["notes"]) => {
        if (!rows.length) return;
        const columns = Object.keys(rows[0]);
        const statement = transaction.prepare(`INSERT INTO ${table} (${columns.join(",")}) VALUES (${columns.map(() => "?").join(",")})`);
        try {
          for (const row of rows) statement.bind(columns.map((column) => row[column])).stepReset();
        } finally { statement.finalize(); }
      };
      insert("notes", notes); insert("cards", cards); insert("revlog", reviews);
      transaction.exec({ sql: "UPDATE col SET models = ?, decks = ?, mod = ?, scm = ?, usn = -1 WHERE id = 1",
        bind: [JSON.stringify(models), JSON.stringify(decks), Date.now(), Date.now()] });
    });
  } catch (error) {
    const cleanup = await Promise.allSettled(written.map((name) => store.remove(name)));
    const orphaned = cleanup.filter((result) => result.status === "rejected").length;
    if (orphaned) throw new Error(`Import failed; no cards were added. ${orphaned} unused media files could not be removed. ${String(error)}`);
    throw error;
  }
  result.cards = cards.length;
  result.media = media.writes.size;
  return result;
}
