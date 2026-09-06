export type LocalCollectionInfo = {
  sqliteVersion: string;
  schemaVersion: number;
  persistent: boolean;
  crossOriginIsolated: boolean;
};

export type DeckSummary = {
  id: number;
  name: string;
  newCount: number;
  learningCount: number;
  reviewCount: number;
  totalCards: number;
};

export type CardState = "new" | "learning" | "review" | "relearning";

export type StudyCard = {
  id: number;
  deckId: number;
  deckName: string;
  questionHtml: string;
  answerHtml: string;
  cardCss: string;
  state: CardState;
  intervalDays: number;
  typedAnswer?: {
    field: string;
    correct: string;
  };
  answerOptions: Array<{
    rating: ReviewRating;
    intervalLabel: string;
  }>;
};

export type ReviewRating = 1 | 2 | 3 | 4;

export type NoteTypeSummary = {
  id: number;
  name: string;
  kind: "standard" | "cloze" | "image-occlusion";
  fields: string[];
  imageOcclusionFields?: {
    occlusions: number;
    image: number;
    header: number;
    backExtra: number;
    comments?: number;
  };
};

export type ApkgImportResult = {
  notes: number;
  cards: number;
  media: number;
  skippedNotes: number;
  decks: string[];
  keptScheduling: boolean;
};

export type BrowserCard = {
  id: number;
  deckId: number;
  deckName: string;
  ordinal: number;
  templateName: string;
  state: CardState;
  status: "active" | "suspended" | "buried";
  intervalDays: number;
  reviews: number;
  lapses: number;
};

export type BrowserNote = {
  id: number;
  notetypeId: number;
  notetypeName: string;
  fieldNames: string[];
  fields: string[];
  tags: string[];
  preview: string;
  modified: number;
  cards: BrowserCard[];
};

export type BrowseNotesResult = {
  notes: BrowserNote[];
  total: number;
  offset: number;
  hasMore: boolean;
};

export type DbCommand =
  | { type: "init" }
  | { type: "listDecks" }
  | { type: "listNotetypes" }
  | { type: "createDeck"; name: string }
  | { type: "addNote"; deckId: number; notetypeId: number; fields: string[] }
  | { type: "addBasicNote"; deckId: number; front: string; back: string }
  | { type: "addClozeNote"; deckId: number; text: string; extra: string }
  | { type: "storeMedia"; filename: string; bytes: ArrayBuffer }
  | { type: "importApkg"; bytes: ArrayBuffer; keepScheduling: boolean }
  | { type: "browseNotes"; query: string; deckId: number | null; offset: number }
  | { type: "updateNote"; noteId: number; fields: string[]; tags: string[] }
  | { type: "deleteNote"; noteId: number }
  | { type: "setCardStatus"; cardId: number; status: BrowserCard["status"] }
  | { type: "getNextCard"; deckId: number }
  | { type: "answerCard"; cardId: number; rating: ReviewRating; timeMs: number };

export type DbRequest = DbCommand & { id: number };

export type DbResponse = {
  id: number;
  ok: boolean;
  result?: unknown;
  error?: string;
  progress?: string;
};
