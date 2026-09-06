"use client";

export function SharedDecks({ onImport }: { onImport: () => void }) {
  return (
    <section className="shared-decks">
      <form className="panel shared-search" action="https://ankiweb.net/shared/decks" method="get"
        target="_blank" rel="noopener noreferrer">
        <h2>Find a shared deck</h2>
        <p className="muted">Search public decks on AnkiWeb, then download the `.apkg` package and import it here.</p>
        <label htmlFor="shared-query">Search</label>
        <div className="shared-search-row">
          <input id="shared-query" name="search" type="search" autoFocus required minLength={3} maxLength={100}
            placeholder="e.g. German vocabulary" />
          <button className="primary-button" type="submit">Search AnkiWeb ↗</button>
        </div>
        <label htmlFor="shared-sort">Sort results</label>
        <select id="shared-sort" name="sort" defaultValue="rating">
          <option value="rating">Top rated</option>
          <option value="modified">Recently updated</option>
          <option value="notes">Most notes</option>
          <option value="title">Title</option>
        </select>
        <p className="shared-search-note">The results open on AnkiWeb so its login works normally. This PWA never receives your AnkiWeb password or cookies.</p>
      </form>

      <div className="shared-steps" aria-label="How to import a shared deck">
        <div><strong>1</strong><span>Search AnkiWeb</span></div>
        <div><strong>2</strong><span>Download the `.apkg`</span></div>
        <div><strong>3</strong><span>Import it here</span></div>
      </div>

      <div className="panel shared-import-help">
        <div>
          <strong>Downloaded your deck?</strong>
          <span className="muted">Select the `.apkg` file to add it to this device.</span>
        </div>
        <button className="secondary-button" type="button" onClick={onImport}>Open Import</button>
      </div>
    </section>
  );
}
