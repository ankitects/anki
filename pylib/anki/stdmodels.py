# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Any, Callable, List, Tuple, Union

from anki.consts import MODEL_CLOZE
from anki.lang import _
from anki.models import NoteType

models: List[Tuple[Union[Callable[[], str], str], Callable[[Any], NoteType]]] = []

# Basic
##########################################################################


def _newBasicModel(col, name=None) -> NoteType:
    mm = col.models
    m = mm.new(name or _("Basic"))
    fm = mm.newField(_("Front"))
    mm.addField(m, fm)
    fm = mm.newField(_("Back"))
    mm.addField(m, fm)
    t = mm.newTemplate(_("Card 1"))
    t["qfmt"] = "{{" + _("Front") + "}}"
    t["afmt"] = "{{FrontSide}}\n\n<hr id=answer>\n\n" + "{{" + _("Back") + "}}"
    mm.addTemplate(m, t)
    return m


def addBasicModel(col) -> NoteType:
    m = _newBasicModel(col)
    col.models.add(m)
    return m


models.append((lambda: _("Basic"), addBasicModel))

# Basic w/ typing
##########################################################################


def addBasicTypingModel(col) -> NoteType:
    mm = col.models
    m = _newBasicModel(col, _("Basic (type in the answer)"))
    t = m["tmpls"][0]
    t["qfmt"] = "{{" + _("Front") + "}}\n\n{{type:" + _("Back") + "}}"
    t["afmt"] = "{{" + _("Front") + "}}\n\n<hr id=answer>\n\n{{type:" + _("Back") + "}}"
    mm.add(m)
    return m


models.append((lambda: _("Basic (type in the answer)"), addBasicTypingModel))

# Forward & Reverse
##########################################################################


def _newForwardReverse(col, name=None) -> NoteType:
    mm = col.models
    m = _newBasicModel(col, name or _("Basic (and reversed card)"))
    t = mm.newTemplate(_("Card 2"))
    t["qfmt"] = "{{" + _("Back") + "}}"
    t["afmt"] = "{{FrontSide}}\n\n<hr id=answer>\n\n" + "{{" + _("Front") + "}}"
    mm.addTemplate(m, t)
    return m


def addForwardReverse(col) -> NoteType:
    m = _newForwardReverse(col)
    col.models.add(m)
    return m


models.append((lambda: _("Basic (and reversed card)"), addForwardReverse))

# Forward & Optional Reverse
##########################################################################


def addForwardOptionalReverse(col) -> NoteType:
    mm = col.models
    m = _newForwardReverse(col, _("Basic (optional reversed card)"))
    av = _("Add Reverse")
    fm = mm.newField(av)
    mm.addField(m, fm)
    t = m["tmpls"][1]
    t["qfmt"] = "{{#%s}}%s{{/%s}}" % (av, t["qfmt"], av)
    mm.add(m)
    return m


models.append((lambda: _("Basic (optional reversed card)"), addForwardOptionalReverse))

# Cloze
##########################################################################


def addClozeModel(col) -> NoteType:
    mm = col.models
    m = mm.new(_("Cloze"))
    m["type"] = MODEL_CLOZE
    txt = _("Text")
    fm = mm.newField(txt)
    mm.addField(m, fm)
    t = mm.newTemplate(_("Cloze"))
    fmt = "{{cloze:%s}}" % txt
    m[
        "css"
    ] += """
.cloze {
 font-weight: bold;
 color: blue;
}
.nightMode .cloze {
 color: lightblue;
}"""
    t["qfmt"] = fmt
    t["afmt"] = fmt
    mm.addTemplate(m, t)
    mm.add(m)
    return m


models.append((lambda: _("Cloze"), addClozeModel))
