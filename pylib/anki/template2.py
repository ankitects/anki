# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This file contains some code related to templates that is not directly
connected to pystache. It may be renamed in the future.
"""

from __future__ import annotations

import re
from typing import Dict, Tuple

import anki
from anki.sound import stripSounds


def renderFromFieldMap(
    qfmt: str, afmt: str, fields: Dict[str, str], card_ord: int
) -> Tuple[str, str]:
    "Renders the provided templates, returning rendered q & a text."
    # question
    format = re.sub("{{(?!type:)(.*?)cloze:", r"{{\1cq-%d:" % (card_ord + 1), qfmt)
    format = format.replace("<%cloze:", "<%%cq:%d:" % (card_ord + 1))
    qtext = anki.template.render(format, fields)

    # answer
    format = re.sub("{{(.*?)cloze:", r"{{\1ca-%d:" % (card_ord + 1), afmt)
    format = format.replace("<%cloze:", "<%%ca:%d:" % (card_ord + 1))
    fields["FrontSide"] = stripSounds(qtext)
    atext = anki.template.render(format, fields)

    return qtext, atext
