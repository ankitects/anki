# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Importing support
==============================

To import, a mapping is created of the form: [FieldModel, ...]. The mapping
may be extended by calling code if a file has more fields. To ignore a
particular FieldModel, replace it with None. A special number 0 donates a tags
field. The same field model should not occur more than once."""

__docformat__ = 'restructuredtext'

import time
from anki.cards import cardsTable
from anki.facts import factsTable, fieldsTable
from anki.lang import _
from anki.utils import genID, canonifyTags
from anki.errors import *

# Base importer
##########################################################################

class ForeignCard(object):
    "An temporary object storing fields and attributes."
    def __init__(self):
        self.fields = []
        self.tags = u""

class Importer(object):

    needMapper = True
    tagDuplicates = False
    multipleCardsAllowed = True

    def __init__(self, deck, file):
        self.file = file
        self._model = deck.currentModel
        self._mapping = None
        self.log = []
        self.deck = deck
        self.total = 0
        self.tagsToAdd = u""

    def doImport(self):
        "Import."
        self.deck.startProgress(6)
        self.deck.updateProgress(_("Importing..."))
        c = self.foreignCards()
        self.importCards(c)
        self.deck.updateProgress()
        self.deck.updateAllPriorities()
        self.deck.finishProgress()
        if c:
            self.deck.setModified()

    def fields(self):
        "The number of fields."
        return 0

    def foreignCards(self):
        "Return a list of foreign cards for importing."
        assert 0

    def resetMapping(self):
        "Reset mapping to default."
        numFields = self.fields()
        m = []
        [m.append(f) for f in self.model.fieldModels if f.required]
        [m.append(f) for f in self.model.fieldModels if not f.required]
        m.append(0)
        rem = max(0, self.fields() - len(m))
        m += [None] * rem
        del m[numFields:]
        self._mapping = m

    def getMapping(self):
        if not self._mapping:
            self.resetMapping()
        return self._mapping

    def setMapping(self, mapping):
        self._mapping = mapping

    mapping = property(getMapping, setMapping)

    def getModel(self):
        return self._model

    def setModel(self, model):
        self._model = model
        # update the mapping for the new model
        self._mapping = None
        self.getMapping()

    model = property(getModel, setModel)

    def importCards(self, cards):
        "Convert each card into a fact, apply attributes and add to deck."
        # ensure all unique and required fields are mapped
        for fm in self.model.fieldModels:
            if fm.required or fm.unique:
                if fm not in self.mapping:
                    raise ImportFormatError(
                        type="missingRequiredUnique",
                        info=_("Missing required/unique field '%(field)s'") %
                        {'field': fm.name})
        active = 0
        for cm in self.model.cardModels:
            if cm.active: active += 1
        if active > 1 and not self.multipleCardsAllowed:
            raise ImportFormatError(type="tooManyCards",
                info=_("""
The current importer only supports a single active card template. Please disable
all but one card template."""))
        # strip invalid cards
        cards = self.stripInvalid(cards)
        cards = self.stripOrTagDupes(cards)
        if cards:
            self.addCards(cards)

    def addCards(self, cards):
        "Add facts in bulk from foreign cards."
        # map tags field to attr
        try:
            idx = self.mapping.index(0)
            for c in cards:
                c.tags = c.fields[idx]
        except ValueError:
            pass
        # add facts
        self.deck.updateProgress()
        factIds = [genID() for n in range(len(cards))]
        self.deck.s.execute(factsTable.insert(),
            [{'modelId': self.model.id,
              'tags': canonifyTags(self.tagsToAdd + "," + cards[n].tags),
              'id': factIds[n]} for n in range(len(cards))])
        self.deck.factCount += len(factIds)
        self.deck.s.execute("""
delete from factsDeleted
where factId in (%s)""" % ",".join([str(s) for s in factIds]))
        # add all the fields
        self.deck.updateProgress()
        for fm in self.model.fieldModels:
            try:
                index = self.mapping.index(fm)
            except ValueError:
                index = None
            data = [{'factId': factIds[m],
                     'fieldModelId': fm.id,
                     'ordinal': fm.ordinal,
                     'id': genID(),
                     'value': (index is not None and
                               cards[m].fields[index] or u"")}
                    for m in range(len(cards))]
            self.deck.s.execute(fieldsTable.insert(),
                                data)
        # and cards
        self.deck.updateProgress()
        now = time.time()
        for cm in self.model.cardModels:
            self._now = now
            if cm.active:
                data = [self.addMeta({
                    'id': genID(),
                    'factId': factIds[m],
                    'cardModelId': cm.id,
                    'ordinal': cm.ordinal,
                    'question': u"",
                    'answer': u"",
                    'type': 2},cards[m]) for m in range(len(cards))]
                self.deck.s.execute(cardsTable.insert(),
                                    data)
        self.deck.updateProgress()
        self.deck.updateCardsFromModel(self.model)
        self.deck.cardCount += len(cards)
        self.total = len(factIds)

    def addMeta(self, data, card):
        "Add any scheduling metadata to cards"
        if 'fields' in card.__dict__:
            del card.fields
        data['created'] = self._now
        data['modified'] = self._now
        data['due'] = self._now
        self._now += .00001
        data.update(card.__dict__)
        data['combinedDue'] = data['due']
        data['isDue'] = data['combinedDue'] < time.time()
        return data

    def stripInvalid(self, cards):
        return [c for c in cards if self.cardIsValid(c)]

    def cardIsValid(self, card):
        fieldNum = len(card.fields)
        for n in range(len(self.mapping)):
            if self.mapping[n] and self.mapping[n].required:
                if fieldNum <= n or not card.fields[n].strip():
                    self.log.append("Fact is missing field '%s': %s" %
                                    (self.mapping[n].name,
                                     ", ".join(card.fields)))
                    return False
        return True

    def stripOrTagDupes(self, cards):
        # build a cache of items
        self.uniqueCache = {}
        for field in self.mapping:
            if field and field.unique:
                self.uniqueCache[field.id] = self.getUniqueCache(field)
        return [c for c in cards if self.cardIsUnique(c)]

    def getUniqueCache(self, field):
        "Return a dict with all fields, to test for uniqueness."
        return dict(self.deck.s.all(
            "select value, 1 from fields where fieldModelId = :fmid",
            fmid=field.id))

    def cardIsUnique(self, card):
        fields = []
        for n in range(len(self.mapping)):
            if self.mapping[n] and self.mapping[n].unique:
                if card.fields[n] in self.uniqueCache[self.mapping[n].id]:
                    if not self.tagDuplicates:
                        self.log.append("Fact has duplicate '%s': %s" %
                                        (self.mapping[n].name,
                                         ", ".join(card.fields)))
                        return False
                    fields.append(self.mapping[n].name)
                else:
                    self.uniqueCache[self.mapping[n].id][card.fields[n]] = 1
        if fields:
            card.tags += u"Import: duplicate, Duplicate: " + (
                "+".join(fields))
        return True

# Export modules
##########################################################################

from anki.importing.csv import TextImporter
from anki.importing.anki10 import Anki10Importer
from anki.importing.mnemosyne10 import Mnemosyne10Importer
from anki.importing.wcu import WCUImporter

Importers = (
    (_("Text separated by tabs or semicolons (*)"), TextImporter),
    (_("Anki 1.0 deck (*.anki)"), Anki10Importer),
    (_("Mnemosyne 1.x deck (*.mem)"), Mnemosyne10Importer),
    (_("CueCard deck (*.wcu)"), WCUImporter),
    )
