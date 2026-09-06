"use client";

import { useEffect, useState } from "react";

import { initLocalCollection, listDecks } from "@/lib/db/client";
import type { DeckSummary, LocalCollectionInfo } from "@/lib/db/types";

type State =
  | { status: "loading" }
  | { status: "ready"; info: LocalCollectionInfo; decks: DeckSummary[] }
  | { status: "error"; message: string };

export function LocalCollectionStatus() {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const info = await initLocalCollection();
        const decks = await listDecks();
        if (!cancelled) setState({ status: "ready", info, decks });
      } catch (error) {
        if (!cancelled) {
          setState({
            status: "error",
            message: error instanceof Error ? error.message : String(error)
          });
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === "loading") {
    return <div className="panel muted">Opening local collection…</div>;
  }

  if (state.status === "error") {
    return (
      <div className="panel error-panel">
        <strong>Local collection failed to open</strong>
        <span>{state.message}</span>
      </div>
    );
  }

  return (
    <>
      <div className="storage-banner">
        <span className={state.info.persistent ? "status-dot good" : "status-dot warning"} />
        <span>
          {state.info.persistent ? "Stored locally on this device" : "Temporary storage fallback"}
        </span>
        <span className="storage-meta">SQLite {state.info.sqliteVersion}</span>
      </div>

      <section className="deck-list" aria-label="Decks">
        {state.decks.map((deck) => (
          <button className="deck-row" key={deck.id} type="button">
            <span className="deck-name">{deck.name}</span>
            <span className="deck-counts" aria-label={`${deck.totalCards} cards`}>
              <span className="new-count">{deck.newCount}</span>
              <span className="learn-count">{deck.learningCount}</span>
              <span className="review-count">{deck.reviewCount}</span>
            </span>
          </button>
        ))}
      </section>
    </>
  );
}
