# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.lang import _

models = []

_header = """\
<style>
.card {
 text-align:center;
 background-color:white;
}
</style>\n\n"""

def _field(name):
    return """\
<span style="font-family:arial;font-size:12px;color:black;\
white-space:pre-wrap;">{{%s}}</span>\n""" % name

# Basic
##########################################################################

def addBasicModel(col):
    mm = col.models
    m = mm.new(_("Basic")) #2 field note"))
    fm = mm.newField(_("Front"))
    mm.addField(m, fm)
    fm = mm.newField(_("Back"))
    mm.addField(m, fm)
    t = mm.newTemplate(_("Forward"))
    t['qfmt'] = _header + _field(_("Front"))
    t['afmt'] = t['qfmt'] + "\n\n<hr id=answerStart>\n\n" + _field(_("Back"))
    mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((_("Basic"), addBasicModel))

# Cloze
##########################################################################

def addClozeModel(col):
    mm = col.models
    m = mm.new(_("Cloze"))
    fm = mm.newField(("Text"))
    mm.addField(m, fm)
    fm = mm.newField(_("Notes"))
    mm.addField(m, fm)
    for i in range(8):
        n = i+1
        t = mm.newTemplate(_("Cloze") + " %d" % n)
        t['qfmt'] = _header + ("{{#cloze:%d:Text}}\n"+
                               _field("cloze:%d:Text" % n)+
                               "{{/cloze:%d:Text}}") % (n, n)
        t['afmt'] = t['qfmt'] + "<br>\n"+_field("Notes")
        mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((_("Cloze"), addClozeModel))
