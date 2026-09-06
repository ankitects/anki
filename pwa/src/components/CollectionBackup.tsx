"use client";

import { useState } from "react";

import { exportCollection } from "@/lib/db/client";
import type { CollectionBackupResult } from "@/lib/db/types";

function downloadBackup(result: CollectionBackupResult) {
  const url = URL.createObjectURL(new Blob([result.bytes], { type: "application/x-colpkg" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = result.filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1_000);
}

export function CollectionBackup({ persistent, onBusyChange }: {
  persistent: boolean;
  onBusyChange: (busy: boolean) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CollectionBackupResult | null>(null);

  const createBackup = async () => {
    if (busy) return;
    setBusy(true);
    onBusyChange(true);
    setError(null);
    setResult(null);
    setProgress("Preparing backup…");
    try {
      const backup = await exportCollection(setProgress);
      downloadBackup(backup);
      setResult(backup);
    } catch (error) {
      setError(error instanceof Error ? error.message : "The collection could not be backed up.");
    } finally {
      setBusy(false);
      onBusyChange(false);
      setProgress("");
    }
  };

  return (
    <section className="settings-list">
      <div className="panel backup-panel" aria-busy={busy}>
        <div>
          <h2>Collection backup</h2>
          <p className="muted">Download your decks, cards, scheduling history, note types, and media as an Anki collection package.</p>
        </div>
        {!persistent && <p className="form-error" role="alert">This collection is using temporary storage. Download a backup before closing or reloading the app.</p>}
        <p className="muted backup-help">The downloaded .colpkg can be imported into the official Anki desktop app. Importing a .colpkg back into this PWA is not supported yet.</p>
        {error && <p className="form-error" role="alert">{error}</p>}
        {busy && <p role="status" aria-live="polite">{progress}</p>}
        {result && <p className="backup-result" role="status" aria-live="polite">
          Backup downloaded · {result.notes} notes · {result.cards} cards · {result.reviews} reviews · {result.media} media files
        </p>}
        <button className="primary-button" type="button" disabled={busy} onClick={() => void createBackup()}>
          {busy ? "Creating backup…" : "Download backup"}
        </button>
      </div>
    </section>
  );
}
