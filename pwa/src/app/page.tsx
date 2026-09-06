import { LocalCollectionStatus } from "@/components/LocalCollectionStatus";

export default function HomePage() {
  return (
    <main className="app-shell">
      <header className="top-bar">
        <div>
          <p className="eyebrow">ANKI PWA</p>
          <h1>Decks</h1>
        </div>
        <div className="top-actions" aria-label="Deck actions">
          <button className="icon-button" type="button" aria-label="Add deck" title="Add deck">
            +
          </button>
        </div>
      </header>

      <LocalCollectionStatus />

      <footer className="bottom-nav" aria-label="Primary navigation">
        <button className="nav-item active" type="button">Decks</button>
        <button className="nav-item" type="button" disabled>Browse</button>
        <button className="nav-item" type="button" disabled>Stats</button>
        <button className="nav-item" type="button" disabled>Settings</button>
      </footer>
    </main>
  );
}
