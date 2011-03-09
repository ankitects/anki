# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from anki.models import Model, Template
from anki.lang import _

models = []

# Basic
##########################################################################

def BasicModel(deck):
    m = Model(deck)
    m.name = _("Basic")
    fm = m.newField()
    fm['name'] = _("Front")
    fm['req'] = True
    fm['uniq'] = True
    m.addField(fm)
    fm = m.newField()
    fm['name'] = _("Back")
    m.addField(fm)
    t = Template(deck)
    t.name = _("Forward")
    t.qfmt = "{{" + _("Front") + "}}"
    t.afmt = "{{" + _("Back") + "}}"
    m.addTemplate(t)
    t = Template(deck)
    t.name = _("Reverse")
    t.qfmt = "{{" + _("Back") + "}}"
    t.afmt = "{{" + _("Front") + "}}"
    t.active = False
    m.addTemplate(t)
    return m

models.append(BasicModel)

# Recovery
##########################################################################

def RecoveryModel():
    m.name = _("Recovery")
    fm = Field(deck)
    fm.name = _("Question")
    m.addField(fm)
    fm = Field(deck)
    fm.name = _("Back")
    m.addField(fm)
    t = Template(deck)
    t.name = _("Forward")
    t.qfmt = "{{" + _("Question") + "}}"
    t.afmt = "{{" + _("Back") + "}}"
    m.addTemplate(t)
    return m
