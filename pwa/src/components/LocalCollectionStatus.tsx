"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  addNote,
  answerCard,
  createDeck,
  deleteDeck,
  getNextCard,
  initLocalCollection,
  listDecks,
  listNotetypes,
  renameDeck,
  storeMedia
} from "@/lib/db/client";
import type { DeckSummary, LocalCollectionInfo, NoteTypeSummary, ReviewRating, StudyCard } from "@/lib/db/types";
import { CardBrowser } from "./CardBrowser";
import { CollectionBackup } from "./CollectionBackup";
import { ImportDeck } from "./ImportDeck";
import { emptyImageOcclusionDraft, ImageOcclusionEditor } from "./ImageOcclusionEditor";
import type { ImageOcclusionDraft } from "./ImageOcclusionEditor";
import { SharedDecks } from "./SharedDecks";

type LoadState =
  | { status: "loading" }
  | { status: "ready"; info: LocalCollectionInfo; decks: DeckSummary[]; notetypes: NoteTypeSummary[] }
  | { status: "error"; message: string };

type Screen = "decks" | "browse" | "settings" | "deck" | "create-deck" | "manage-deck" | "add-note" | "review" | "import" | "shared";

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error);
}

function cardDocument(content: string, cardCss: string) {
  return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
    <style>
      :root{color-scheme:light dark}body{margin:0;padding:28px 22px;font-family:Arial,sans-serif;font-size:21px;
      line-height:1.45;text-align:center;color:#17191c;background:#fff;overflow-wrap:anywhere}
      hr#answer{margin:28px 0;border:0;border-top:1px solid #d8dce3}img,video{max-width:100%;height:auto}
      .anki-audio{width:min(100%,360px);margin:14px auto}.hint{color:#1f6fd1;text-decoration:underline;cursor:help}
      .type-answer-marker{display:inline-block;margin-top:16px;color:#667085;font-size:14px}.type-answer-correct{font-weight:700}
      @media(prefers-color-scheme:dark){body{color:#f3f4f6;background:#1c1f23}hr#answer{border-color:#3b4149}}
      ${cardCss.replace(/<\/style/gi, "<\\/style")}
    </style></head><body class="card">${content}</body></html>`;
}

function occlusionNumber(value: number) {
  if (!Number.isFinite(value) || value === 0) return ".0000";
  return value.toFixed(4).replace(/^0+|0+$/g, "");
}

function imageOcclusionFields(notetype: NoteTypeSummary, draft: ImageOcclusionDraft, filename: string) {
  const indexes = notetype.imageOcclusionFields;
  if (!indexes) throw new Error("This Image Occlusion note type is missing its field mapping");
  const fields = notetype.fields.map(() => "");
  fields[indexes.occlusions] = draft.masks.map((mask, index) => {
    const inactive = draft.hideAllGuessOne ? ":oi=1" : "";
    return `{{c${index + 1}::image-occlusion:rect:left=${occlusionNumber(mask.left)}:top=${occlusionNumber(mask.top)}:width=${occlusionNumber(mask.width)}:height=${occlusionNumber(mask.height)}${inactive}}}<br>`;
  }).join("");
  fields[indexes.image] = `<img src="${encodeURIComponent(filename)}">`;
  fields[indexes.header] = draft.header.trim();
  fields[indexes.backExtra] = draft.backExtra.trim();
  if (indexes.comments !== undefined && indexes.comments < fields.length) fields[indexes.comments] = draft.comments.trim();
  return fields;
}

function leafDeckName(name: string) {
  return name.split("::").at(-1) ?? name;
}

export function LocalCollectionStatus() {
  const [state, setState] = useState<LoadState>({ status: "loading" });
  const [screen, setScreen] = useState<Screen>("decks");
  const [selectedDeckId, setSelectedDeckId] = useState<number | null>(null);
  const [deckName, setDeckName] = useState("");
  const [subdeckName, setSubdeckName] = useState("");
  const [noteTypeId, setNoteTypeId] = useState<number | null>(null);
  const [noteFields, setNoteFields] = useState<string[]>([]);
  const [attachments, setAttachments] = useState<File[]>([]);
  const [imageOcclusion, setImageOcclusion] = useState<ImageOcclusionDraft>(emptyImageOcclusionDraft);
  const [studyCard, setStudyCard] = useState<StudyCard | null>(null);
  const [studyComplete, setStudyComplete] = useState(false);
  const [answerShown, setAnswerShown] = useState(false);
  const [typedAnswer, setTypedAnswer] = useState("");
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const shownAt = useRef(Date.now());

  const refreshDecks = useCallback(async () => {
    const decks = await listDecks();
    setState((current) => current.status === "ready" ? { ...current, decks } : current);
    return decks;
  }, []);

  const refreshCollection = useCallback(async () => {
    const [decks, notetypes] = await Promise.all([listDecks(), listNotetypes()]);
    setState((current) => current.status === "ready" ? { ...current, decks, notetypes } : current);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const info = await initLocalCollection();
        const [decks, notetypes] = await Promise.all([listDecks(), listNotetypes()]);
        if (!cancelled) {
          setState({ status: "ready", info, decks, notetypes });
          const initial = notetypes.find((notetype) => notetype.name === "Basic") ?? notetypes[0];
          if (initial) {
            setNoteTypeId(initial.id);
            setNoteFields(initial.fields.map(() => ""));
          }
        }
      } catch (error) {
        if (!cancelled) setState({ status: "error", message: errorMessage(error) });
      }
    };
    void load();
    return () => { cancelled = true; };
  }, []);

  const selectedDeck = useMemo(
    () => state.status === "ready" ? state.decks.find((deck) => deck.id === selectedDeckId) ?? null : null,
    [selectedDeckId, state]
  );
  const selectedNotetype = useMemo(
    () => state.status === "ready" ? state.notetypes.find((notetype) => notetype.id === noteTypeId) ?? null : null,
    [noteTypeId, state]
  );

  const goToDecks = () => {
    setScreen("decks");
    setSelectedDeckId(null);
    setActionError(null);
  };

  const goToDeck = (deckId: number) => {
    setSelectedDeckId(deckId);
    setScreen("deck");
    setActionError(null);
  };

  const openDeckManagement = () => {
    if (!selectedDeck) return;
    setDeckName(leafDeckName(selectedDeck.name));
    setSubdeckName("");
    setActionError(null);
    setScreen("manage-deck");
  };

  const saveDeck = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setActionError(null);
    try {
      const deck = await createDeck(deckName);
      await refreshDecks();
      setDeckName("");
      goToDeck(deck.id);
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const saveDeckRename = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedDeck) return;
    const leaf = deckName.trim();
    if (leaf.includes("::")) {
      setActionError("Rename one deck level at a time; use subdecks for hierarchy.");
      return;
    }
    const parent = selectedDeck.name.split("::").slice(0, -1).join("::");
    const fullName = parent ? `${parent}::${leaf}` : leaf;
    setBusy(true);
    setActionError(null);
    try {
      await renameDeck(selectedDeck.id, fullName);
      await refreshDecks();
      setScreen("deck");
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const saveSubdeck = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedDeck) return;
    const child = subdeckName.trim();
    if (!child) return;
    if (child.includes("::")) {
      setActionError("Create one subdeck level at a time.");
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      const deck = await createDeck(`${selectedDeck.name}::${child}`);
      await refreshDecks();
      setSubdeckName("");
      goToDeck(deck.id);
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const removeSelectedDeck = async () => {
    if (!selectedDeck || selectedDeck.id === 1 || busy) return;
    const hasChildren = state.decks.some((deck) => deck.id !== selectedDeck.id && deck.name.toLocaleLowerCase().startsWith(`${selectedDeck.name.toLocaleLowerCase()}::`));
    const scope = hasChildren ? "this deck, its subdecks, and their cards" : "this deck and its cards";
    if (!window.confirm(`Delete ${scope}? Notes that have no cards left will also be deleted. This cannot be undone.`)) return;
    setBusy(true);
    setActionError(null);
    try {
      await deleteDeck(selectedDeck.id);
      await refreshDecks();
      goToDecks();
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const saveNote = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedDeckId || !selectedNotetype) return;
    setBusy(true);
    setActionError(null);
    try {
      if (selectedNotetype.kind === "image-occlusion") {
        if (!imageOcclusion.image) throw new Error("Choose an image to occlude");
        if (!imageOcclusion.image.size || imageOcclusion.image.size > 64 * 1024 * 1024) {
          throw new Error("Choose a non-empty image no larger than 64 MiB");
        }
        if (!imageOcclusion.image.type.startsWith("image/")
          && !/\.(?:avif|gif|jpe?g|png|svg|webp)$/i.test(imageOcclusion.image.name)) {
          throw new Error("Choose a supported image file");
        }
        if (!imageOcclusion.masks.length) throw new Error("Draw at least one mask on the image");
        const filename = await storeMedia(imageOcclusion.image.name, await imageOcclusion.image.arrayBuffer());
        await addNote(selectedDeckId, selectedNotetype.id, imageOcclusionFields(selectedNotetype, imageOcclusion, filename));
        await refreshDecks();
        setImageOcclusion(emptyImageOcclusionDraft);
        setScreen("deck");
        return;
      }
      const mediaMarkup: string[] = [];
      for (const file of attachments) {
        const filename = await storeMedia(file.name, await file.arrayBuffer());
        if (file.type.startsWith("image/")) mediaMarkup.push(`<img src="${filename}">`);
        else if (file.type.startsWith("video/")) mediaMarkup.push(`<video controls src="${filename}"></video>`);
        else mediaMarkup.push(`[sound:${filename}]`);
      }
      const fields = [...noteFields];
      const mediaField = Math.min(1, Math.max(0, fields.length - 1));
      fields[mediaField] = [fields[mediaField], ...mediaMarkup].filter(Boolean).join("<br>");
      await addNote(selectedDeckId, selectedNotetype.id, fields);
      await refreshDecks();
      setNoteFields(selectedNotetype.fields.map(() => ""));
      setAttachments([]);
      setScreen("deck");
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const beginStudy = async () => {
    if (!selectedDeckId) return;
    setBusy(true);
    setActionError(null);
    try {
      const card = await getNextCard(selectedDeckId);
      setStudyCard(card);
      setStudyComplete(card === null);
      setAnswerShown(false);
      setTypedAnswer("");
      shownAt.current = Date.now();
      setScreen("review");
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const rateCard = async (rating: ReviewRating) => {
    if (!studyCard || !selectedDeckId) return;
    setBusy(true);
    setActionError(null);
    try {
      await answerCard(studyCard.id, rating, Date.now() - shownAt.current);
      const next = await getNextCard(selectedDeckId);
      setStudyCard(next);
      setStudyComplete(next === null);
      setAnswerShown(false);
      setTypedAnswer("");
      shownAt.current = Date.now();
      await refreshDecks();
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  if (state.status === "loading") {
    return <main className="app-shell"><div className="panel muted">Opening local collection…</div></main>;
  }

  if (state.status === "error") {
    return (
      <main className="app-shell">
        <div className="panel error-panel">
          <strong>Local collection failed to open</strong>
          <span>{state.message}</span>
        </div>
      </main>
    );
  }

  const showBack = screen !== "decks" && screen !== "browse" && screen !== "settings";
  const title = screen === "import" ? "Import" : screen === "shared" ? "Shared decks" : screen === "browse" ? "Browse" : screen === "settings" ? "Settings"
    : screen === "decks" || screen === "create-deck" ? "Decks" : selectedDeck?.name ?? "Deck";

  return (
    <main className="app-shell">
      <header className="top-bar">
        <div className="title-group">
          {showBack && (
            <button className="back-button" type="button" disabled={busy}
              onClick={screen === "deck" || !selectedDeckId ? goToDecks : () => goToDeck(selectedDeckId)} aria-label="Back">
              ‹
            </button>
          )}
          <div>
            <p className="eyebrow">ANKI PWA</p>
            <h1>{title}</h1>
          </div>
        </div>
        {screen === "decks" && (
          <div className="top-actions">
            <button className="secondary-button" type="button"
              onClick={() => { setScreen("shared"); setActionError(null); }}>Shared</button>
            <button className="secondary-button" type="button" onClick={() => setScreen("import")}>Import</button>
            <button className="icon-button" type="button" onClick={() => { setDeckName(""); setScreen("create-deck"); setActionError(null); }} aria-label="Add deck">+</button>
          </div>
        )}
        {screen === "deck" && (
          <div className="top-actions">
            <button className="secondary-button" type="button" disabled={busy} onClick={openDeckManagement}>Manage</button>
            <button className="icon-button" type="button" onClick={() => {
              const initial = state.notetypes.find((notetype) => notetype.id === noteTypeId) ?? state.notetypes[0];
              if (initial) {
                setNoteTypeId(initial.id);
                setNoteFields(initial.fields.map(() => ""));
              }
              setAttachments([]);
              setImageOcclusion(emptyImageOcclusionDraft);
              setScreen("add-note");
              setActionError(null);
            }} aria-label="Add card">+</button>
          </div>
        )}
      </header>

      {screen === "decks" && (
        <>
          <div className="storage-banner">
            <span className={state.info.persistent ? "status-dot good" : "status-dot warning"} />
            <span>{state.info.persistent ? "Stored locally on this device" : "Temporary storage fallback"}</span>
            <span className="storage-meta">Anki schema {state.info.schemaVersion} · SQLite {state.info.sqliteVersion}</span>
          </div>
          <section className="deck-list" aria-label="Decks">
            {state.decks.map((deck) => {
              const depth = Math.max(0, deck.name.split("::").length - 1);
              return (
                <button className="deck-row" key={deck.id} type="button" onClick={() => goToDeck(deck.id)}>
                  <span className="deck-name" style={{ paddingInlineStart: `${depth * 18}px` }}>
                    {depth > 0 ? "↳ " : ""}{leafDeckName(deck.name)}
                  </span>
                  <span className="deck-counts" aria-label={`${deck.totalCards} cards`}>
                    <span className="new-count">{deck.newCount}</span>
                    <span className="learn-count">{deck.learningCount}</span>
                    <span className="review-count">{deck.reviewCount}</span>
                  </span>
                </button>
              );
            })}
          </section>
        </>
      )}

      {screen === "import" && <ImportDeck persistent={state.info.persistent} onBusyChange={setBusy}
        onImported={refreshCollection} onDone={goToDecks} />}

      {screen === "shared" && <SharedDecks onImport={() => setScreen("import")} />}

      {screen === "browse" && <CardBrowser decks={state.decks} onCollectionChanged={refreshCollection} />}

      {screen === "settings" && <CollectionBackup persistent={state.info.persistent} onBusyChange={setBusy} />}

      {screen === "create-deck" && (
        <form className="panel form-panel" onSubmit={saveDeck}>
          <label htmlFor="deck-name">Deck name</label>
          <input id="deck-name" autoFocus value={deckName} onChange={(event) => setDeckName(event.target.value)} placeholder="e.g. Spanish" />
          {actionError && <p className="form-error" role="alert">{actionError}</p>}
          <button className="primary-button" type="submit" disabled={busy || !deckName.trim()}>{busy ? "Creating…" : "Create deck"}</button>
        </form>
      )}

      {screen === "manage-deck" && selectedDeck && (
        <section className="settings-list">
          <form className="panel form-panel" onSubmit={saveDeckRename}>
            <div className="form-heading"><strong>Rename deck</strong><span>{selectedDeck.name}</span></div>
            <label htmlFor="manage-deck-name">Name</label>
            <input id="manage-deck-name" autoFocus value={deckName} onChange={(event) => setDeckName(event.target.value)} />
            <button className="primary-button" type="submit" disabled={busy || !deckName.trim()}>{busy ? "Saving…" : "Save name"}</button>
          </form>
          <form className="panel form-panel" onSubmit={saveSubdeck}>
            <div className="form-heading"><strong>Create subdeck</strong><span>Under {selectedDeck.name}</span></div>
            <label htmlFor="subdeck-name">Subdeck name</label>
            <input id="subdeck-name" value={subdeckName} onChange={(event) => setSubdeckName(event.target.value)} placeholder="e.g. Verbs" />
            <button className="secondary-button" type="submit" disabled={busy || !subdeckName.trim()}>{busy ? "Creating…" : "Create subdeck"}</button>
          </form>
          {actionError && <p className="panel form-error" role="alert">{actionError}</p>}
          {selectedDeck.id !== 1 && (
            <div className="panel form-panel">
              <div className="form-heading"><strong>Delete deck</strong><span>{selectedDeck.totalCards} cards including subdecks</span></div>
              <p className="muted">Deleting a deck also deletes its subdecks and cards. Notes are removed only when no cards remain elsewhere.</p>
              <button className="danger-button" type="button" disabled={busy} onClick={() => void removeSelectedDeck()}>Delete deck</button>
            </div>
          )}
        </section>
      )}

      {screen === "deck" && selectedDeck && (
        <section className="deck-overview">
          <div className="count-grid">
            <div><strong className="new-count">{selectedDeck.newCount}</strong><span>New</span></div>
            <div><strong className="learn-count">{selectedDeck.learningCount}</strong><span>Learning</span></div>
            <div><strong className="review-count">{selectedDeck.reviewCount}</strong><span>To review</span></div>
          </div>
          {actionError && <p className="form-error panel" role="alert">{actionError}</p>}
          <button className="primary-button study-button" type="button" disabled={busy} onClick={beginStudy}>{busy ? "Opening…" : "Study now"}</button>
          <p className="deck-total">{selectedDeck.totalCards} {selectedDeck.totalCards === 1 ? "card" : "cards"} total, including subdecks</p>
        </section>
      )}

      {screen === "add-note" && selectedDeck && selectedNotetype && (
        <form className="panel form-panel" onSubmit={saveNote}>
          <div className="form-heading"><strong>Add note</strong></div>
          <label htmlFor="note-type">Note type</label>
          <select id="note-type" value={selectedNotetype.id} onChange={(event) => {
            const id = Number(event.target.value);
            const notetype = state.notetypes.find((candidate) => candidate.id === id);
            setNoteTypeId(id);
            setNoteFields(notetype?.fields.map(() => "") ?? []);
            setImageOcclusion(emptyImageOcclusionDraft);
          }}>
            {state.notetypes.map((notetype) => <option value={notetype.id} key={notetype.id}>{notetype.name}</option>)}
          </select>
          {selectedNotetype.kind === "image-occlusion" ? (
            <ImageOcclusionEditor value={imageOcclusion} disabled={busy} onChange={setImageOcclusion} />
          ) : selectedNotetype.fields.map((field, index) => (
            <div className="field-editor" key={`${selectedNotetype.id}-${field}`}>
              <label htmlFor={`note-field-${index}`}>{field}</label>
              <textarea id={`note-field-${index}`} autoFocus={index === 0} rows={index === 0 ? 5 : 4} value={noteFields[index] ?? ""} onChange={(event) => setNoteFields((current) => current.map((value, fieldIndex) => fieldIndex === index ? event.target.value : value))} placeholder={selectedNotetype.kind === "cloze" && index === 0 ? "The capital is {{c1::Paris}}." : undefined} />
            </div>
          ))}
          {selectedNotetype.kind !== "image-occlusion" && <label className="media-picker" htmlFor="media-files">
            <span>Attach image or audio</span>
            <input id="media-files" type="file" accept="image/*,audio/*,video/*" multiple onChange={(event) => setAttachments([...event.target.files ?? []])} />
          </label>}
          {selectedNotetype.kind !== "image-occlusion" && attachments.length > 0 && <p className="attachment-list">{attachments.map((file) => file.name).join(", ")}</p>}
          {actionError && <p className="form-error" role="alert">{actionError}</p>}
          <button className="primary-button" type="submit" disabled={busy || (selectedNotetype.kind === "image-occlusion"
            ? !imageOcclusion.image || !imageOcclusion.masks.length : !noteFields[0]?.trim())}>{busy ? "Saving…" : "Add card"}</button>
        </form>
      )}

      {screen === "review" && (
        <section className="reviewer">
          {studyComplete ? (
            <div className="panel congratulations">
              <span className="complete-mark">✓</span>
              <h2>Congratulations!</h2>
              <p className="muted">You have finished this deck for now.</p>
              <button className="secondary-button" type="button" onClick={() => selectedDeckId && goToDeck(selectedDeckId)}>Back to deck</button>
            </div>
          ) : studyCard ? (
            <>
              <iframe className="card-frame" sandbox="" title={answerShown ? "Card answer" : "Card question"} srcDoc={cardDocument(answerShown ? studyCard.answerHtml : studyCard.questionHtml, studyCard.cardCss)} />
              {actionError && <p className="form-error" role="alert">{actionError}</p>}
              {!answerShown ? (
                <>
                  {studyCard.typedAnswer && <label className="typed-answer-panel" htmlFor="typed-answer">
                    <span>Type your answer</span>
                    <input id="typed-answer" autoFocus autoComplete="off" value={typedAnswer}
                      onChange={(event) => setTypedAnswer(event.target.value)}
                      onKeyDown={(event) => { if (event.key === "Enter") setAnswerShown(true); }} />
                  </label>}
                  <button className="primary-button show-answer" type="button" onClick={() => setAnswerShown(true)}>Show answer</button>
                </>
              ) : (
                <>
                  {studyCard.typedAnswer && <div className={`typed-answer-result ${typedAnswer.normalize("NFC").trim() === studyCard.typedAnswer.correct.normalize("NFC").trim() ? "correct" : "incorrect"}`}>
                    <span>Your answer</span><strong>{typedAnswer || "(blank)"}</strong>
                    <span>Correct answer</span><strong>{studyCard.typedAnswer.correct}</strong>
                  </div>}
                  <div className="answer-grid">
                    {studyCard.answerOptions.map((option) => (
                      <button className={`answer-button rating-${option.rating}`} type="button" key={option.rating} disabled={busy} onClick={() => void rateCard(option.rating)}>
                        <span>{option.intervalLabel}</span>{["", "Again", "Hard", "Good", "Easy"][option.rating]}
                      </button>
                    ))}
                  </div>
                </>
              )}
            </>
          ) : null}
        </section>
      )}

      {(screen === "decks" || screen === "browse" || screen === "settings") && (
        <footer className="bottom-nav" aria-label="Primary navigation">
          <button className={`nav-item ${screen === "decks" ? "active" : ""}`} type="button" onClick={goToDecks}>Decks</button>
          <button className={`nav-item ${screen === "browse" ? "active" : ""}`} type="button"
            onClick={() => { setScreen("browse"); setSelectedDeckId(null); setActionError(null); }}>Browse</button>
          <button className="nav-item" type="button" disabled>Stats</button>
          <button className={`nav-item ${screen === "settings" ? "active" : ""}`} type="button"
            onClick={() => { setScreen("settings"); setSelectedDeckId(null); setActionError(null); }}>Settings</button>
        </footer>
      )}
    </main>
  );
}
