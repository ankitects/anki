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
from anki.utils import canonifyTags, ids2str
from anki.errors import *
from anki.deck import NEW_CARDS_RANDOM

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
    # if set, update instead of regular importing
    # (foreignCardFieldIndex, fieldModelId)
    updateKey = None
    multipleCardsAllowed = True
    needDelimiter = False

    def __init__(self, deck, file):
        self.file = file
        self._model = deck.currentModel
        self._mapping = None
        self.log = []
        self.deck = deck
        self.total = 0
        self.tagsToAdd = u""

    def doImport(self):
        "Import. Caller must .reset()"
        if self.updateKey is not None:
            return self.doUpdate()
        random = self.deck.newCardOrder == NEW_CARDS_RANDOM
        num = 7
        if random:
            num += 1
        self.deck.startProgress(num)
        self.deck.updateProgress(_("Importing..."))
        c = self.foreignCards()
        if self.importCards(c):
            self.deck.updateProgress()
            self.deck.updateCardTags(self.cardIds)
            self.deck.updateProgress()
            self.deck.updatePriorities(self.cardIds)
            if random:
                self.deck.updateProgress()
                self.deck.randomizeNewCards(self.cardIds)
        self.deck.finishProgress()
        if c:
            self.deck.setModified()

    def doUpdate(self):
        self.deck.startProgress(8)
        # grab the data from the external file
        self.deck.updateProgress(_("Updating..."))
        cards = self.foreignCards()
        # grab data from db
        self.deck.updateProgress()
        fields = self.deck.s.all("""
select factId, value from fields where fieldModelId = :id
and value != ''""",
                               id=self.updateKey[1])
        # hash it
        self.deck.updateProgress()
        vhash = {}
        fids = []
        for (fid, val) in fields:
            fids.append(fid)
            vhash[val] = fid
        # prepare tags
        tagsIdx = None
        try:
            tagsIdx = self.mapping.index(0)
            for c in cards:
                c.tags = canonifyTags(self.tagsToAdd + " " + c.fields[tagsIdx])
        except ValueError:
            pass
        # look for matches
        self.deck.updateProgress()
        upcards = []
        newcards = []
        for c in cards:
            v = c.fields[self.updateKey[0]]
            if v in vhash:
                # ignore empty keys
                if v:
                    # fid, card
                    upcards.append((vhash[v], c))
            else:
                newcards.append(c)
        # update fields
        for fm in self.model.fieldModels:
            if fm.id == self.updateKey[1]:
                # don't update key
                continue
            try:
                index = self.mapping.index(fm)
            except ValueError:
                # not mapped
                continue
            data = [{'fid': fid,
                     'fmid': fm.id,
                     'v': c.fields[index]}
                    for (fid, c) in upcards]
            self.deck.s.execute("""
update fields set value = :v where factId = :fid and fieldModelId = :fmid""",
                                data)
        # update tags
        self.deck.updateProgress()
        if tagsIdx is not None:
            data = [{'fid': fid,
                     't': c.fields[tagsIdx]}
                    for (fid, c) in upcards]
            self.deck.s.execute(
                "update facts set tags = :t where id = :fid",
                data)
        # rebuild caches
        self.deck.updateProgress()
        cids = self.deck.s.column0(
            "select id from cards where factId in %s" %
            ids2str(fids))
        self.deck.updateCardTags(cids)
        self.deck.updateProgress()
        self.deck.updatePriorities(cids)
        self.deck.updateProgress()
        self.deck.updateCardsFromFactIds(fids)
        self.total = len(fids)
        self.deck.setModified()
        self.deck.finishProgress()

    def fields(self):
        "The number of fields."
        return 0

    def foreignCards(self):
        "Return a list of foreign cards for importing."
        assert 0

    def resetMapping(self):
        "Reset mapping to default."
        numFields = self.fields()
        m = [f for f in self.model.fieldModels]
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
                info=_("""\
The current importer only supports a single active card template. Please disable\
 all but one card template."""))
        # strip invalid cards
        cards = self.stripInvalid(cards)
        cards = self.stripOrTagDupes(cards)
        self.cardIds = []
        if cards:
            self.addCards(cards)
        return cards

    def addCards(self, cards):
        "Add facts in bulk from foreign cards."
        # map tags field to attr
        try:
            idx = self.mapping.index(0)
            for c in cards:
                c.tags += " " + c.fields[idx]
        except ValueError:
            pass
        # add facts
        self.deck.updateProgress()
        factIds = [genID() for n in range(len(cards))]
        factCreated = {}
        def fudgeCreated(d, tmp=[]):
            if not tmp:
                tmp.append(time.time())
            else:
                tmp[0] += 0.0001
            d['created'] = tmp[0]
            factCreated[d['id']] = d['created']
            return d
        self.deck.s.execute(factsTable.insert(),
            [fudgeCreated({'modelId': self.model.id,
              'tags': canonifyTags(self.tagsToAdd + " " + cards[n].tags),
              'id': factIds[n]}) for n in range(len(cards))])
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
        active = 0
        for cm in self.model.cardModels:
            if cm.active:
                active += 1
                data = [self.addMeta({
                    'id': genID(),
                    'factId': factIds[m],
                    'factCreated': factCreated[factIds[m]],
                    'cardModelId': cm.id,
                    'ordinal': cm.ordinal,
                    'question': u"",
                    'answer': u""
                    },cards[m]) for m in range(len(cards))]
                self.deck.s.execute(cardsTable.insert(),
                                    data)
        self.deck.updateProgress()
        self.deck.updateCardsFromFactIds(factIds)
        self.deck.cardCount += len(cards) * active
        self.total = len(factIds)

    def addMeta(self, data, card):
        "Add any scheduling metadata to cards"
        if 'fields' in card.__dict__:
            del card.fields
        t = data['factCreated'] + data['ordinal'] * 0.00001
        data['created'] = t
        data['modified'] = t
        data['due'] = t
        data.update(card.__dict__)
        data['tags'] = u""
        self.cardIds.append(data['id'])
        data['combinedDue'] = data['due']
        if data.get('successive', 0):
            t = 1
        elif data.get('reps', 0):
            t = 0
        else:
            t = 2
        data['type'] = t
        data['relativeDelay'] = t
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
        fieldsAsTags = []
        for n in range(len(self.mapping)):
            if self.mapping[n] and self.mapping[n].unique:
                if card.fields[n] in self.uniqueCache[self.mapping[n].id]:
                    if not self.tagDuplicates:
                        self.log.append("Fact has duplicate '%s': %s" %
                                        (self.mapping[n].name,
                                         ", ".join(card.fields)))
                        return False
                    fieldsAsTags.append(self.mapping[n].name.replace(" ", "-"))
                else:
                    self.uniqueCache[self.mapping[n].id][card.fields[n]] = 1
        if fieldsAsTags:
            card.tags += u" Duplicate:" + (
                "+".join(fieldsAsTags))
            card.tags = canonifyTags(card.tags)
        return True

# Export modules
##########################################################################

from anki.importing.csvfile import TextImporter
from anki.importing.anki10 import Anki10Importer
from anki.importing.mnemosyne10 import Mnemosyne10Importer
from anki.importing.wcu import WCUImporter
from anki.importing.supermemo_xml import SupermemoXmlImporter
from anki.importing.dingsbums import DingsBumsImporter

Importers = (
    (_("Text separated by tabs or semicolons (*)"), TextImporter),
    (_("Anki Deck (*.anki)"), Anki10Importer),
    (_("Mnemosyne Deck (*.mem)"), Mnemosyne10Importer),
    (_("CueCard Deck (*.wcu)"), WCUImporter),
    (_("Supermemo XML export (*.xml)"), SupermemoXmlImporter),
    (_("DingsBums?! Deck (*.dbxml)"), DingsBumsImporter),
    )
