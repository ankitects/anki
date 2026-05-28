"""Simple tkinter GUI for the YouTube sentence miner."""
from __future__ import annotations

import logging
import os
import queue
import shutil
import sys
import threading
import tkinter as tk
from tkinter import messagebox, scrolledtext, ttk
from typing import Any

try:
    import keyring  # type: ignore[import-untyped]
    _HAS_KEYRING = True
except ImportError:
    _HAS_KEYRING = False

_KEYRING_SERVICE = "sentence-miner"
_KEYRING_USER = "anthropic-api-key"

C: dict[str, str] = {
    "bg":      "#1e1e1e",
    "surface": "#2d2d2d",
    "text":    "#f0f0f0",
    "muted":   "#999999",
    "accent":  "#5ba0d0",
    "success": "#4caf82",
    "warn":    "#e0a040",
    "danger":  "#c0392b",
    "border":  "#444444",
    "entry":   "#3a3a3a",
}


# ── Styling helpers ────────────────────────────────────────────────────────────

def _label(parent: tk.Widget, text: str, **kw: Any) -> tk.Label:
    return tk.Label(parent, text=text, bg=C["surface"], fg=C["text"],
                    font=("Helvetica", 10), **kw)


def _muted_label(parent: tk.Widget, text: str, **kw: Any) -> tk.Label:
    return tk.Label(parent, text=text, bg=C["surface"], fg=C["muted"],
                    font=("Helvetica", 9), **kw)


def _entry(parent: tk.Widget, textvariable: tk.StringVar, width: int = 32, **kw: Any) -> tk.Entry:
    return tk.Entry(parent, textvariable=textvariable, width=width,
                    bg=C["entry"], fg=C["text"], insertbackground=C["text"],
                    relief="flat", bd=4, font=("Helvetica", 10), **kw)


def _button(parent: tk.Widget, text: str, command: Any, accent: bool = False, **kw: Any) -> tk.Button:
    bg = C["accent"] if accent else C["border"]
    fg = "#ffffff" if accent else C["text"]
    return tk.Button(parent, text=text, command=command,
                     bg=bg, fg=fg, activebackground=C["accent"],
                     activeforeground="#ffffff", relief="flat",
                     padx=10, pady=4, cursor="hand2",
                     font=("Helvetica", 10, "bold" if accent else "normal"), **kw)


def _section(parent: tk.Widget, title: str) -> tk.LabelFrame:
    return tk.LabelFrame(parent, text=f"  {title}  ",
                         bg=C["surface"], fg=C["muted"],
                         font=("Helvetica", 9), relief="groove",
                         bd=1, labelanchor="nw", padx=8, pady=6)


# ── Logging handler that feeds a queue ────────────────────────────────────────

class _QueueHandler(logging.Handler):
    def __init__(self, q: queue.Queue[str]) -> None:
        super().__init__()
        self._q = q

    def emit(self, record: logging.LogRecord) -> None:
        msg = self.format(record)
        self._q.put_nowait(msg)


# ── Main window ───────────────────────────────────────────────────────────────

class MinerApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("YouTube Sentence Miner")
        self.configure(bg=C["bg"])
        self.resizable(True, True)
        self.minsize(600, 520)

        self._running = False
        self._log_queue: queue.Queue[str] = queue.Queue()
        self._setup_logging()
        self._build()
        self._load_keychain()
        self._poll_log()

    def _setup_logging(self) -> None:
        handler = _QueueHandler(self._log_queue)
        handler.setFormatter(logging.Formatter("%(asctime)s  %(levelname)-7s  %(message)s",
                                               datefmt="%H:%M:%S"))
        root_log = logging.getLogger()
        root_log.setLevel(logging.INFO)
        root_log.addHandler(handler)

    # ── Widget construction ────────────────────────────────────────────────────

    def _build(self) -> None:
        outer = tk.Frame(self, bg=C["bg"], padx=12, pady=12)
        outer.pack(fill="both", expand=True)

        # ── Input section ──────────────────────────────────────────────────────
        inp = _section(outer, "Video")
        inp.pack(fill="x", pady=(0, 8))

        self._url = tk.StringVar()
        self._deck = tk.StringVar(value="Sentence Mining")
        self._language = tk.StringVar(value="en")
        self._target_lang = tk.StringVar()
        self._limit = tk.StringVar(value="20")
        self._window = tk.StringVar(value="30")
        self._model = tk.StringVar(value="claude-sonnet-4-6")
        self._anki_url = tk.StringVar(value="http://localhost:8765")

        rows = [
            ("YouTube URL", self._url, 48, False),
            ("Deck",        self._deck, 28, False),
        ]
        for r, (lbl, var, w, _) in enumerate(rows):
            _label(inp, lbl + ":").grid(row=r, column=0, sticky="e", padx=(0, 6), pady=3)
            _entry(inp, var, width=w).grid(row=r, column=1, columnspan=3, sticky="w", pady=3)

        # Two-column row: Language + Target language
        _label(inp, "Transcript language:").grid(row=2, column=0, sticky="e", padx=(0, 6), pady=3)
        _entry(inp, self._language, width=6).grid(row=2, column=1, sticky="w", pady=3)
        _label(inp, "Target language:").grid(row=2, column=2, sticky="e", padx=(12, 6), pady=3)
        _entry(inp, self._target_lang, width=14).grid(row=2, column=3, sticky="w", pady=3)

        # Two-column row: Limit + Window
        _label(inp, "Card limit (0=∞):").grid(row=3, column=0, sticky="e", padx=(0, 6), pady=3)
        _entry(inp, self._limit, width=6).grid(row=3, column=1, sticky="w", pady=3)
        _label(inp, "Window (s):").grid(row=3, column=2, sticky="e", padx=(12, 6), pady=3)
        _entry(inp, self._window, width=6).grid(row=3, column=3, sticky="w", pady=3)

        # Advanced row: Model + AnkiConnect
        _label(inp, "Model:").grid(row=4, column=0, sticky="e", padx=(0, 6), pady=3)
        _entry(inp, self._model, width=22).grid(row=4, column=1, sticky="w", pady=3)
        _label(inp, "AnkiConnect:").grid(row=4, column=2, sticky="e", padx=(12, 6), pady=3)
        _entry(inp, self._anki_url, width=22).grid(row=4, column=3, sticky="w", pady=3)

        # ── API key section ────────────────────────────────────────────────────
        key_sec = _section(outer, "API Key")
        key_sec.pack(fill="x", pady=(0, 8))

        self._api_key = tk.StringVar()
        self._show_key = tk.BooleanVar(value=False)

        key_row = tk.Frame(key_sec, bg=C["surface"])
        key_row.pack(fill="x")

        self._key_entry = _entry(key_row, self._api_key, width=44, show="●")
        self._key_entry.pack(side="left", padx=(0, 6))

        _button(key_row, "Show", self._toggle_show_key).pack(side="left", padx=2)
        _button(key_row, "Save to keychain", self._save_key).pack(side="left", padx=2)
        _button(key_row, "Clear", self._clear_key, ).pack(side="left", padx=2)

        if not _HAS_KEYRING:
            _muted_label(key_sec, "keyring not installed — key won't persist between sessions").pack(anchor="w", pady=(4, 0))

        # ── Mine button ────────────────────────────────────────────────────────
        btn_row = tk.Frame(outer, bg=C["bg"])
        btn_row.pack(fill="x", pady=(0, 8))

        self._mine_btn = _button(btn_row, "⛏  Mine Sentences", self._start_mining,
                                 accent=True, pady=8, font=("Helvetica", 12, "bold"))
        self._mine_btn.pack(fill="x")

        # Progress bar (hidden until running)
        self._progress = ttk.Progressbar(outer, mode="determinate")
        self._progress.pack(fill="x", pady=(0, 6))
        self._progress.pack_forget()

        # ── Log output ─────────────────────────────────────────────────────────
        log_sec = _section(outer, "Log")
        log_sec.pack(fill="both", expand=True)

        self._log_text = scrolledtext.ScrolledText(
            log_sec, height=12, bg=C["bg"], fg=C["text"],
            font=("Courier", 9), relief="flat", state="disabled",
            insertbackground=C["text"],
        )
        self._log_text.pack(fill="both", expand=True)
        self._log_text.tag_config("warn",    foreground=C["warn"])
        self._log_text.tag_config("error",   foreground=C["danger"])
        self._log_text.tag_config("success", foreground=C["success"])

    # ── Keychain helpers ───────────────────────────────────────────────────────

    def _load_keychain(self) -> None:
        if not _HAS_KEYRING:
            return
        try:
            stored = keyring.get_password(_KEYRING_SERVICE, _KEYRING_USER)
            if stored:
                self._api_key.set(stored)
                self._append_log("API key loaded from keychain.", tag="success")
        except Exception:
            pass

    def _save_key(self) -> None:
        key = self._api_key.get().strip()
        if not key:
            messagebox.showwarning("Empty key", "Enter an API key first.")
            return
        if not _HAS_KEYRING:
            messagebox.showinfo("keyring unavailable",
                                "Install keyring (pip install keyring) to persist the key.")
            return
        try:
            keyring.set_password(_KEYRING_SERVICE, _KEYRING_USER, key)
            self._append_log("API key saved to keychain.", tag="success")
        except Exception as exc:
            messagebox.showerror("Keychain error", str(exc))

    def _clear_key(self) -> None:
        self._api_key.set("")
        if not _HAS_KEYRING:
            return
        try:
            keyring.delete_password(_KEYRING_SERVICE, _KEYRING_USER)
            self._append_log("API key removed from keychain.", tag="warn")
        except Exception:
            pass

    def _toggle_show_key(self) -> None:
        self._show_key.set(not self._show_key.get())
        self._key_entry.config(show="" if self._show_key.get() else "●")

    # ── Log output ─────────────────────────────────────────────────────────────

    def _append_log(self, msg: str, tag: str = "") -> None:
        self._log_text.config(state="normal")
        self._log_text.insert("end", msg + "\n", tag or ())
        self._log_text.see("end")
        self._log_text.config(state="disabled")

    def _poll_log(self) -> None:
        try:
            while True:
                msg = self._log_queue.get_nowait()
                low = msg.lower()
                if "error" in low or "failed" in low:
                    tag = "error"
                elif "warn" in low or "skip" in low:
                    tag = "warn"
                elif "done" in low or "added" in low:
                    tag = "success"
                else:
                    tag = ""
                self._append_log(msg, tag)
        except queue.Empty:
            pass
        self.after(100, self._poll_log)

    # ── Mining ─────────────────────────────────────────────────────────────────

    def _validate_inputs(self) -> dict[str, Any] | None:
        url = self._url.get().strip()
        if not url:
            messagebox.showwarning("Missing URL", "Paste a YouTube URL.")
            return None

        api_key = self._api_key.get().strip()
        if not api_key:
            api_key = os.environ.get("ANTHROPIC_API_KEY", "")
        if not api_key:
            messagebox.showwarning(
                "Missing API key",
                "Enter your Anthropic API key (or set ANTHROPIC_API_KEY).",
            )
            return None

        try:
            limit = int(self._limit.get() or "0")
            window = float(self._window.get() or "30")
        except ValueError:
            messagebox.showwarning("Invalid input", "Limit must be an integer; window must be a number.")
            return None

        return {
            "url": url,
            "deck": self._deck.get().strip() or "Sentence Mining",
            "language": self._language.get().strip() or "en",
            "target_language": self._target_lang.get().strip(),
            "api_key": api_key,
            "anki_url": self._anki_url.get().strip() or "http://localhost:8765",
            "model": self._model.get().strip() or "claude-sonnet-4-6",
            "limit": limit,
            "window": window,
        }

    def _start_mining(self) -> None:
        if self._running:
            return
        params = self._validate_inputs()
        if params is None:
            return

        missing = [t for t in ("ffmpeg", "yt-dlp") if not shutil.which(t)]
        if missing:
            messagebox.showerror(
                "Missing tools",
                f"Required tools not found in PATH: {', '.join(missing)}\n\n"
                "Install ffmpeg and yt-dlp, then try again.",
            )
            return

        self._running = True
        self._mine_btn.config(state="disabled", text="Mining…")
        self._progress["value"] = 0
        self._progress.pack(fill="x", pady=(0, 6))
        self._append_log("─" * 60)

        threading.Thread(target=self._mining_thread, args=(params,), daemon=True).start()

    def _mining_thread(self, params: dict[str, Any]) -> None:
        from main import run_mining

        def on_progress(current: int, total: int) -> None:
            pct = int(current / total * 100) if total else 0
            self.after(0, lambda p=pct: self._progress.config(value=p))

        try:
            added, skipped = run_mining(
                url=params["url"],
                deck=params["deck"],
                language=params["language"],
                target_language=params["target_language"],
                api_key=params["api_key"],
                anki_url=params["anki_url"],
                model=params["model"],
                limit=params["limit"],
                window=params["window"],
                on_progress=on_progress,
            )
            self.after(0, self._on_done, added, skipped, None)
        except Exception as exc:
            self.after(0, self._on_done, 0, 0, exc)

    def _on_done(self, added: int, skipped: int, error: Exception | None) -> None:
        self._running = False
        self._mine_btn.config(state="normal", text="⛏  Mine Sentences")
        self._progress.pack_forget()

        if error:
            self._append_log(f"ERROR: {error}", tag="error")
            messagebox.showerror("Mining failed", str(error))
        else:
            self._append_log(
                f"Finished — {added} card(s) added, {skipped} skipped.", tag="success"
            )


def main() -> None:
    app = MinerApp()
    app.mainloop()


if __name__ == "__main__":
    main()
