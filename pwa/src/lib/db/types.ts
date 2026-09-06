export type LocalCollectionInfo = {
  sqliteVersion: string;
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

export type DbRequest = {
  id: number;
  type: "init" | "listDecks";
};

export type DbResponse = {
  id: number;
  ok: boolean;
  result?: unknown;
  error?: string;
};
