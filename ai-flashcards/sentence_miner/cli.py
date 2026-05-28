"""Argument parsing and pre-flight checks."""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import urllib.error
import urllib.request


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="sentence-miner",
        description="Mine vocabulary from YouTube videos and create Anki flashcards",
    )
    parser.add_argument("url", help="YouTube video URL")
    parser.add_argument(
        "--deck",
        default="Sentence Mining",
        help="Anki deck name (default: Sentence Mining)",
    )
    parser.add_argument(
        "--language",
        default="en",
        help="Transcript language code, e.g. en, es, ja (default: en)",
    )
    parser.add_argument(
        "--target-language",
        default="",
        help="Human-readable target language name for the LLM prompt, e.g. 'Spanish'",
    )
    parser.add_argument(
        "--api-key",
        default="",
        help="Anthropic API key (defaults to ANTHROPIC_API_KEY env var)",
    )
    parser.add_argument(
        "--anki-url",
        default="http://localhost:8765",
        help="AnkiConnect base URL (default: http://localhost:8765)",
    )
    parser.add_argument(
        "--model",
        default="claude-sonnet-4-6",
        help="Claude model to use (default: claude-sonnet-4-6)",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Maximum number of cards to create per run; 0 = unlimited",
    )
    parser.add_argument(
        "--note-type",
        default="FlashAI Mined",
        help="Anki note type name (created if absent)",
    )
    parser.add_argument(
        "--window",
        type=float,
        default=30.0,
        help="Transcript window size in seconds sent to the LLM at once (default: 30)",
    )
    return parser.parse_args()


def check_dependencies() -> None:
    """Abort with a helpful message if ffmpeg or yt-dlp are missing."""
    missing = [tool for tool in ("ffmpeg", "yt-dlp") if not shutil.which(tool)]
    if not missing:
        return
    print(f"[ERROR] Missing required tools: {', '.join(missing)}", file=sys.stderr)
    hints = {
        "ffmpeg": "  ffmpeg : https://ffmpeg.org/download.html  (or: apt install ffmpeg)",
        "yt-dlp": "  yt-dlp : pip install yt-dlp",
    }
    for tool in missing:
        print(hints[tool], file=sys.stderr)
    sys.exit(1)


def ping_anki(anki_url: str) -> None:
    """Abort with a helpful message if AnkiConnect is unreachable."""
    payload = json.dumps({"action": "version", "version": 6}).encode()
    try:
        with urllib.request.urlopen(anki_url, data=payload, timeout=5) as resp:
            result = json.loads(resp.read())
        if result.get("error"):
            raise RuntimeError(result["error"])
    except (urllib.error.URLError, OSError) as exc:
        print(f"[ERROR] Cannot reach AnkiConnect at {anki_url}", file=sys.stderr)
        print(
            "  Make sure Anki is open and the AnkiConnect add-on is installed.",
            file=sys.stderr,
        )
        print(f"  Details: {exc}", file=sys.stderr)
        sys.exit(1)
