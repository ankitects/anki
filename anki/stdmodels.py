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
    t = mm.newTemplate(_("Card 1"))
    t['qfmt'] = "{{"+_("Front")+"}}"
    t['afmt'] = "{{FrontSide}}\n\n<hr id=answer>\n\n"+"{{"+_("Back")+"}}"
    mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((lambda: _("Basic"), addBasicModel))

# Forward & Reverse
##########################################################################

def addForwardReverse(col):
    mm = col.models
    m = addBasicModel(col)
    m['name'] = _("Basic (and reversed card)")
    t = mm.newTemplate(_("Card 2"))
    t['qfmt'] = "{{"+_("Back")+"}}"
    t['afmt'] = "{{FrontSide}}\n\n<hr id=answer>\n\n"+"{{"+_("Front")+"}}"
    mm.addTemplate(m, t)
    return m

models.append((lambda: _("Forward & Reverse"), addForwardReverse))

# Forward & Optional Reverse
##########################################################################

def addForwardOptionalReverse(col):
    mm = col.models
    m = addBasicModel(col)
    m['name'] = _("Basic (optional reversed card)")
    fm = mm.newField(_("Add Reverse"))
    mm.addField(m, fm)
    t = mm.newTemplate(_("Card 2"))
    t['qfmt'] = "{{#Add Reverse}}{{"+_("Back")+"}}{{/Add Reverse}}"
    t['afmt'] = "{{FrontSide}}\n\n<hr id=answer>\n\n"+"{{"+_("Front")+"}}"
    mm.addTemplate(m, t)
    return m

models.append((lambda: _("Forward & Optional Reverse"), addForwardOptionalReverse))

# Cloze
##########################################################################

def addClozeModel(col):
    mm = col.models
    m = mm.new(_("Cloze"))
    m['type'] = MODEL_CLOZE
    txt = _("Text")
    fm = mm.newField(txt)
    mm.addField(m, fm)
    fm = mm.newField(_("Extra"))
    mm.addField(m, fm)
    t = mm.newTemplate(_("Cloze"))
    fmt = "{{cloze:%s}}" % txt
    m['css'] += """
.cloze {
 font-weight: bold;
 color: blue;
}"""
    t['qfmt'] = fmt
    t['afmt'] = fmt + "<br>\n{{%s}}" % _("Extra")
    mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((lambda: _("Cloze"), addClozeModel))
