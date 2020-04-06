# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This file contains the Python portion of the template rendering code.

Templates can have filters applied to field replacements. The Rust template
rendering code will apply any built in filters, and stop at the first
unrecognized filter. The remaining filters are returned to Python,
and applied using the hook system. For example,
{{myfilter:hint:text:Field}} will apply the built in text and hint filters,
and then attempt to apply myfilter. If no add-ons have provided the filter,
the filter is skipped.

Add-ons can register a filter with the following code:

from anki import hooks
hooks.field_filter.append(myfunc)

This will call myfunc, passing the field text in as the first argument.
Your function should decide if it wants to modify the text by checking
the filter_name argument, and then return the text whether it has been
modified or not.

A Python implementation of the standard filters is currently available in the
template_legacy.py file, using the legacy addHook() system.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

import anki
from anki import hooks
from anki.cards import Card
from anki.decks import DeckManager
from anki.models import NoteType
from anki.notes import Note
from anki.rsbackend import TemplateReplacementList
from anki.sound import AVTag

CARD_BLANK_HELP = (
    "https://anki.tenderapp.com/kb/card-appearance/the-front-of-this-card-is-blank"
)


class TemplateRenderContext:
    """Holds information for the duration of one card render.

    This may fetch information lazily in the future, so please avoid
    using the _private fields directly."""

    def __init__(
        self,
        col: anki.storage._Collection,
        card: Card,
        note: Note,
        fields: Dict[str, str],
        qfmt: str,
        afmt: str,
    ) -> None:
        self._col = col
        self._card = card
        self._note = note
        self._fields = fields
        self._qfmt = qfmt
        self._afmt = afmt

        # if you need to store extra state to share amongst rendering
        # hooks, you can insert it into this dictionary
        self.extra_state: Dict[str, Any] = {}

    def col(self) -> anki.storage._Collection:
        return self._col

    def fields(self) -> Dict[str, str]:
        return self._fields

    def card(self) -> Card:
        """Returns the card being rendered.

        Be careful not to call .q() or .a() on the card, or you'll create an
        infinite loop."""
        return self._card

    def note(self) -> Note:
        return self._note

    def note_type(self) -> NoteType:
        return self.card().note_type()

    def qfmt(self) -> str:
        return self._qfmt

    def afmt(self) -> str:
        return self._afmt


@dataclass
class TemplateRenderOutput:
    "Stores the rendered templates and extracted AV tags."
    question_text: str
    answer_text: str
    question_av_tags: List[AVTag]
    answer_av_tags: List[AVTag]


def render_card(
    col: anki.storage._Collection, card: Card, note: Note, browser: bool
) -> TemplateRenderOutput:
    "Render a card."
    # collect data
    fields = fields_for_rendering(col, card, note)
    qfmt, afmt = templates_for_card(card, browser)
    ctx = TemplateRenderContext(
        col=col, card=card, note=note, fields=fields, qfmt=qfmt, afmt=afmt
    )

    # render
    try:
        output = render_card_from_context(ctx)
    except anki.rsbackend.TemplateError as e:
        output = TemplateRenderOutput(
            question_text=str(e),
            answer_text=str(e),
            question_av_tags=[],
            answer_av_tags=[],
        )

    hooks.card_did_render(output, ctx)

    return output


def templates_for_card(card: Card, browser: bool) -> Tuple[str, str]:
    template = card.template()
    if browser:
        q, a = template.get("bqfmt"), template.get("bafmt")
    else:
        q, a = None, None
    q = q or template.get("qfmt")
    a = a or template.get("afmt")
    return q, a  # type: ignore


def fields_for_rendering(
    col: anki.storage._Collection, card: Card, note: Note
) -> Dict[str, str]:
    # fields from note
    fields = dict(note.items())

    # add special fields
    fields["Tags"] = note.stringTags().strip()
    fields["Type"] = card.note_type()["name"]
    fields["Deck"] = col.decks.name(card.odid or card.did)
    fields["Subdeck"] = DeckManager.basename(fields["Deck"])
    fields["Card"] = card.template()["name"]
    flag = card.userFlag()
    fields["CardFlag"] = flag and f"flag{flag}" or ""
    fields["c%d" % (card.ord + 1)] = "1"

    return fields


def render_card_from_context(ctx: TemplateRenderContext) -> TemplateRenderOutput:
    """Renders the provided templates, returning rendered output.

    Will raise if the template is invalid."""
    col = ctx.col()

    (qnodes, anodes) = col.backend.render_card(
        ctx.qfmt(), ctx.afmt(), ctx.fields(), ctx.card().ord
    )

    qtext = apply_custom_filters(qnodes, ctx, front_side=None)
    qtext, q_avtags = col.backend.extract_av_tags(qtext, True)

    atext = apply_custom_filters(anodes, ctx, front_side=qtext)
    atext, a_avtags = col.backend.extract_av_tags(atext, False)

    return TemplateRenderOutput(
        question_text=qtext,
        answer_text=atext,
        question_av_tags=q_avtags,
        answer_av_tags=a_avtags,
    )


def apply_custom_filters(
    rendered: TemplateReplacementList,
    ctx: TemplateRenderContext,
    front_side: Optional[str],
) -> str:
    "Complete rendering by applying any pending custom filters."
    # template already fully rendered?
    if len(rendered) == 1 and isinstance(rendered[0], str):
        return rendered[0]

    res = ""
    for node in rendered:
        if isinstance(node, str):
            res += node
        else:
            # do we need to inject in FrontSide?
            if node.field_name == "FrontSide" and front_side is not None:
                node.current_text = front_side

            field_text = node.current_text
            for filter_name in node.filters:
                field_text = hooks.field_filter(
                    field_text, node.field_name, filter_name, ctx
                )
                # legacy hook - the second and fifth argument are no longer used.
                field_text = anki.hooks.runFilter(
                    "fmod_" + filter_name,
                    field_text,
                    "",
                    ctx.fields(),
                    node.field_name,
                    "",
                )

            res += field_text
    return res
