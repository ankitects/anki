# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.lang import _

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
    t['qfmt'] = "{{" + _("Front") + "}}"
    t['afmt'] = "{{" + _("Back") + "}}"
    mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((_("Basic"), addBasicModel))

# Cloze
##########################################################################

def addClozeModel(col):
    mm = col.models
    m = mm.new(_("Cloze"))
    fm = mm.newField(_("Text"))
    mm.addField(m, fm)
    fm = mm.newField(_("Notes"))
    mm.addField(m, fm)
    for i in range(8):
        n = i+1
        t = mm.newTemplate(_("Cloze") + " %d" % n)
        t['qfmt'] = ("{{#cloze:%d:Text}}<br>{{cloze:%d:%s}}<br>"+
                     "{{/cloze:%d:Text}}") % (n, n, _("Text"), n)
        t['afmt'] = ("{{cloze:%d:" + _("Text") + "}}") % n
        t['afmt'] += "<br>{{" + _("Notes") + "}}"
        mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((_("Cloze"), addClozeModel))
