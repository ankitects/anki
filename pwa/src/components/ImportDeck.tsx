"use client";

import { useState } from "react";

import { importApkg } from "@/lib/db/client";
import type { ApkgImportResult } from "@/lib/db/types";

const MAX_FILE_BYTES = 128 * 1024 * 1024;

export function ImportDeck({ persistent, onBusyChange, onImported, onDone }: {
  persistent: boolean;
  onBusyChange: (busy: boolean) => void;
  onImported: () => Promise<void>;
  onDone: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [keepScheduling, setKeepScheduling] = useState(true);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ApkgImportResult | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!file || busy) return;
    setBusy(true);
    onBusyChange(true);
    setError(null);
    setResult(null);
    setProgress("Opening file…");
    try {
      if (!/\.apkg$/i.test(file.name)) throw new Error("Choose an Anki deck package (.apkg), not a collection backup (.colpkg).");
      if (!file.size || file.size > MAX_FILE_BYTES) throw new Error("Choose a non-empty .apkg file no larger than 128 MiB.");
      const imported = await importApkg(await file.arrayBuffer(), keepScheduling, setProgress);
      setResult(imported);
      try { await onImported(); }
      catch { setError("Import finished, but the deck list could not refresh. Reload the app to see your cards."); }
    } catch (error) {
      setError(error instanceof Error ? error.message : "The deck could not be imported.");
    } finally {
      setBusy(false);
      onBusyChange(false);
      setProgress("");
    }
  };

  return (
    <form className="panel form-panel import-panel" onSubmit={submit} aria-busy={busy}>
      <h2>Import an Anki deck</h2>
      <p className="muted">Choose an .apkg exported from Anki or downloaded from shared decks. Your file stays on this device.</p>
      {!persistent && <p className="form-error" role="alert">Storage is temporary in this browser. Imported cards and media will be lost when you close or reload the app.</p>}
      <label htmlFor="apkg-file">Deck package (.apkg, up to 128 MiB)</label>
      <input id="apkg-file" type="file" accept=".apkg" disabled={busy} onChange={(event) => {
        setFile(event.target.files?.[0] ?? null);
        setError(null);
        setResult(null);
      }} />
      <label className="checkbox-label" htmlFor="keep-scheduling">
        <input id="keep-scheduling" type="checkbox" checked={keepScheduling} disabled={busy}
          onChange={(event) => setKeepScheduling(event.target.checked)} />
        Keep imported scheduling and review history
      </label>
      <p className="muted import-help">Uncheck to start imported cards as new. Existing notes are skipped, including any edits to them. Imported cards use this PWA’s current FSRS settings; deck option presets and add-ons are not imported.</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      {busy && <p role="status" aria-live="polite">{progress}</p>}
      {result && (
        <div className="import-result" role="status" aria-live="polite">
          <strong>{result.notes ? "Import complete" : "No new notes to import"}</strong>
          <p>{result.notes} notes · {result.cards} cards · {result.media} new media files</p>
          {result.skippedNotes > 0 && <p>{result.skippedNotes} existing notes skipped. Your edits and reviews were kept.</p>}
          {result.decks.length > 0 && <p>Decks: {result.decks.join(", ")}</p>}
          <button className="primary-button" type="button" onClick={onDone}>Back to decks</button>
        </div>
      )}
      {!result && <button className="primary-button" type="submit" disabled={!file || busy}>{busy ? "Importing…" : "Import deck"}</button>}
    </form>
  );
}
