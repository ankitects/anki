#!/usr/bin/env python3
"""
FlashAI – AI-Powered Flashcard App (Desktop / Tkinter edition)
Spaced repetition (SM-2) + Claude AI card generation + image support.

Install:
    pip install anthropic pillow          # recommended
    pip install pillow                     # images only (uses urllib fallback for API)
    python flashai.py                      # run

Data stored at: ~/.flashai/flashai.db  (SQLite)
"""

import os
import sys
import json
import uuid
import time
import random
import sqlite3
import threading
import io
from pathlib import Path
from typing import Optional
import tkinter as tk
from tkinter import ttk, messagebox, filedialog, scrolledtext

# ── Optional dependencies ──────────────────────────────────────────────────────
try:
    from PIL import Image, ImageTk
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

try:
    import anthropic as _anthropic
    HAS_ANTHROPIC = True
except ImportError:
    HAS_ANTHROPIC = False

# ── Constants ──────────────────────────────────────────────────────────────────
APP_NAME    = "FlashAI"
DB_PATH     = Path.home() / ".flashai" / "flashai.db"

C = {                             # Colour palette (matches the web version)
    "primary":    "#5C6BC0",
    "primary_dk": "#3949AB",
    "success":    "#43A047",
    "warn":       "#FB8C00",
    "danger":     "#E53935",
    "again":      "#EF5350",
    "hard":       "#FFA726",
    "good":       "#66BB6A",
    "easy":       "#42A5F5",
    "bg":         "#F0F2F8",
    "surface":    "#FFFFFF",
    "border":     "#E0E0E0",
    "text":       "#212121",
    "text2":      "#757575",
}

if sys.platform == "win32":
    FONT = "Segoe UI"
elif sys.platform == "darwin":
    FONT = "SF Pro Display"
else:
    FONT = "Ubuntu"


# ── Database ───────────────────────────────────────────────────────────────────
class Database:
    """SQLite-backed persistence layer."""

    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.con = sqlite3.connect(str(path), check_same_thread=False)
        self.con.row_factory = sqlite3.Row
        self.con.execute("PRAGMA foreign_keys = ON")
        self._create_tables()
        self._seed_if_empty()

    def _create_tables(self):
        self.con.executescript("""
            CREATE TABLE IF NOT EXISTS decks (
                id   TEXT PRIMARY KEY,
                name TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS cards (
                id          TEXT PRIMARY KEY,
                deck_id     TEXT NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
                front       TEXT NOT NULL,
                back        TEXT NOT NULL,
                front_image BLOB,
                back_image  BLOB,
                interval    REAL    DEFAULT 1,
                ease        REAL    DEFAULT 2.5,
                due         INTEGER DEFAULT 0,
                reps        INTEGER DEFAULT 0,
                lapses      INTEGER DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS settings (
                key   TEXT PRIMARY KEY,
                value TEXT
            );
        """)
        self.con.commit()

    def _seed_if_empty(self):
        if self.con.execute("SELECT COUNT(*) FROM decks").fetchone()[0]:
            return
        did = str(uuid.uuid4())
        self.con.execute("INSERT INTO decks VALUES (?,?)", (did, "Sample Deck"))
        now = int(time.time())
        samples = [
            ("What is spaced repetition?",
             "A learning technique that schedules reviews at increasing intervals "
             "to maximise long-term retention."),
            ("What does SRS stand for?",
             "Spaced Repetition System"),
            ("Who invented the SM-2 algorithm?",
             "Piotr Woźniak, in 1987, for the SuperMemo software."),
            ("What is the 'forgetting curve'?",
             "A graph showing how memory retention decays exponentially over time "
             "without review — discovered by Ebbinghaus."),
            ("What is a 'lapse' in SRS?",
             "When a card that was in the review queue is answered 'Again' — "
             "it resets to a short interval."),
        ]
        for front, back in samples:
            self.con.execute(
                "INSERT INTO cards (id, deck_id, front, back, due) VALUES (?,?,?,?,?)",
                (str(uuid.uuid4()), did, front, back, now),
            )
        self.con.commit()

    # ── Decks ──────────────────────────────────────────────────
    def get_decks(self):
        return self.con.execute("SELECT * FROM decks ORDER BY name").fetchall()

    def create_deck(self, name: str) -> str:
        did = str(uuid.uuid4())
        self.con.execute("INSERT INTO decks VALUES (?,?)", (did, name))
        self.con.commit()
        return did

    def delete_deck(self, deck_id: str):
        self.con.execute("DELETE FROM decks WHERE id=?", (deck_id,))
        self.con.commit()

    def deck_stats(self, deck_id: str) -> dict:
        now = int(time.time())
        total = self.con.execute(
            "SELECT COUNT(*) FROM cards WHERE deck_id=?", (deck_id,)
        ).fetchone()[0]
        due = self.con.execute(
            "SELECT COUNT(*) FROM cards WHERE deck_id=? AND (reps=0 OR due<=?)",
            (deck_id, now),
        ).fetchone()[0]
        return {"total": total, "due": due}

    # ── Cards ──────────────────────────────────────────────────
    def get_cards(self, deck_id=None, search=None):
        q = ("SELECT c.*, d.name AS deck_name "
             "FROM cards c JOIN decks d ON c.deck_id=d.id WHERE 1=1")
        params = []
        if deck_id:
            q += " AND c.deck_id=?"; params.append(deck_id)
        if search:
            q += " AND (c.front LIKE ? OR c.back LIKE ?)"
            params += [f"%{search}%", f"%{search}%"]
        return self.con.execute(q, params).fetchall()

    def get_due_cards(self, deck_id: str, max_new: int = 20) -> list:
        now = int(time.time())
        review = self.con.execute(
            "SELECT * FROM cards WHERE deck_id=? AND reps>0 AND due<=?",
            (deck_id, now),
        ).fetchall()
        new = self.con.execute(
            "SELECT * FROM cards WHERE deck_id=? AND reps=0 LIMIT ?",
            (deck_id, max_new),
        ).fetchall()
        cards = [dict(r) for r in review + new]
        random.shuffle(cards)
        return cards

    def add_card(self, deck_id, front, back,
                 front_image=None, back_image=None) -> str:
        cid = str(uuid.uuid4())
        self.con.execute(
            "INSERT INTO cards (id,deck_id,front,back,front_image,back_image,due) "
            "VALUES (?,?,?,?,?,?,?)",
            (cid, deck_id, front, back, front_image, back_image, int(time.time())),
        )
        self.con.commit()
        return cid

    def update_card_schedule(self, card: dict):
        self.con.execute(
            "UPDATE cards SET interval=?,ease=?,due=?,reps=?,lapses=? WHERE id=?",
            (card["interval"], card["ease"], card["due"],
             card["reps"], card["lapses"], card["id"]),
        )
        self.con.commit()

    def delete_card(self, card_id: str):
        self.con.execute("DELETE FROM cards WHERE id=?", (card_id,))
        self.con.commit()

    # ── Settings ───────────────────────────────────────────────
    def get_setting(self, key: str, default=None):
        row = self.con.execute(
            "SELECT value FROM settings WHERE key=?", (key,)
        ).fetchone()
        return row[0] if row else default

    def set_setting(self, key: str, value):
        self.con.execute(
            "INSERT OR REPLACE INTO settings VALUES (?,?)", (key, str(value))
        )
        self.con.commit()

    # ── Import / Export ────────────────────────────────────────
    def export_json(self) -> str:
        data: dict = {"decks": {}, "cards": []}
        for d in self.get_decks():
            data["decks"][d["id"]] = {"name": d["name"]}
        for c in self.get_cards():
            card = dict(c)
            card.pop("front_image", None)
            card.pop("back_image", None)
            card.pop("deck_name", None)
            data["cards"].append(card)
        return json.dumps(data, indent=2, ensure_ascii=False)

    def import_json(self, raw: str):
        data = json.loads(raw)
        existing_decks = {d["id"] for d in self.get_decks()}
        existing_cards = {c["id"] for c in self.get_cards()}
        for did, info in data.get("decks", {}).items():
            if did not in existing_decks:
                self.con.execute("INSERT INTO decks VALUES (?,?)", (did, info["name"]))
        for c in data.get("cards", []):
            if c["id"] not in existing_cards:
                self.con.execute(
                    "INSERT OR IGNORE INTO cards "
                    "(id,deck_id,front,back,interval,ease,due,reps,lapses) "
                    "VALUES (?,?,?,?,?,?,?,?,?)",
                    (c["id"], c["deck_id"], c["front"], c["back"],
                     c.get("interval", 1), c.get("ease", 2.5),
                     # Browser stores due in milliseconds; Python uses seconds.
                     # Divide by 1000 when the value is clearly in ms (> year 3000 in s).
                     int(c.get("due", 0)) // 1000
                     if int(c.get("due", 0)) > 32_503_680_000 else int(c.get("due", 0)),
                     c.get("reps", 0), c.get("lapses", 0)),
                )
        self.con.commit()


# ── SRS Algorithm (simplified SM-2) ───────────────────────────────────────────
def schedule_card(card: dict, rating: int) -> dict:
    """Return a new dict with updated scheduling fields. rating: 1=Again … 4=Easy"""
    import copy
    c = copy.deepcopy(card)
    DAY = 86_400

    if rating == 1:               # Again
        c["lapses"] += 1
        c["ease"]    = max(1.3, c["ease"] - 0.20)
        c["interval"] = 1
        c["reps"]    = 0
    else:
        if c["reps"] < 2:         # Bootstrapping
            c["interval"] = 4 if (c["reps"] == 1 and rating >= 3) else 1
        elif rating == 2:         # Hard
            c["interval"] = max(1, round(c["interval"] * 1.2))
            c["ease"]     = max(1.3, c["ease"] - 0.15)
        elif rating == 3:         # Good
            c["interval"] = max(1, round(c["interval"] * c["ease"]))
        else:                     # Easy
            c["interval"] = max(1, round(c["interval"] * c["ease"] * 1.3))
            c["ease"]     = min(4.0, c["ease"] + 0.15)
        c["reps"] += 1

    c["due"] = int(time.time()) + int(c["interval"] * DAY)
    return c


def preview_intervals(card: dict) -> dict:
    return {r: interval_label(schedule_card(card, i)["interval"])
            for r, i in [("hard", 2), ("good", 3), ("easy", 4)]}


def interval_label(days: float) -> str:
    if days < 1:   return "<1d"
    if days < 30:  return f"{int(days)}d"
    if days < 365: return f"{round(days/30)}mo"
    return f"{days/365:.1f}y"


# ── Claude API call (background thread) ───────────────────────────────────────
def generate_cards_async(text: str, api_key: str, model: str, max_cards: int,
                          on_success, on_error):
    prompt = (
        f"Create up to {max_cards} high-quality flashcard pairs from the text below.\n"
        "Each card should test a single, clear concept. Use question-and-answer format "
        "with concise answers (1–2 sentences max).\n"
        "Return ONLY valid JSON — an array of objects with \"front\" and \"back\" keys.\n"
        "No markdown, no code fences, no commentary.\n\n"
        f'Text:\n"""\n{text}\n"""'
    )

    def run():
        try:
            if HAS_ANTHROPIC:
                client = _anthropic.Anthropic(api_key=api_key)
                resp = client.messages.create(
                    model=model, max_tokens=2048,
                    messages=[{"role": "user", "content": prompt}],
                )
                raw = resp.content[0].text
            else:
                import urllib.request
                payload = json.dumps({
                    "model": model, "max_tokens": 2048,
                    "messages": [{"role": "user", "content": prompt}],
                }).encode()
                req = urllib.request.Request(
                    "https://api.anthropic.com/v1/messages",
                    data=payload,
                    headers={
                        "Content-Type": "application/json",
                        "x-api-key": api_key,
                        "anthropic-version": "2023-06-01",
                    },
                )
                with urllib.request.urlopen(req, timeout=60) as r:
                    raw = json.loads(r.read())["content"][0]["text"]

            # Strip markdown fences robustly: ```json ... ``` or plain JSON
            import re as _re
            cleaned = _re.sub(r'^```[a-zA-Z]*\n?', '', raw.strip())
            cleaned = _re.sub(r'\n?```$', '', cleaned).strip()
            cards = json.loads(cleaned)
            if not isinstance(cards, list):
                raise ValueError("Model did not return a JSON array.")
            on_success(cards)
        except Exception as exc:
            on_error(str(exc))

    threading.Thread(target=run, daemon=True).start()


# ── Image helpers ──────────────────────────────────────────────────────────────
def blob_to_photoimage(blob: Optional[bytes],
                       max_size=(520, 180)) -> Optional["ImageTk.PhotoImage"]:
    if not HAS_PIL or not blob:
        return None
    try:
        img = Image.open(io.BytesIO(blob))
        img.thumbnail(max_size, Image.LANCZOS)
        return ImageTk.PhotoImage(img)
    except Exception:
        return None


# ── Utility: simple card-surface frame ────────────────────────────────────────
def surface_frame(parent, **kw) -> tk.Frame:
    kw.setdefault("bg", C["surface"])
    kw.setdefault("relief", tk.FLAT)
    return tk.Frame(parent, **kw)


def hdr_label(parent, text, **kw) -> tk.Label:
    kw.setdefault("bg", C["bg"])
    kw.setdefault("fg", C["text"])
    kw.setdefault("font", (FONT, 14, "bold"))
    return tk.Label(parent, text=text, **kw)


def small_label(parent, text, **kw) -> tk.Label:
    kw.setdefault("bg", C["bg"])
    kw.setdefault("fg", C["text2"])
    kw.setdefault("font", (FONT, 9))
    return tk.Label(parent, text=text, **kw)


def flat_button(parent, text, bg, fg="white", command=None,
                padx=14, pady=7, **kw) -> tk.Button:
    return tk.Button(
        parent, text=text, bg=bg, fg=fg,
        activebackground=bg, activeforeground=fg,
        relief=tk.FLAT, bd=0, font=(FONT, 10, "bold"),
        padx=padx, pady=pady, cursor="hand2",
        command=command, **kw,
    )


# ── Main Application Window ────────────────────────────────────────────────────
class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(APP_NAME)
        self.geometry("940x660")
        self.minsize(720, 520)
        self.configure(bg=C["bg"])

        try:
            self.db = Database(DB_PATH)
        except Exception as exc:
            messagebox.showerror(
                "Database Error",
                f"Could not open the FlashAI database:\n\n{exc}\n\n"
                f"Path: {DB_PATH}\n\n"
                "Check file permissions or delete the file to start fresh.",
            )
            self.destroy()
            return
        self._setup_ttk_style()
        self._build_header()
        self._build_content()
        self.show_tab("review")

    # ── Style ──────────────────────────────────────────────────
    def _setup_ttk_style(self):
        s = ttk.Style(self)
        s.theme_use("clam")
        s.configure("TFrame",        background=C["bg"])
        s.configure("TLabel",        background=C["bg"], font=(FONT, 10))
        s.configure("TEntry",        padding=6)
        s.configure("TCombobox",     padding=6)
        s.configure("Treeview",
                    background=C["surface"], fieldbackground=C["surface"],
                    foreground=C["text"], rowheight=34, font=(FONT, 9))
        s.configure("Treeview.Heading",
                    font=(FONT, 9, "bold"), foreground=C["text2"],
                    background=C["bg"])
        s.map("Treeview",
              background=[("selected", C["primary"])],
              foreground=[("selected", "white")])

    # ── Header ─────────────────────────────────────────────────
    def _build_header(self):
        hdr = tk.Frame(self, bg=C["primary"], height=54)
        hdr.pack(fill=tk.X)
        hdr.pack_propagate(False)

        tk.Label(hdr, text="Flash", font=(FONT, 16, "bold"),
                 bg=C["primary"], fg="white").pack(side=tk.LEFT, padx=(18, 0))
        tk.Label(hdr, text="AI", font=(FONT, 16),
                 bg=C["primary"], fg="#ffffffbb").pack(side=tk.LEFT, padx=(0, 20))

        self._nav_btns: dict[str, tk.Button] = {}
        tabs = [
            ("review",   "📖 Review"),
            ("generate", "✨ Generate"),
            ("browse",   "🗂 Browse"),
            ("decks",    "📚 Decks"),
            ("settings", "⚙️ Settings"),
        ]
        for name, label in tabs:
            btn = tk.Button(
                hdr, text=label, font=(FONT, 10),
                bg=C["primary"], fg="white",
                activebackground=C["primary_dk"], activeforeground="white",
                relief=tk.FLAT, bd=0, padx=12, pady=8, cursor="hand2",
                command=lambda n=name: self.show_tab(n),
            )
            btn.pack(side=tk.LEFT, padx=2)
            self._nav_btns[name] = btn

    # ── Content ────────────────────────────────────────────────
    def _build_content(self):
        self._content = tk.Frame(self, bg=C["bg"])
        self._content.pack(fill=tk.BOTH, expand=True, padx=14, pady=14)

        self._tabs: dict[str, "BaseTab"] = {
            "review":   ReviewTab(self._content, self),
            "generate": GenerateTab(self._content, self),
            "browse":   BrowseTab(self._content, self),
            "decks":    DecksTab(self._content, self),
            "settings": SettingsTab(self._content, self),
        }
        for tab in self._tabs.values():
            tab.place(relwidth=1, relheight=1)

    def show_tab(self, name: str):
        for tab in self._tabs.values():
            tab.lower()
        self._tabs[name].lift()
        self._tabs[name].on_show()
        for n, btn in self._nav_btns.items():
            btn.configure(bg=C["primary_dk"] if n == name else C["primary"])


# ── Base Tab ───────────────────────────────────────────────────────────────────
class BaseTab(tk.Frame):
    def __init__(self, parent, app: App):
        super().__init__(parent, bg=C["bg"])
        self.app = app

    def on_show(self):
        pass


# ── Review Tab ─────────────────────────────────────────────────────────────────
class ReviewTab(BaseTab):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._deck_map: dict[str, str] = {}  # populated by _refresh_deck_combo
        self._queue: list[dict] = []
        self._idx = 0
        self._done = 0
        self._total = 0
        self._flipped = False
        self._deck_id = ""
        self._photo_front: Optional["ImageTk.PhotoImage"] = None
        self._photo_back:  Optional["ImageTk.PhotoImage"] = None
        self._build()

    def _build(self):
        # ── Deck picker ────────────────────────────────────────
        top = tk.Frame(self, bg=C["bg"])
        top.pack(fill=tk.X, pady=(0, 6))
        tk.Label(top, text="Deck:", bg=C["bg"], font=(FONT, 10)).pack(side=tk.LEFT)
        self._deck_var = tk.StringVar()
        self._deck_cb = ttk.Combobox(top, textvariable=self._deck_var,
                                     state="readonly", width=28)
        self._deck_cb.pack(side=tk.LEFT, padx=8)
        self._deck_cb.bind("<<ComboboxSelected>>", lambda _: self._start())

        # ── Stats ──────────────────────────────────────────────
        sf = tk.Frame(self, bg=C["bg"])
        sf.pack()
        self._lbl_new  = tk.Label(sf, bg=C["bg"], fg=C["easy"],  font=(FONT, 9, "bold"))
        self._lbl_rev  = tk.Label(sf, bg=C["bg"], fg=C["good"],  font=(FONT, 9, "bold"))
        self._lbl_done = tk.Label(sf, bg=C["bg"], fg=C["text2"], font=(FONT, 9, "bold"))
        for l in (self._lbl_new, self._lbl_rev, self._lbl_done):
            l.pack(side=tk.LEFT, padx=10)

        # ── Progress bar ───────────────────────────────────────
        pb_wrap = tk.Frame(self, bg=C["border"], height=6)
        pb_wrap.pack(fill=tk.X, pady=6)
        pb_wrap.pack_propagate(False)
        self._prog = tk.Frame(pb_wrap, bg=C["primary"], height=6)
        self._prog.place(relwidth=0, relheight=1)

        # ── Card surface ───────────────────────────────────────
        self._card = surface_frame(self, padx=32, pady=24)
        self._card.pack(fill=tk.BOTH, expand=True, pady=6)

        self._lbl_q_tag  = tk.Label(self._card, text="QUESTION", bg=C["surface"],
                                     fg=C["primary"], font=(FONT, 8, "bold"))
        self._lbl_q_tag.pack()
        self._lbl_front  = tk.Label(self._card, text="", bg=C["surface"],
                                     fg=C["text"], font=(FONT, 14),
                                     wraplength=600, justify=tk.CENTER)
        self._lbl_front.pack(expand=True)
        self._lbl_f_img  = tk.Label(self._card, bg=C["surface"])
        # packed only when image present

        self._sep = tk.Frame(self._card, bg=C["border"], height=1)

        self._lbl_a_tag  = tk.Label(self._card, text="ANSWER", bg=C["surface"],
                                     fg=C["success"], font=(FONT, 8, "bold"))
        self._lbl_back   = tk.Label(self._card, text="", bg=C["surface"],
                                     fg=C["text"], font=(FONT, 12),
                                     wraplength=600, justify=tk.CENTER)
        self._lbl_b_img  = tk.Label(self._card, bg=C["surface"])

        # ── Action area ────────────────────────────────────────
        self._action = tk.Frame(self, bg=C["bg"])
        self._action.pack(pady=8)

        self._btn_show = flat_button(self._action, "Show Answer",
                                     C["primary"], command=self._flip)
        self._btn_show.pack()

        self._rating_row = tk.Frame(self._action, bg=C["bg"])

        self._hint_labels: dict[str, tk.Label] = {}
        for label, color, rating in [
            ("Again", C["again"], 1),
            ("Hard",  C["hard"],  2),
            ("Good",  C["good"],  3),
            ("Easy",  C["easy"],  4),
        ]:
            col = tk.Frame(self._rating_row, bg=C["bg"])
            col.pack(side=tk.LEFT, padx=6)
            flat_button(col, label, color, width=8, padx=0,
                        command=lambda r=rating: self._rate(r)).pack()
            hint = tk.Label(col, text="", bg=C["bg"], fg=C["text2"], font=(FONT, 8))
            hint.pack()
            self._hint_labels[label.lower()] = hint

        self._lbl_empty = tk.Label(self, text="🎉 No cards due right now!",
                                    bg=C["bg"], fg=C["text2"], font=(FONT, 13))

    # ── Public ─────────────────────────────────────────────────
    def on_show(self):
        self._refresh_deck_combo()
        self._start()

    def jump_to_deck(self, deck_name: str):
        """Called from Decks tab 'Study' button."""
        self._deck_var.set(deck_name)
        self._start()

    # ── Internal ───────────────────────────────────────────────
    def _refresh_deck_combo(self):
        decks = self.app.db.get_decks()
        self._deck_map = {d["name"]: d["id"] for d in decks}
        names = list(self._deck_map)
        self._deck_cb["values"] = names
        if names and self._deck_var.get() not in names:
            self._deck_var.set(names[0])

    def _start(self):
        name = self._deck_var.get()
        if not name:
            return
        self._deck_id = self._deck_map.get(name, "")
        max_new = int(self.app.db.get_setting("new_per_day", 20))
        self._queue = self.app.db.get_due_cards(self._deck_id, max_new)
        self._idx = self._done = 0
        self._total = len(self._queue)
        self._flipped = False
        self._render()

    def _render(self):
        pct = self._done / self._total if self._total else 0
        self._prog.place(relwidth=pct, relheight=1)

        remaining = self._queue[self._idx:]
        new_c = sum(1 for c in remaining if c["reps"] == 0)
        rev_c = sum(1 for c in remaining if c["reps"] > 0)
        self._lbl_new.config( text=f"🔵 {new_c} new")
        self._lbl_rev.config( text=f"🟢 {rev_c} review")
        self._lbl_done.config(text=f"⚪ {self._done} done")

        if self._idx >= len(self._queue):
            msg = (f"✅ Session complete! Reviewed {self._done} card(s)."
                   if self._total else "🎉 No cards due! Come back later.")
            self._show_empty(msg)
            return

        card = self._queue[self._idx]
        self._show_front(card)

    def _show_front(self, card: dict):
        self._lbl_empty.pack_forget()
        self._card.pack(fill=tk.BOTH, expand=True, pady=6)
        self._action.pack(pady=8)

        self._lbl_q_tag.config(fg=C["primary"])
        self._lbl_front.config(text=card["front"])

        # Front image
        self._photo_front = blob_to_photoimage(card.get("front_image"))
        if self._photo_front:
            self._lbl_f_img.config(image=self._photo_front)
            self._lbl_f_img.pack(pady=8)
        else:
            self._lbl_f_img.pack_forget()

        # Hide back side
        for w in (self._sep, self._lbl_a_tag, self._lbl_back, self._lbl_b_img):
            w.pack_forget()

        self._btn_show.pack()
        self._rating_row.pack_forget()
        self._flipped = False

    def _flip(self):
        if self._flipped:
            return
        self._flipped = True
        card = self._queue[self._idx]

        self._sep.pack(fill=tk.X, pady=10)
        self._lbl_a_tag.pack()
        self._lbl_back.config(text=card["back"])
        self._lbl_back.pack(pady=4)

        self._photo_back = blob_to_photoimage(card.get("back_image"))
        if self._photo_back:
            self._lbl_b_img.config(image=self._photo_back)
            self._lbl_b_img.pack(pady=8)
        else:
            self._lbl_b_img.pack_forget()

        ivs = preview_intervals(card)
        self._hint_labels["hard"].config(text="~" + ivs["hard"])
        self._hint_labels["good"].config(text="~" + ivs["good"])
        self._hint_labels["easy"].config(text="~" + ivs["easy"])

        self._btn_show.pack_forget()
        self._rating_row.pack()

    def _rate(self, rating: int):
        card = self._queue[self._idx]
        updated = schedule_card(card, rating)
        self.app.db.update_card_schedule(updated)
        self._done += 1
        self._idx += 1
        self._flipped = False
        self._render()

    def _show_empty(self, msg: str):
        self._card.pack_forget()
        self._action.pack_forget()
        self._lbl_empty.config(text=msg)
        self._lbl_empty.pack(expand=True)


# ── Generate Tab ───────────────────────────────────────────────────────────────
class GenerateTab(BaseTab):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._generated: list[dict] = []
        self._build()

    def _build(self):
        small_label(self, "Source Text — paste a textbook excerpt, notes, or vocabulary list:").pack(anchor=tk.W)
        self._txt = scrolledtext.ScrolledText(
            self, height=8, wrap=tk.WORD, font=(FONT, 10),
            bg=C["surface"], fg=C["text"], relief=tk.FLAT,
            insertbackground=C["text"],
        )
        self._txt.pack(fill=tk.X, pady=(4, 10))
        self._txt.insert("1.0", "Paste text here…")
        self._txt.bind("<FocusIn>", self._clear_ph)

        # Controls
        ctrl = tk.Frame(self, bg=C["bg"])
        ctrl.pack(fill=tk.X, pady=(0, 8))

        tk.Label(ctrl, text="Deck:", bg=C["bg"], font=(FONT, 10)).pack(side=tk.LEFT)
        self._deck_var = tk.StringVar()
        self._deck_cb  = ttk.Combobox(ctrl, textvariable=self._deck_var,
                                       state="readonly", width=24)
        self._deck_cb.pack(side=tk.LEFT, padx=6)

        tk.Label(ctrl, text="Max cards:", bg=C["bg"], font=(FONT, 10)).pack(side=tk.LEFT)
        self._count_var = tk.StringVar(value="10")
        ttk.Combobox(ctrl, textvariable=self._count_var,
                     values=["5", "10", "15", "20"],
                     state="readonly", width=5).pack(side=tk.LEFT, padx=6)

        self._btn_gen = flat_button(ctrl, "✨ Generate", C["primary"],
                                    command=self._generate)
        self._btn_gen.pack(side=tk.LEFT, padx=10)

        self._lbl_status = tk.Label(self, text="", bg=C["bg"],
                                     fg=C["text2"], font=(FONT, 10))
        self._lbl_status.pack()

        # Scrollable preview
        wrap = tk.Frame(self, bg=C["bg"])
        wrap.pack(fill=tk.BOTH, expand=True)
        self._canvas = tk.Canvas(wrap, bg=C["bg"], highlightthickness=0)
        sb = ttk.Scrollbar(wrap, orient=tk.VERTICAL, command=self._canvas.yview)
        self._canvas.configure(yscrollcommand=sb.set)
        sb.pack(side=tk.RIGHT, fill=tk.Y)
        self._canvas.pack(fill=tk.BOTH, expand=True)
        self._inner = tk.Frame(self._canvas, bg=C["bg"])
        self._canvas.create_window((0, 0), window=self._inner, anchor=tk.NW)
        self._inner.bind("<Configure>", lambda _: self._canvas.configure(
            scrollregion=self._canvas.bbox("all")))

        # Add-to-deck button (hidden until cards ready)
        self._btn_add = flat_button(self, "✅ Add to Deck", C["success"],
                                    command=self._add_cards)

    def on_show(self):
        self._refresh_decks()

    def _refresh_decks(self):
        decks = self.app.db.get_decks()
        self._deck_map = {d["name"]: d["id"] for d in decks}
        names = list(self._deck_map)
        self._deck_cb["values"] = names
        if names and self._deck_var.get() not in names:
            self._deck_var.set(names[0])

    def _clear_ph(self, _):
        if self._txt.get("1.0", "end-1c") == "Paste text here…":
            self._txt.delete("1.0", tk.END)

    def _generate(self):
        text = self._txt.get("1.0", "end-1c").strip()
        if not text or text == "Paste text here…":
            messagebox.showwarning("Input needed", "Please paste some text first.")
            return
        api_key = self.app.db.get_setting("api_key", "")
        if not api_key:
            messagebox.showwarning("API Key", "Add your Anthropic API key in ⚙️ Settings.")
            return

        self._btn_gen.configure(state=tk.DISABLED, text="Generating…")
        self._lbl_status.config(text="Asking Claude…")
        self._btn_add.pack_forget()

        generate_cards_async(
            text=text,
            api_key=api_key,
            model=self.app.db.get_setting("model", "claude-sonnet-4-6"),
            max_cards=int(self._count_var.get()),
            on_success=lambda cards: self.after(0, self._on_success, cards),
            on_error=lambda err:     self.after(0, self._on_error, err),
        )

    def _on_success(self, cards: list):
        self._generated = cards
        self._btn_gen.configure(state=tk.NORMAL, text="✨ Generate")
        self._lbl_status.config(text=f"{len(cards)} cards generated — review below.")
        self._render_preview()
        self._btn_add.pack(pady=8)

    def _on_error(self, err: str):
        self._btn_gen.configure(state=tk.NORMAL, text="✨ Generate")
        self._lbl_status.config(text="")
        messagebox.showerror("Generation failed", err)

    def _render_preview(self):
        for w in self._inner.winfo_children():
            w.destroy()
        for i, card in enumerate(self._generated):
            row = surface_frame(self._inner, padx=12, pady=10)
            row.pack(fill=tk.X, padx=4, pady=3)
            tk.Label(row, text=card["front"], bg=C["surface"], fg=C["text"],
                     font=(FONT, 10, "bold"), wraplength=550,
                     justify=tk.LEFT, anchor=tk.W).pack(fill=tk.X)
            tk.Label(row, text=card["back"], bg=C["surface"], fg=C["text2"],
                     font=(FONT, 9), wraplength=550,
                     justify=tk.LEFT, anchor=tk.W).pack(fill=tk.X)
            def _remove(idx=i):
                self._generated.pop(idx)
                self._lbl_status.config(text=f"{len(self._generated)} cards")
                self._render_preview()
                if not self._generated:
                    self._btn_add.pack_forget()
            tk.Button(row, text="✕", bg=C["surface"], fg=C["text2"],
                      relief=tk.FLAT, font=(FONT, 9), cursor="hand2",
                      command=_remove).place(relx=1.0, rely=0, anchor=tk.NE)

    def _add_cards(self):
        did = self._deck_map.get(self._deck_var.get())
        if not did:
            return
        for c in self._generated:
            self.app.db.add_card(did, c["front"].strip(), c["back"].strip())
        n, name = len(self._generated), self._deck_var.get()
        self._generated = []
        for w in self._inner.winfo_children():
            w.destroy()
        self._btn_add.pack_forget()
        self._lbl_status.config(text="")
        messagebox.showinfo("Done!", f"{n} card(s) added to "{name}".")


# ── Browse Tab ─────────────────────────────────────────────────────────────────
class BrowseTab(BaseTab):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._card_map: dict[str, str] = {}   # tree iid → card id
        self._build()

    def _build(self):
        # Filter row
        top = tk.Frame(self, bg=C["bg"])
        top.pack(fill=tk.X, pady=(0, 8))

        tk.Label(top, text="Search:", bg=C["bg"]).pack(side=tk.LEFT)
        self._search_var = tk.StringVar()
        ttk.Entry(top, textvariable=self._search_var, width=22).pack(side=tk.LEFT, padx=6)
        self._search_var.trace_add("write", lambda *_: self._refresh())

        tk.Label(top, text="Deck:", bg=C["bg"]).pack(side=tk.LEFT)
        self._filter_var = tk.StringVar()
        self._filter_cb  = ttk.Combobox(top, textvariable=self._filter_var,
                                         state="readonly", width=22)
        self._filter_cb.bind("<<ComboboxSelected>>", lambda _: self._refresh())
        self._filter_cb.pack(side=tk.LEFT, padx=6)

        flat_button(top, "+ Add Card", C["primary"],
                    command=self._add_dialog, pady=5).pack(side=tk.RIGHT)

        # Treeview
        cols = ("front", "back", "deck", "due", "interval")
        self._tree = ttk.Treeview(self, columns=cols, show="headings",
                                   selectmode="browse")
        for col, label, width in [
            ("front",    "Front",    240),
            ("back",     "Back",     240),
            ("deck",     "Deck",     120),
            ("due",      "Due",       80),
            ("interval", "Interval",  70),
        ]:
            self._tree.heading(col, text=label)
            self._tree.column(col, width=width, anchor=tk.W)

        sb = ttk.Scrollbar(self, command=self._tree.yview)
        self._tree.configure(yscrollcommand=sb.set)
        sb.pack(side=tk.RIGHT, fill=tk.Y)
        self._tree.pack(fill=tk.BOTH, expand=True)
        self._tree.bind("<Delete>",    self._delete_selected)
        self._tree.bind("<BackSpace>", self._delete_selected)

    def on_show(self):
        self._refresh_filter()
        self._refresh()

    def _refresh_filter(self):
        decks = self.app.db.get_decks()
        self._deck_map = {"All Decks": ""} | {d["name"]: d["id"] for d in decks}
        self._filter_cb["values"] = list(self._deck_map)
        if self._filter_var.get() not in self._deck_map:
            self._filter_var.set("All Decks")

    def _refresh(self):
        search  = self._search_var.get() or None
        deck_id = (self._deck_map.get(self._filter_var.get(), "")
                   if hasattr(self, "_deck_map") else "")
        cards   = self.app.db.get_cards(deck_id or None, search)
        now     = int(time.time())

        self._tree.delete(*self._tree.get_children())
        self._card_map = {}

        for c in cards:
            diff = c["due"] - now
            if c["reps"] == 0:
                due_s = "new"
            elif diff < 0:
                d = abs(diff) // 86400
                due_s = f"{d}d ago" if d else "today"
            else:
                d = diff // 86400
                due_s = f"in {d}d" if d else "today"

            has_img = bool(c["front_image"] or c["back_image"])
            front_s = ("📷 " if has_img else "") + c["front"][:55]

            iid = self._tree.insert("", tk.END, values=(
                front_s, c["back"][:55], c["deck_name"],
                due_s, interval_label(c["interval"]),
            ))
            self._card_map[iid] = c["id"]

    def _delete_selected(self, _=None):
        sel = self._tree.selection()
        if not sel:
            return
        if not messagebox.askyesno("Delete", "Delete selected card?"):
            return
        for iid in sel:
            cid = self._card_map.get(iid)
            if cid:
                self.app.db.delete_card(cid)
        self._refresh()

    def _add_dialog(self):
        _AddCardDialog(self, self.app, on_done=self._refresh)


# ── Add Card Dialog ────────────────────────────────────────────────────────────
class _AddCardDialog(tk.Toplevel):
    def __init__(self, parent, app: App, on_done=None):
        super().__init__(parent)
        self.app = app
        self._on_done = on_done
        self._front_img: Optional[bytes] = None
        self._back_img:  Optional[bytes] = None

        self.title("Add Card")
        self.geometry("520x500")
        self.configure(bg=C["bg"])
        self.transient(parent)
        self.grab_set()
        self._build()

    def _build(self):
        decks = self.app.db.get_decks()
        self._deck_map = {d["name"]: d["id"] for d in decks}

        def row(label):
            tk.Label(self, text=label, bg=C["bg"], fg=C["text2"],
                     font=(FONT, 9, "bold")).pack(anchor=tk.W, padx=16, pady=(10, 2))

        row("Deck")
        self._deck_var = tk.StringVar(value=list(self._deck_map)[0] if self._deck_map else "")
        ttk.Combobox(self, textvariable=self._deck_var,
                     values=list(self._deck_map), state="readonly").pack(fill=tk.X, padx=16)

        row("Front (Question)")
        self._txt_front = scrolledtext.ScrolledText(
            self, height=4, wrap=tk.WORD, font=(FONT, 10),
            bg=C["surface"], fg=C["text"], relief=tk.FLAT)
        self._txt_front.pack(fill=tk.X, padx=16)

        self._lbl_fi, self._btn_fi = self._img_row("front", "📎 Front image")

        row("Back (Answer)")
        self._txt_back = scrolledtext.ScrolledText(
            self, height=4, wrap=tk.WORD, font=(FONT, 10),
            bg=C["surface"], fg=C["text"], relief=tk.FLAT)
        self._txt_back.pack(fill=tk.X, padx=16)

        self._lbl_bi, self._btn_bi = self._img_row("back", "📎 Back image")

        btns = tk.Frame(self, bg=C["bg"])
        btns.pack(pady=12, padx=16, anchor=tk.W)
        flat_button(btns, "Add Card", C["success"], command=self._save).pack(side=tk.LEFT)
        flat_button(btns, "Cancel", C["bg"], fg=C["text2"],
                    command=self.destroy).pack(side=tk.LEFT, padx=8)

    def _img_row(self, side: str, label: str):
        row = tk.Frame(self, bg=C["bg"])
        row.pack(anchor=tk.W, padx=16, pady=2)
        lbl = tk.Label(row, text="No image", bg=C["bg"], fg=C["text2"], font=(FONT, 8))
        lbl.pack(side=tk.LEFT)
        btn = tk.Button(row, text=label, bg=C["bg"], fg=C["primary"],
                        relief=tk.FLAT, font=(FONT, 8), cursor="hand2",
                        command=lambda s=side, l=lbl: self._pick_img(s, l))
        btn.pack(side=tk.LEFT, padx=6)
        return lbl, btn

    def _pick_img(self, side: str, lbl: tk.Label):
        path = filedialog.askopenfilename(
            parent=self,
            filetypes=[("Image files", "*.png *.jpg *.jpeg *.gif *.webp *.bmp")],
        )
        if not path:
            return
        with open(path, "rb") as f:
            blob = f.read()
        if side == "front":
            self._front_img = blob
        else:
            self._back_img  = blob
        lbl.config(text=os.path.basename(path))

    def _save(self):
        front = self._txt_front.get("1.0", "end-1c").strip()
        back  = self._txt_back.get( "1.0", "end-1c").strip()
        if not front or not back:
            messagebox.showwarning("Required", "Both Front and Back are required.", parent=self)
            return
        did = self._deck_map.get(self._deck_var.get())
        if not did:
            return
        self.app.db.add_card(did, front, back, self._front_img, self._back_img)
        if self._on_done:
            self._on_done()
        self.destroy()


# ── Decks Tab ──────────────────────────────────────────────────────────────────
class DecksTab(BaseTab):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def _build(self):
        top = tk.Frame(self, bg=C["bg"])
        top.pack(fill=tk.X, pady=(0, 12))
        hdr_label(top, "Your Decks").pack(side=tk.LEFT)
        flat_button(top, "+ New Deck", C["primary"],
                    pady=6, command=self._new_deck_dialog).pack(side=tk.RIGHT)

        self._list = tk.Frame(self, bg=C["bg"])
        self._list.pack(fill=tk.BOTH, expand=True)

        # Import / Export section
        sep = tk.Frame(self, bg=C["border"], height=1)
        sep.pack(fill=tk.X, pady=12)
        io_row = tk.Frame(self, bg=C["bg"])
        io_row.pack(anchor=tk.W)
        tk.Label(io_row, text="Import / Export  ", bg=C["bg"],
                 font=(FONT, 11, "bold"), fg=C["text"]).pack(side=tk.LEFT)
        flat_button(io_row, "⬇️ Export JSON", C["bg"], fg=C["primary"],
                    pady=5, command=self._export).pack(side=tk.LEFT, padx=6)
        flat_button(io_row, "⬆️ Import JSON", C["bg"], fg=C["primary"],
                    pady=5, command=self._import).pack(side=tk.LEFT)

    def on_show(self):
        self._refresh()

    def _refresh(self):
        for w in self._list.winfo_children():
            w.destroy()
        decks = self.app.db.get_decks()
        if not decks:
            small_label(self._list, "No decks yet — create one!").pack(pady=20)
            return
        for deck in decks:
            stats = self.app.db.deck_stats(deck["id"])
            row = surface_frame(self._list, padx=18, pady=14)
            row.pack(fill=tk.X, pady=4)

            info = tk.Frame(row, bg=C["surface"])
            info.pack(side=tk.LEFT, fill=tk.X, expand=True)
            tk.Label(info, text=deck["name"], bg=C["surface"], fg=C["text"],
                     font=(FONT, 12, "bold")).pack(anchor=tk.W)
            tk.Label(info, text=f"{stats['total']} cards · {stats['due']} due",
                     bg=C["surface"], fg=C["text2"], font=(FONT, 9)).pack(anchor=tk.W)

            btns = tk.Frame(row, bg=C["surface"])
            btns.pack(side=tk.RIGHT)
            flat_button(btns, "📖 Study", C["primary"], pady=5,
                        command=lambda n=deck["name"]: self._study(n)).pack(side=tk.LEFT, padx=4)
            flat_button(btns, "🗑 Delete", C["bg"], fg=C["danger"], pady=5,
                        command=lambda i=deck["id"], n=deck["name"]: self._delete(i, n)
                        ).pack(side=tk.LEFT)

    def _study(self, deck_name: str):
        rt: ReviewTab = self.app._tabs["review"]  # type: ignore[assignment]
        self.app.show_tab("review")
        rt.jump_to_deck(deck_name)

    def _delete(self, deck_id: str, name: str):
        stats = self.app.db.deck_stats(deck_id)
        if not messagebox.askyesno(
            "Delete Deck",
            f'Delete "{name}" and all {stats["total"]} card(s)?',
        ):
            return
        self.app.db.delete_deck(deck_id)
        self._refresh()

    def _new_deck_dialog(self):
        dlg = tk.Toplevel(self)
        dlg.title("New Deck")
        dlg.geometry("360x150")
        dlg.configure(bg=C["bg"])
        dlg.transient(self)
        dlg.grab_set()

        tk.Label(dlg, text="Deck Name", bg=C["bg"], fg=C["text2"],
                 font=(FONT, 9, "bold")).pack(anchor=tk.W, padx=16, pady=(16, 4))
        name_var = tk.StringVar()
        e = ttk.Entry(dlg, textvariable=name_var, font=(FONT, 11))
        e.pack(fill=tk.X, padx=16)
        e.focus_set()

        def create():
            name = name_var.get().strip()
            if not name:
                return
            self.app.db.create_deck(name)
            dlg.destroy()
            self._refresh()

        e.bind("<Return>", lambda _: create())
        row = tk.Frame(dlg, bg=C["bg"])
        row.pack(pady=12, padx=16, anchor=tk.W)
        flat_button(row, "Create", C["primary"], pady=5, command=create).pack(side=tk.LEFT)
        flat_button(row, "Cancel", C["bg"], fg=C["text2"],
                    pady=5, command=dlg.destroy).pack(side=tk.LEFT, padx=8)

    def _export(self):
        path = filedialog.asksaveasfilename(
            defaultextension=".json",
            filetypes=[("JSON files", "*.json")],
            initialfile="flashai-backup.json",
        )
        if not path:
            return
        with open(path, "w", encoding="utf-8") as f:
            f.write(self.app.db.export_json())
        messagebox.showinfo("Export", f"Saved to:\n{path}")

    def _import(self):
        path = filedialog.askopenfilename(filetypes=[("JSON files", "*.json")])
        if not path:
            return
        try:
            with open(path, encoding="utf-8") as f:
                self.app.db.import_json(f.read())
            messagebox.showinfo("Import", "Import complete!")
            self._refresh()
        except Exception as exc:
            messagebox.showerror("Import failed", str(exc))


# ── Settings Tab ───────────────────────────────────────────────────────────────
class SettingsTab(BaseTab):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def _build(self):
        card = surface_frame(self, padx=24, pady=24)
        card.pack(fill=tk.X, pady=(0, 14))

        hdr_label(card, "Settings", bg=C["surface"]).pack(anchor=tk.W, pady=(0, 16))

        def row(label: str):
            tk.Label(card, text=label, bg=C["surface"], fg=C["text2"],
                     font=(FONT, 9, "bold")).pack(anchor=tk.W, pady=(10, 3))

        row("Anthropic API Key")
        self._api_var = tk.StringVar()
        ttk.Entry(card, textvariable=self._api_var, show="•", width=52).pack(fill=tk.X)

        row("Claude Model")
        self._model_var = tk.StringVar()
        ttk.Combobox(card, textvariable=self._model_var, state="readonly", values=[
            "claude-haiku-4-5-20251001",
            "claude-sonnet-4-6",
            "claude-opus-4-7",
        ]).pack(fill=tk.X)

        row("New cards per day (per deck)")
        self._npd_var = tk.StringVar(value="20")
        ttk.Entry(card, textvariable=self._npd_var, width=8).pack(anchor=tk.W)

        btn_row = tk.Frame(card, bg=C["surface"])
        btn_row.pack(anchor=tk.W, pady=(14, 0))
        flat_button(btn_row, "Save Settings", C["primary"], command=self._save).pack(side=tk.LEFT)
        self._lbl_saved = tk.Label(btn_row, text="", bg=C["surface"],
                                    fg=C["success"], font=(FONT, 10))
        self._lbl_saved.pack(side=tk.LEFT, padx=10)

        # About section
        about = surface_frame(self, padx=24, pady=20)
        about.pack(fill=tk.X)
        tk.Label(about, text="About SRS Scheduling", bg=C["surface"], fg=C["text"],
                 font=(FONT, 11, "bold")).pack(anchor=tk.W, pady=(0, 8))
        body = (
            "FlashAI uses a simplified SM-2 spaced repetition algorithm.\n\n"
            "Each card tracks an interval (days until next review) and an ease factor "
            "(default 2.5×).\n\n"
            "  Again → interval resets to 1 day; ease −0.20\n"
            "  Hard  → interval × 1.2;            ease −0.15\n"
            "  Good  → interval × ease            (the sweet spot)\n"
            "  Easy  → interval × ease × 1.3;     ease +0.15\n\n"
            f"Data stored at: {DB_PATH}"
        )
        tk.Label(about, text=body, bg=C["surface"], fg=C["text2"],
                 font=(FONT, 10), justify=tk.LEFT, anchor=tk.W).pack(anchor=tk.W)

        if not HAS_PIL:
            tk.Label(about,
                     text="⚠️  Pillow not installed — image support disabled.\n"
                          "   Run:  pip install pillow",
                     bg=C["surface"], fg=C["warn"],
                     font=(FONT, 9), justify=tk.LEFT).pack(anchor=tk.W, pady=(8, 0))
        if not HAS_ANTHROPIC:
            tk.Label(about,
                     text="ℹ️  anthropic SDK not installed — using urllib fallback.\n"
                          "   Run:  pip install anthropic",
                     bg=C["surface"], fg=C["text2"],
                     font=(FONT, 9), justify=tk.LEFT).pack(anchor=tk.W, pady=(4, 0))

    def on_show(self):
        self._api_var.set(  self.app.db.get_setting("api_key",    ""))
        self._model_var.set(self.app.db.get_setting("model",      "claude-sonnet-4-6"))
        self._npd_var.set(  self.app.db.get_setting("new_per_day", "20"))

    def _save(self):
        self.app.db.set_setting("api_key",    self._api_var.get().strip())
        self.app.db.set_setting("model",      self._model_var.get())
        self.app.db.set_setting("new_per_day", self._npd_var.get())
        self._lbl_saved.config(text="✓ Saved")
        self.after(2000, lambda: self._lbl_saved.config(text=""))


# ── Entry point ────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    app = App()
    app.mainloop()
