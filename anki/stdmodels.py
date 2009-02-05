# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Standard models
==============================================================
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

# these are provided for convenience. all of the fields can be changed in real
# time and they will be stored with the deck.

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

# Japanese
##########################################################################

def JapaneseModel():
    m = Model(_("Japanese"))
    # expression
    f = FieldModel(u'Expression', True, True)
    font = u"Mincho"
    f.quizFontSize = 72
    f.quizFontFamily = font
    f.editFontFamily = font
    m.addFieldModel(f)
    # meaning
    m.addFieldModel(FieldModel(u'Meaning', False, False))
    # reading
    f = FieldModel(u'Reading', False, False)
    f.quizFontFamily = font
    f.editFontFamily = font
    m.addFieldModel(f)
    m.addCardModel(CardModel(u"Recognition",
                             u"%(Expression)s",
                             u"%(Reading)s<br>%(Meaning)s"))
    m.addCardModel(CardModel(u"Recall",
                             u"%(Meaning)s",
                             u"%(Expression)s<br>%(Reading)s",
                             active=False))
    m.tags = u"Japanese"
    return m

models['Japanese'] = JapaneseModel

# Cantonese
##########################################################################

def CantoneseModel():
    m = Model(_("Cantonese"))
    f = FieldModel(u'Expression')
    f.quizFontSize = 72
    m.addFieldModel(f)
    m.addFieldModel(FieldModel(u'Meaning', False, False))
    m.addFieldModel(FieldModel(u'Reading', False, False))
    m.addCardModel(CardModel(u"Recognition",
                             u"%(Expression)s",
                             u"%(Reading)s<br>%(Meaning)s"))
    m.addCardModel(CardModel(u"Recall",
                             u"%(Meaning)s",
                             u"%(Expression)s<br>%(Reading)s",
                             active=False))
    m.tags = u"Cantonese"
    return m

models['Cantonese'] = CantoneseModel

# Mandarin
##########################################################################

def MandarinModel():
    m = Model(_("Mandarin"))
    f = FieldModel(u'Expression')
    f.quizFontSize = 72
    m.addFieldModel(f)
    m.addFieldModel(FieldModel(u'Meaning', False, False))
    m.addFieldModel(FieldModel(u'Reading', False, False))
    m.addCardModel(CardModel(u"Recognition",
                             u"%(Expression)s",
                             u"%(Reading)s<br>%(Meaning)s"))
    m.addCardModel(CardModel(u"Recall",
                             u"%(Meaning)s",
                             u"%(Expression)s<br>%(Reading)s",
                             active=False))
    m.tags = u"Mandarin"
    return m

models['Mandarin'] = MandarinModel
