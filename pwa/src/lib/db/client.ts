"use client";

import type { DbRequest, DbResponse, DeckSummary, LocalCollectionInfo } from "./types";

let worker: Worker | null = null;
let requestId = 0;
const pending = new Map<number, { resolve: (value: unknown) => void; reject: (reason?: unknown) => void }>();

function getWorker(): Worker {
  if (typeof window === "undefined") {
    throw new Error("The local collection is only available in the browser");
  }

  if (!worker) {
    worker = new Worker(new URL("./anki-db.worker.ts", import.meta.url), { type: "module" });
    worker.addEventListener("message", (event: MessageEvent<DbResponse>) => {
      const response = event.data;
      const waiter = pending.get(response.id);
      if (!waiter) return;

      pending.delete(response.id);
      if (response.ok) {
        waiter.resolve(response.result);
      } else {
        waiter.reject(new Error(response.error ?? "Unknown SQLite worker error"));
      }
    });

    worker.addEventListener("error", (event) => {
      const error = new Error(event.message || "SQLite worker crashed");
      for (const waiter of pending.values()) waiter.reject(error);
      pending.clear();
      worker?.terminate();
      worker = null;
    });
  }

  return worker;
}

function request<T>(type: DbRequest["type"]): Promise<T> {
  const id = ++requestId;
  const message: DbRequest = { id, type };

  return new Promise<T>((resolve, reject) => {
    pending.set(id, {
      resolve: (value) => resolve(value as T),
      reject
    });
    getWorker().postMessage(message);
  });
}

export function initLocalCollection() {
  return request<LocalCollectionInfo>("init");
}

export function listDecks() {
  return request<DeckSummary[]>("listDecks");
}
