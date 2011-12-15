# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.lang import _

models = []

header = """\
<style>
.card {
 text-align:center;
 background-color:white;
}
</style>\n\n"""

def field(name, family="arial", size=20):
    return """\
<span style="font-family:%s; font-size:%spx; color:black; \
white-space:pre-wrap;">{{%s}}</span>\n""" % (family, size, name)

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
    t['qfmt'] = header + field(_("Front"))
    t['afmt'] = t['qfmt'] + "\n\n<hr id=answerStart>\n\n" + field(_("Back"))
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
        t['qfmt'] = header + ("{{#cloze:%d:Text}}\n"+
                               field("cloze:%d:Text" % n)+
                               "{{/cloze:%d:Text}}") % (n, n)
        t['afmt'] = t['qfmt'] + "<br>\n"+field("Notes")
        mm.addTemplate(m, t)
    mm.add(m)
    return m

models.append((_("Cloze"), addClozeModel))
