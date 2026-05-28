"""Orchestrator: ties CLI, media, processor, and Anki client together."""
from __future__ import annotations

import logging
import os
import shutil
import sys
import tempfile
from pathlib import Path
from typing import Callable

from anki_client import AnkiClient, AnkiError
from cli import check_dependencies, parse_args, ping_anki
from media_handler import download_audio, slice_audio
from processor import extract_video_id, fetch_transcript, process_transcript

log = logging.getLogger(__name__)


def run_mining(
    url: str,
    deck: str = "Sentence Mining",
    language: str = "en",
    target_language: str = "",
    api_key: str = "",
    anki_url: str = "http://localhost:8765",
    model: str = "claude-sonnet-4-6",
    limit: int = 0,
    note_type: str = "FlashAI Mined",
    window: float = 30.0,
    on_progress: Callable[[int, int], None] | None = None,
) -> tuple[int, int]:
    """Core mining logic; returns (added, skipped).

    *on_progress(current, total)* is called after each card attempt so callers
    can update a progress bar.  It is invoked from the calling thread.
    """
    video_id = extract_video_id(url)
    log.info("Video ID: %s", video_id)

    log.info("Fetching transcript (language=%s) …", language)
    segments = fetch_transcript(video_id, language)
    log.info(
        "Transcript: %d segments, ~%.0fs total",
        len(segments),
        sum(s.get("duration", 0) for s in segments),
    )

    log.info("Extracting vocabulary with %s …", model)
    cards = process_transcript(
        segments,
        api_key=api_key,
        model=model,
        target_language=target_language,
        window=window,
        limit=limit,
    )

    if not cards:
        log.info("No vocabulary cards found — nothing to add to Anki.")
        return 0, 0

    log.info("Found %d card(s) to create.", len(cards))

    tmp_dir = Path(tempfile.mkdtemp(prefix="sentence_miner_"))
    try:
        log.info("Downloading audio …")
        audio_path = download_audio(url, tmp_dir)
        log.info("Audio ready: %s", audio_path.name)

        anki = AnkiClient(anki_url)
        anki.ensure_deck(deck)
        anki.ensure_note_type(note_type)

        added = 0
        skipped = 0

        for i, card in enumerate(cards, 1):
            word: str = card["target_word"]
            sentence: str = card["reconstructed_sentence"]
            definition: str = card["definition"]
            translation: str = card["english_translation"]
            start: float = max(float(card["start_time"]), 0.0)  # clamp LLM-supplied value
            duration: float = max(float(card["duration"]), 0.5)

            log.info("[%d/%d] %r @ %.2fs + %.2fs", i, len(cards), word, start, duration)

            safe_word = "".join(c if c.isalnum() or c in "-_" else "_" for c in word)
            clip_filename = f"sm_{video_id}_{safe_word}_{int(start * 1000)}.m4a"
            clip_path = tmp_dir / clip_filename

            try:
                slice_audio(audio_path, start, duration, clip_path)
            except RuntimeError as exc:
                log.warning("  Skipping (audio slice failed): %s", exc)
                skipped += 1
                if on_progress:
                    on_progress(i, len(cards))
                continue

            audio_data = clip_path.read_bytes()
            try:
                anki.store_media(clip_filename, audio_data)
            except AnkiError as exc:
                log.warning("  Skipping (media store failed): %s", exc)
                skipped += 1
                if on_progress:
                    on_progress(i, len(cards))
                continue

            try:
                note_id = anki.add_note(
                    deck=deck,
                    note_type=note_type,
                    word=word,
                    sentence=sentence,
                    definition=definition,
                    translation=translation,
                    audio_filename=clip_filename,
                )
            except AnkiError as exc:
                log.warning("  Skipping (addNote failed): %s", exc)
                skipped += 1
                if on_progress:
                    on_progress(i, len(cards))
                continue

            if note_id is None:
                log.info("  Duplicate — skipped.")
                skipped += 1
            else:
                log.info("  Added note #%d", note_id)
                added += 1

            if on_progress:
                on_progress(i, len(cards))

    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)

    log.info("Done.  %d added, %d skipped.", added, skipped)
    return added, skipped


def run() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s  %(levelname)-7s  %(message)s",
        datefmt="%H:%M:%S",
    )

    args = parse_args()
    check_dependencies()
    ping_anki(args.anki_url)

    api_key = args.api_key or os.environ.get("ANTHROPIC_API_KEY", "")
    if not api_key:
        print(
            "[ERROR] No Anthropic API key supplied.\n"
            "  Pass --api-key KEY  or  set ANTHROPIC_API_KEY in your environment.",
            file=sys.stderr,
        )
        return 1

    try:
        run_mining(
            url=args.url,
            deck=args.deck,
            language=args.language,
            target_language=args.target_language,
            api_key=api_key,
            anki_url=args.anki_url,
            model=args.model,
            limit=args.limit,
            note_type=args.note_type,
            window=args.window,
        )
    except Exception as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(run())
