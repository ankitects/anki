# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Standard Models.
==============================================================

Plugins can add to the 'models' dict to provide more standard
models.
"""

from anki.models import Model, CardModel, FieldModel
from anki.lang import _

models = {}

def byName(name):
    fn = models.get(name)
    if fn:
        return fn()
    raise ValueError("No such model available!")

def names():
    return models.keys()

# Basic
##########################################################################

def BasicModel():
    m = Model(_('Basic'))
    m.addFieldModel(FieldModel(u'Front', True, True))
    m.addFieldModel(FieldModel(u'Back', False, False))
    m.addCardModel(CardModel(u'Forward', u'%(Front)s', u'%(Back)s'))
    m.addCardModel(CardModel(u'Reverse', u'%(Back)s', u'%(Front)s',
                             active=False))
    m.tags = u"Basic"
    return m

models['Basic'] = BasicModel

# Recovery
##########################################################################

def RecoveryModel():
    m = Model(_('Recovery'))
    m.addFieldModel(FieldModel(u'Question', False, False))
    m.addFieldModel(FieldModel(u'Answer', False, False))
    m.addCardModel(CardModel(u'Single', u'{{{Question}}}', u'{{{Answer}}}'))
    m.tags = u"Recovery"
    return m
