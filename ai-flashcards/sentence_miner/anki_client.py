"""AnkiConnect REST client."""
from __future__ import annotations

import base64
import json
import urllib.error
import urllib.request
from typing import Any


class AnkiError(Exception):
    pass


_NOTE_TYPE_CSS = (
    ".card { font-family: sans-serif; font-size: 20px; text-align: center; "
    "color: #222; background: #fff; padding: 1em; } "
    ".back-section { margin-top: 0.8em; font-size: 0.9em; color: #555; }"
)

_CARD_FRONT = "{{Word}}<br><br><span style='font-size:0.85em'>{{Sentence}}</span><br><br>{{Audio}}"
_CARD_BACK = (
    "{{FrontSide}}<hr>"
    "<b>{{Definition}}</b>"
    "<div class='back-section'><em>{{Translation}}</em></div>"
)


class AnkiClient:
    def __init__(self, url: str = "http://localhost:8765") -> None:
        self.url = url

    def _invoke(self, action: str, **params: Any) -> Any:
        payload = json.dumps({"action": action, "version": 6, "params": params}).encode()
        try:
            with urllib.request.urlopen(self.url, data=payload, timeout=15) as resp:
                result = json.loads(resp.read())
        except urllib.error.URLError as exc:
            raise AnkiError(f"Cannot reach AnkiConnect at {self.url}: {exc}") from exc
        except (json.JSONDecodeError, ValueError) as exc:
            raise AnkiError(f"AnkiConnect returned non-JSON response: {exc}") from exc
        if result.get("error"):
            raise AnkiError(result["error"])
        if "result" not in result:
            raise AnkiError(f"Unexpected AnkiConnect response (no 'result' key): {result}")
        return result["result"]

    def ping(self) -> int:
        return self._invoke("version")  # type: ignore[return-value]

    def ensure_deck(self, deck_name: str) -> None:
        self._invoke("createDeck", deck=deck_name)

    def ensure_note_type(self, note_type: str) -> None:
        existing: list[str] = self._invoke("modelNames")
        if note_type in existing:
            return
        self._invoke(
            "createModel",
            modelName=note_type,
            inOrderFields=["Word", "Sentence", "Audio", "Definition", "Translation"],
            css=_NOTE_TYPE_CSS,
            cardTemplates=[
                {
                    "Name": "Sentence Mining",
                    "Front": _CARD_FRONT,
                    "Back": _CARD_BACK,
                }
            ],
        )

    def store_media(self, filename: str, data: bytes) -> str:
        encoded = base64.b64encode(data).decode()
        return self._invoke("storeMediaFile", filename=filename, data=encoded)  # type: ignore[return-value]

    def add_note(
        self,
        deck: str,
        note_type: str,
        word: str,
        sentence: str,
        definition: str,
        translation: str,
        audio_filename: str,
    ) -> int | None:
        """Add a note; returns the new note ID or None if it was a duplicate."""
        try:
            return self._invoke(  # type: ignore[return-value]
                "addNote",
                note={
                    "deckName": deck,
                    "modelName": note_type,
                    "fields": {
                        "Word": word,
                        "Sentence": sentence,
                        "Audio": f"[sound:{audio_filename}]",
                        "Definition": definition,
                        "Translation": translation,
                    },
                    "options": {"allowDuplicate": False},
                    "tags": ["sentence-miner"],
                },
            )
        except AnkiError as exc:
            if "duplicate" in str(exc).lower():
                return None
            raise
