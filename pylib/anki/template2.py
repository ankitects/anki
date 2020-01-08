# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This file contains some code related to templates that is not directly
connected to pystache. It may be renamed in the future.
"""

from __future__ import annotations

import re
from typing import Any, Callable, Dict, Tuple

import anki
from anki.hooks import addHook
from anki.lang import _
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


# Filters
##########################################################################


def hint(txt, extra, context, tag, fullname) -> str:
    if not txt.strip():
        return ""
    # random id
    domid = "hint%d" % id(txt)
    return """
<a class=hint href="#"
onclick="this.style.display='none';document.getElementById('%s').style.display='block';return false;">
%s</a><div id="%s" class=hint style="display: none">%s</div>
""" % (
        domid,
        _("Show %s") % tag,
        domid,
        txt,
    )


FURIGANA_RE = r" ?([^ >]+?)\[(.+?)\]"
RUBY_REPL = r"<ruby><rb>\1</rb><rt>\2</rt></ruby>"


def replace_if_not_audio(repl: str) -> Callable[[Any], Any]:
    def func(match):
        if match.group(2).startswith("sound:"):
            # return without modification
            return match.group(0)
        else:
            return re.sub(FURIGANA_RE, repl, match.group(0))

    return func


def without_nbsp(s: str) -> str:
    return s.replace("&nbsp;", " ")


def kanji(txt: str, *args) -> str:
    return re.sub(FURIGANA_RE, replace_if_not_audio(r"\1"), without_nbsp(txt))


def kana(txt: str, *args) -> str:
    return re.sub(FURIGANA_RE, replace_if_not_audio(r"\2"), without_nbsp(txt))


def furigana(txt: str, *args) -> str:
    return re.sub(FURIGANA_RE, replace_if_not_audio(RUBY_REPL), without_nbsp(txt))


addHook("fmod_hint", hint)
addHook("fmod_kanji", kanji)
addHook("fmod_kana", kana)
addHook("fmod_furigana", furigana)
