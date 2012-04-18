# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.lang import _
from anki.consts import MODEL_CLOZE

models = []

# Basic
##########################################################################

def addBasicModel(col):
    mm = col.models
    m = mm.new(_("Basic"))
    fm = mm.newField(_("Front"))
    mm.addField(m, fm)
    fm = mm.newField(_("Back"))
    mm.addField(m, fm)
    t = mm.newTemplate(_("Forward"))
    t['qfmt'] = "{{Front}}"
    t['afmt'] = t['qfmt'] + "\n\n<hr id=answer>\n\n{{Back}}"
    mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((_("Basic"), addBasicModel))

# Cloze
##########################################################################

def addClozeModel(col):
    mm = col.models
    m = mm.new(_("Cloze"))
    m['type'] = MODEL_CLOZE
    fm = mm.newField(_("Text"))
    mm.addField(m, fm)
    fm = mm.newField(_("Extra"))
    mm.addField(m, fm)
    t = mm.newTemplate(_("Cloze"))
    fmt = "{{Cloze}}"
    t['css'] += """
.cloze {
 font-weight: bold;
 color: blue;
}"""
    t['qfmt'] = fmt
    t['afmt'] = fmt + "<br>\n{{%s}}" % _("Extra")
    mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((_("Cloze"), addClozeModel))
