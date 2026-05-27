# FlashAI

AI-powered flashcards with spaced repetition — two flavours in one folder:

| File | What it is |
|---|---|
| `index.html` | Browser app — open directly, no install |
| `flashai.py` | Desktop app — Python + tkinter |

---

## Browser app (`index.html`)

Just open the file in any modern browser — Chrome, Firefox, Edge, Safari.

- All data lives in **localStorage** (cards/decks) and **IndexedDB** (images)
- Enter your Anthropic API key in ⚙️ Settings; it stays in your browser only
- Use **Export JSON** to back up or move data between browsers

---

## Desktop app (`flashai.py`)

### Quick start

**Linux / macOS**

```bash
cd ai-flashcards
bash setup.sh        # one-time setup
./run.sh             # launch
```

**Windows**

```
cd ai-flashcards
setup.bat            # one-time setup
run.bat              # launch
```

**Manual (any platform)**

```bash
python3 -m venv .venv
source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python flashai.py
```

### Requirements

| Package | Why | Without it |
|---|---|---|
| Python ≥ 3.9 | runtime | — |
| `tkinter` | GUI (ships with most Python installs) | app won't start |
| `Pillow` | image display | images disabled; text-only mode |
| `anthropic` | Claude SDK | falls back to `urllib` (still works) |

```
pip install anthropic pillow
```

### Data storage

All data is saved to **`~/.flashai/flashai.db`** (SQLite).  
Images are stored as BLOBs in the database — no external files needed.

Export/Import via the **📚 Decks** tab produces JSON compatible with the browser version (text cards only; images are not included in the JSON export).

---

## How the SRS works

FlashAI uses a simplified **SM-2** algorithm:

```
Again → interval = 1d;             ease −0.20
Hard  → interval × 1.2;           ease −0.15
Good  → interval × ease           (default ease = 2.5×)
Easy  → interval × ease × 1.3;    ease +0.15
```

New card intervals grow: 1d → 4d → ~10d → ~25d → … until months apart,
showing each card right before you'd forget it.

---

## AI card generation

1. Add your **Anthropic API key** in ⚙️ Settings
2. Go to ✨ **Generate**, paste any text
3. FlashAI asks Claude to produce `{front, back}` JSON pairs
4. Preview, remove any you don't want, then **Add to Deck**

The API call goes directly from your browser / desktop to `api.anthropic.com` —
no intermediary server.
