# /// script
# requires-python = ">=3.12"
# dependencies = ["pydantic>=2"]
# ///
"""Span grounding: the layer that makes generated cards traceable and makes
prompt injection structurally ineffective.

Deliberately free of any model-SDK dependency. The defence has to be verifiable
without touching the API, because it is what holds when the prompting layer
fails — a defence you can only test by asking the model is not a defence.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from pydantic import BaseModel, Field

# A span shorter than this can occur in the source by coincidence, so matching
# it proves nothing about where the card came from.
MIN_CITATION_CHARS = 20


class Card(BaseModel):
    """One generated flashcard."""

    front: str = Field(description="The question. A single fact, asked directly.")
    back: str = Field(description="The answer. As short as the fact allows.")
    topic: str = Field(
        description="AAMC content-outline category, e.g. '1A' or '3B'. "
        "Use only categories present in the source."
    )
    citation: str = Field(
        description="A span copied VERBATIM from the source document that "
        "supports this card. Must be reproduced character for character. "
        "Do not paraphrase, summarise, or repair the text."
    )


class CardBatch(BaseModel):
    cards: list[Card]


@dataclass
class GroundedCard:
    card: Card
    source_name: str
    offset: int


@dataclass
class RejectedCard:
    card: Card
    reason: str


def normalise(text: str) -> str:
    """Fold away differences that are not meaningful for grounding.

    Models reproduce a span's words reliably but not always its whitespace,
    quote style, or dash width — especially across a line break in the source.
    Normalising those keeps verification strict about *content* while
    tolerating typography. Wording itself must still match exactly.
    """
    text = unicodedata.normalize("NFKC", text)
    text = text.replace("’", "'").replace("‘", "'")
    text = text.replace("“", '"').replace("”", '"')
    text = text.replace("—", "-").replace("–", "-")
    return re.sub(r"\s+", " ", text).strip().lower()


def verify_grounding(
    cards: list[Card], source_text: str, source_name: str
) -> tuple[list[GroundedCard], list[RejectedCard]]:
    """Keep only cards whose citation genuinely appears in the source.

    This is the traceability guarantee and the injection defence in one step.
    A hijacked generation produces text that is not in the document, so it
    fails here regardless of what the attacking text said or how it was
    hidden. We never have to detect the attack — only to require evidence.
    """
    haystack = normalise(source_text)
    kept: list[GroundedCard] = []
    rejected: list[RejectedCard] = []

    for card in cards:
        needle = normalise(card.citation)
        if len(needle) < MIN_CITATION_CHARS:
            rejected.append(RejectedCard(card, "citation too short to verify"))
            continue
        offset = haystack.find(needle)
        if offset == -1:
            rejected.append(RejectedCard(card, "citation not found in source"))
            continue
        kept.append(GroundedCard(card, source_name, offset))

    return kept, rejected
