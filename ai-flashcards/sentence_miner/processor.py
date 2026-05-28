"""Transcript fetching and LLM-based vocabulary extraction."""
from __future__ import annotations

import json
import logging
import re
from typing import Any

log = logging.getLogger(__name__)

# Strict JSON schema expected back from the LLM.
CARD_SCHEMA = """{
  "cards": [
    {
      "target_word": "string",
      "definition": "string",
      "reconstructed_sentence": "string",
      "english_translation": "string",
      "start_time": 0.0,
      "duration": 0.0
    }
  ]
}"""

_SYSTEM_PROMPT = """\
You are a language-learning assistant that creates flashcards from video transcripts.

Given a transcript excerpt (with per-segment start times and durations), identify
interesting or challenging vocabulary words. For each word produce one flashcard using
the JSON schema below. Return ONLY the JSON object — no markdown, no extra commentary.

Rules:
- Pick words that are useful to learn; skip very common words (the, is, a …).
- reconstructed_sentence must be a natural sentence from the transcript containing the word.
- start_time and duration must match the transcript segment(s) that contain the sentence.
- If a sentence spans multiple segments, set start_time to the earliest segment start and
  duration to cover all of them (add a 0.3 s buffer to each end, clamped to 0).
- Return an empty cards array if there is nothing worth learning.

Schema:
""" + CARD_SCHEMA


def _build_user_message(segments: list[dict[str, Any]], target_language: str) -> str:
    lang_hint = f" (language: {target_language})" if target_language else ""
    lines = [f"Transcript excerpt{lang_hint}:\n"]
    for seg in segments:
        start = seg["start"]
        end = start + seg["duration"]
        lines.append(f"[{start:.2f}–{end:.2f}s] {seg['text']}")
    return "\n".join(lines)


def _call_llm(
    user_message: str,
    api_key: str,
    model: str,
) -> list[dict[str, Any]]:
    """Send one transcript window to the LLM and return the parsed card list."""
    try:
        import anthropic  # type: ignore[import-untyped]

        client = anthropic.Anthropic(api_key=api_key)
        resp = client.messages.create(
            model=model,
            max_tokens=2048,
            system=_SYSTEM_PROMPT,
            messages=[{"role": "user", "content": user_message}],
        )
        raw = resp.content[0].text
    except ImportError:
        raw = _call_llm_urllib(user_message, api_key, model)

    return _parse_response(raw)


def _call_llm_urllib(user_message: str, api_key: str, model: str) -> str:
    """urllib fallback for environments without the anthropic SDK."""
    import urllib.error
    import urllib.request

    payload = json.dumps(
        {
            "model": model,
            "max_tokens": 2048,
            "system": _SYSTEM_PROMPT,
            "messages": [{"role": "user", "content": user_message}],
        }
    ).encode()
    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=payload,
        headers={
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read())
    except urllib.error.HTTPError as exc:
        try:
            err_body = json.loads(exc.read())
            msg = err_body.get("error", {}).get("message", str(exc))
        except Exception:
            msg = str(exc)
        raise RuntimeError(f"Anthropic API error: {msg}") from exc
    return data["content"][0]["text"]


def _parse_response(raw: str) -> list[dict[str, Any]]:
    """Extract and parse the JSON object from the LLM response."""
    # Search for the outermost JSON object regardless of surrounding prose or fences.
    m = re.search(r"\{.*\}", raw, re.DOTALL)
    cleaned = m.group(0).strip() if m else raw.strip()
    try:
        obj = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        log.warning("LLM returned non-JSON: %s … (%s)", raw[:120], exc)
        return []
    cards = obj.get("cards", [])
    valid = []
    required = {"target_word", "definition", "reconstructed_sentence", "english_translation", "start_time", "duration"}
    for card in cards:
        if required.issubset(card.keys()):
            valid.append(card)
        else:
            log.warning("Skipping malformed card: %s", card)
    return valid


def extract_video_id(url: str) -> str:
    """Return the 11-character YouTube video ID from any supported URL format."""
    m = re.search(r"(?:v=|youtu\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})", url)
    if not m:
        raise ValueError(f"Could not extract video ID from URL: {url}")
    return m.group(1)


def fetch_transcript(video_id: str, language: str) -> list[dict[str, Any]]:
    """Return the list of transcript segments from youtube-transcript-api."""
    try:
        from youtube_transcript_api import YouTubeTranscriptApi  # type: ignore[import-untyped]
    except ImportError:
        raise RuntimeError(
            "youtube-transcript-api is not installed. "
            "Run: pip install youtube-transcript-api"
        )

    try:
        return YouTubeTranscriptApi.get_transcript(video_id, languages=[language])
    except Exception as exc:
        raise RuntimeError(
            f"Could not fetch transcript for {video_id} (language={language}): {exc}"
        ) from exc


def group_segments(
    segments: list[dict[str, Any]], window: float = 30.0
) -> list[list[dict[str, Any]]]:
    """Split *segments* into windows of approximately *window* seconds each."""
    groups: list[list[dict[str, Any]]] = []
    current: list[dict[str, Any]] = []
    current_duration = 0.0

    for seg in segments:
        current.append(seg)
        current_duration += seg.get("duration", 0.0)
        if current_duration >= window:
            groups.append(current)
            current = []
            current_duration = 0.0

    if current:
        groups.append(current)
    return groups


def process_transcript(
    segments: list[dict[str, Any]],
    api_key: str,
    model: str,
    target_language: str,
    window: float = 30.0,
    limit: int = 0,
) -> list[dict[str, Any]]:
    """Run all transcript windows through the LLM and return the aggregated card list."""
    groups = group_segments(segments, window)
    all_cards: list[dict[str, Any]] = []

    for i, group in enumerate(groups, 1):
        log.info("Processing window %d/%d …", i, len(groups))
        user_msg = _build_user_message(group, target_language)
        cards = _call_llm(user_msg, api_key, model)
        log.info("  → %d card(s) found", len(cards))
        all_cards.extend(cards)
        if limit and len(all_cards) >= limit:
            break

    if limit:
        all_cards = all_cards[:limit]
    return all_cards
