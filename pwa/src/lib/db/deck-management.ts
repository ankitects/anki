import type { Database } from "@sqlite.org/sqlite-wasm";

export type DeckRecord = {
  id: number;
  name: string;
  mod: number;
  usn: number;
  dyn: number;
  [key: string]: unknown;
};

const DEFAULT_DECK_ID = 1;

function nowSeconds() {
  return Math.floor(Date.now() / 1000);
}

function sameName(left: string, right: string) {
  return left.localeCompare(right, undefined, { sensitivity: "base" }) === 0;
}

function isSameOrChild(name: string, parentName: string) {
  return sameName(name, parentName)
    || name.toLocaleLowerCase().startsWith(`${parentName.toLocaleLowerCase()}::`);
}

function readDecks(database: Database): Record<string, DeckRecord> {
  const json = String(database.selectValue("SELECT decks FROM col WHERE id = 1") ?? "{}");
  return JSON.parse(json) as Record<string, DeckRecord>;
}

function touchCollection(database: Database) {
  database.exec({
    sql: "UPDATE col SET mod = ?, usn = -1 WHERE id = 1",
    bind: [nowSeconds()]
  });
}

export function normalizeDeckName(input: string) {
  const parts = input.normalize("NFC").trim().split("::").map((part) => part.trim());
  if (!parts.length || parts.some((part) => !part)) throw new Error("Deck name cannot be empty");
  if (parts.some((part) => /[\u0000-\u001f\u007f]/.test(part))) throw new Error("Deck name contains unsupported characters");
  const name = parts.join("::");
  if (name.length > 500) throw new Error("Deck name is too long");
  return name;
}

export function deckScopeIds(decks: Record<string, DeckRecord>, deckId: number) {
  const deck = decks[String(deckId)];
  if (!deck || deck.dyn !== 0) throw new Error("Deck not found");
  return Object.values(decks)
    .filter((candidate) => candidate.dyn === 0 && isSameOrChild(candidate.name, deck.name))
    .map((candidate) => candidate.id);
}

export function renameDeck(database: Database, deckId: number, nameInput: string): void {
  const decks = readDecks(database);
  const deck = decks[String(deckId)];
  if (!deck || deck.dyn !== 0) throw new Error("Deck not found");

  const nextName = normalizeDeckName(nameInput);
  const previousName = deck.name;
  if (previousName === nextName) return;
  if (nextName.toLocaleLowerCase().startsWith(`${previousName.toLocaleLowerCase()}::`)) {
    throw new Error("A deck cannot be moved inside itself");
  }

  const subtree = Object.values(decks).filter((candidate) => candidate.dyn === 0 && isSameOrChild(candidate.name, previousName));
  const subtreeIds = new Set(subtree.map((candidate) => candidate.id));
  const replacements = subtree.map((candidate) => ({
    deck: candidate,
    name: candidate.id === deckId ? nextName : `${nextName}${candidate.name.slice(previousName.length)}`
  }));

  for (const replacement of replacements) {
    const conflict = Object.values(decks).find((candidate) =>
      !subtreeIds.has(candidate.id) && sameName(candidate.name, replacement.name));
    if (conflict) throw new Error(`A deck named “${replacement.name}” already exists`);
  }

  const normalizedTargets = new Set<string>();
  for (const replacement of replacements) {
    const key = replacement.name.toLocaleLowerCase();
    if (normalizedTargets.has(key)) throw new Error("The renamed deck hierarchy would contain duplicate names");
    normalizedTargets.add(key);
  }

  const mod = nowSeconds();
  for (const replacement of replacements) {
    replacement.deck.name = replacement.name;
    replacement.deck.mod = mod;
    replacement.deck.usn = -1;
  }

  database.transaction("IMMEDIATE", (transaction) => {
    transaction.exec({ sql: "UPDATE col SET decks = ? WHERE id = 1", bind: [JSON.stringify(decks)] });
    touchCollection(transaction);
  });
}

export function deleteDeck(database: Database, deckId: number): void {
  const decks = readDecks(database);
  const deck = decks[String(deckId)];
  if (!deck || deck.dyn !== 0) throw new Error("Deck not found");
  if (deckId === DEFAULT_DECK_ID) throw new Error("The Default deck cannot be deleted");

  const deckIds = deckScopeIds(decks, deckId);
  const deckIdSet = new Set(deckIds);
  const placeholders = deckIds.map(() => "?").join(",");
  const cardRows = database.selectObjects(`SELECT id, nid FROM cards WHERE did IN (${placeholders})`, deckIds);
  const cardIds = cardRows.map((row) => Number(row.id));
  const candidateNoteIds = [...new Set(cardRows.map((row) => Number(row.nid)))];
  const orphanNoteIds = candidateNoteIds.filter((noteId) => {
    const remaining = Number(database.selectValue(
      `SELECT count(*) FROM cards WHERE nid = ? AND did NOT IN (${placeholders})`,
      [noteId, ...deckIds]
    ) ?? 0);
    return remaining === 0;
  });

  for (const id of deckIds) delete decks[String(id)];

  database.transaction("IMMEDIATE", (transaction) => {
    for (const cardId of cardIds) {
      transaction.exec({ sql: "INSERT INTO graves (usn, oid, type) VALUES (-1, ?, 0)", bind: [cardId] });
      transaction.exec({ sql: "DELETE FROM revlog WHERE cid = ?", bind: [cardId] });
      transaction.exec({ sql: "DELETE FROM cards WHERE id = ?", bind: [cardId] });
    }
    for (const noteId of orphanNoteIds) {
      transaction.exec({ sql: "INSERT INTO graves (usn, oid, type) VALUES (-1, ?, 1)", bind: [noteId] });
      transaction.exec({ sql: "DELETE FROM notes WHERE id = ?", bind: [noteId] });
    }
    for (const id of deckIdSet) {
      transaction.exec({ sql: "INSERT INTO graves (usn, oid, type) VALUES (-1, ?, 2)", bind: [id] });
    }
    transaction.exec({ sql: "UPDATE col SET decks = ? WHERE id = 1", bind: [JSON.stringify(decks)] });
    touchCollection(transaction);
  });
}

export function moveCard(database: Database, cardId: number, deckId: number): void {
  const decks = readDecks(database);
  const target = decks[String(deckId)];
  if (!target || target.dyn !== 0) throw new Error("Destination deck not found");
  const card = database.selectObject("SELECT id, did FROM cards WHERE id = ?", [cardId]);
  if (!card) throw new Error("Card not found");
  if (Number(card.did) === deckId) return;

  database.transaction("IMMEDIATE", (transaction) => {
    transaction.exec({
      sql: "UPDATE cards SET did = ?, mod = ?, usn = -1 WHERE id = ?",
      bind: [deckId, nowSeconds(), cardId]
    });
    touchCollection(transaction);
  });
}
