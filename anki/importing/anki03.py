# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Importing Anki v0.3 decks
==========================
"""
__docformat__ = 'restructuredtext'

import cPickle, cStringIO, os, datetime, types, sys
from anki.models import Model, FieldModel, CardModel
from anki.facts import Fact, factsTable, fieldsTable
from anki.cards import cardsTable
from anki.stats import *
from anki.features import FeatureManager
from anki.importing import Importer
from anki.utils import genID

def transformClasses(m, c):
    "Map objects into dummy classes"
    class EmptyClass(object):
        pass
    class EmptyList(list):
        pass
    class EmptyDict(dict):
        pass
    if c == "Fact":
        return EmptyDict
    elif c in ("CardModels", "Deck", "Facts", "Fields", "Models"):
        return EmptyList
    return EmptyClass

def load(path):
    "Load a deck from PATH."
    if isinstance(path, types.UnicodeType):
        path = path.encode(sys.getfilesystemencoding())
    file = open(path, "rb")
    try:
        try:
            data = file.read()
        except (IOError, OSError):
            raise ImportError
    finally:
        file.close()
    unpickler = cPickle.Unpickler(cStringIO.StringIO(data))
    unpickler.find_global = transformClasses
    deck = unpickler.load()
    deck.path = unicode(os.path.abspath(path), sys.getfilesystemencoding())
    return deck

# we need to upgrade the deck before converting
def maybeUpgrade(deck):
    # change old hardInterval from 1 day to 8-12 hours
    if list(deck.sched.hardInterval) == [1.0, 1.0]:
        deck.sched.hardInterval = [0.3333, 0.5]
    # add 'medium priority' support
    if not hasattr(deck.sched, 'medPriority'):
        deck.sched.medPriority = []
    # add delay2
    if not hasattr(deck.sched, "delay2"):
        deck.sched.delay2 = 28800
    # add collapsing
    if not hasattr(deck.sched, "collapse"):
        deck.sched.collapse = 18000
    # card related
    # - add 'total' attribute
    for card in deck:
        card.__dict__['total'] = (
            card.stats['new']['yes'] +
            card.stats['new']['no'] +
            card.stats['young']['yes'] +
            card.stats['young']['no'] +
            card.stats['old']['yes'] +
            card.stats['old']['no'])

class Anki03Importer(Importer):

    needMapper = False

    def doImport(self):
        oldDeck = load(self.file)
        maybeUpgrade(oldDeck)
        # mappings for old->new ids
        cardModels = {}
        fieldModels = {}
        # start with the models
        s = self.deck.s
        deck = self.deck
        import types
        def uni(txt):
            if txt is None:
                return txt
            if not isinstance(txt, types.UnicodeType):
                txt = unicode(txt, "utf-8")
            return txt
        for oldModel in oldDeck.facts.models:
            model = Model(uni(oldModel.name), uni(oldModel.description))
            model.id = oldModel.id
            model.tags = u", ".join(oldModel.tags)
            model.features = u", ".join(oldModel.decorators)
            model.created = oldModel.created
            model.modified = oldModel.modified
            deck.newCardOrder = min(1, oldModel.position)
            deck.addModel(model)
            # fields
            for oldField in oldModel.fields:
                fieldModel = FieldModel(uni(oldField.name),
                                        uni(oldField.description),
                                        oldField.name in oldModel.required,
                                        oldField.name in oldModel.unique)
                fieldModel.features = u", ".join(oldField.features)
                fieldModel.quizFontFamily = uni(oldField.display['quiz']['fontFamily'])
                fieldModel.quizFontSize = oldField.display['quiz']['fontSize']
                fieldModel.quizFontColour = uni(oldField.display['quiz']['fontColour'])
                fieldModel.editFontFamily = uni(oldField.display['edit']['fontFamily'])
                fieldModel.editFontSize = oldField.display['edit']['fontSize']
                fieldModel.id = oldField.id
                model.addFieldModel(fieldModel)
                s.flush() # we need the id
                fieldModels[id(oldField)] = fieldModel
            # card models
            for oldCard in oldModel.allcards:
                cardModel = CardModel(uni(oldCard.name),
                                      uni(oldCard.description),
                                      uni(oldCard.qformat),
                                      uni(oldCard.aformat))
                cardModel.active = oldCard in oldModel.cards
                cardModel.questionInAnswer = oldCard.questionInAnswer
                cardModel.id = oldCard.id
                model.spacing = 0.25
                cardModel.questionFontFamily = uni(oldCard.display['question']['fontFamily'])
                cardModel.questionFontSize = oldCard.display['question']['fontSize']
                cardModel.questionFontColour = uni(oldCard.display['question']['fontColour'])
                cardModel.questionAlign = oldCard.display['question']['align']
                cardModel.answerFontFamily = uni(oldCard.display['answer']['fontFamily'])
                cardModel.answerFontSize = oldCard.display['answer']['fontSize']
                cardModel.answerFontColour = uni(oldCard.display['answer']['fontColour'])
                cardModel.answerAlign = oldCard.display['answer']['align']
                cardModel.lastFontFamily = uni(oldCard.display['last']['fontFamily'])
                cardModel.lastFontSize = oldCard.display['last']['fontSize']
                cardModel.lastFontColour = uni(oldCard.display['last']['fontColour'])
                cardModel.editQuestionFontFamily = (
                    uni(oldCard.display['editQuestion']['fontFamily']))
                cardModel.editQuestionFontSize = (
                    oldCard.display['editQuestion']['fontSize'])
                cardModel.editAnswerFontFamily = (
                    uni(oldCard.display['editAnswer']['fontFamily']))
                cardModel.editAnswerFontSize = (
                    oldCard.display['editAnswer']['fontSize'])
                model.addCardModel(cardModel)
                s.flush() # we need the id
                cardModels[id(oldCard)] = cardModel
        # facts
        def getSpace(lastCard, lastAnswered):
            if not lastCard:
                return 0
            return lastAnswered + lastCard.delay
        def getLastCardId(fact):
            if not fact.lastCard:
                return None
            ret = [c.id for c in fact.cards if c.model.id == fact.lastCard.id]
            if ret:
                return ret[0]
        d = [{'id': f.id,
              'modelId': f.model.id,
              'created': f.created,
              'modified': f.modified,
              'tags': u",".join(f.tags),
              'spaceUntil': getSpace(f.lastCard, f.lastAnswered),
              'lastCardId': getLastCardId(f)
              } for f in oldDeck.facts]
        if d:
            s.execute(factsTable.insert(), d)
        self.total = len(oldDeck.facts)
        # fields in facts
        toAdd = []
        for oldFact in oldDeck.facts:
            for field in oldFact.model.fields:
                toAdd.append({'factId': oldFact.id,
                              'id': genID(),
                              'fieldModelId': fieldModels[id(field)].id,
                              'ordinal': fieldModels[id(field)].ordinal,
                              'value': uni(oldFact.get(field.name, u""))})
        if toAdd:
            s.execute(fieldsTable.insert(), toAdd)
        # cards
        class FakeObj(object):
            pass
        fake = FakeObj()
        fake.fact = FakeObj()
        fake.fact.model = FakeObj()
        fake.cardModel = FakeObj()
        def renderQA(c, type):
            fake.tags = u", ".join(c.tags)
            fake.fact.tags = u", ".join(c.fact.tags)
            fake.fact.model.tags = u", ".join(c.fact.model.tags)
            fake.cardModel.name = c.model.name
            return cardModels[id(c.model)].renderQA(fake, c.fact, type)
        d = [{'id': c.id,
              'created': c.created,
              'modified': c.modified,
              'factId': c.fact.id,
              'ordinal': cardModels[id(c.model)].ordinal,
              'cardModelId': cardModels[id(c.model)].id,
              'tags': u", ".join(c.tags),
              'factor': 2.5,
              'firstAnswered': c.firstAnswered,
              'interval': c.interval,
              'lastInterval': c.lastInterval,
              'modified': c.modified,
              'due': c.nextTime,
              'lastDue': c.lastTime,
              'reps': c.total,
              'question': renderQA(c, 'question'),
              'answer': renderQA(c, 'answer'),
              'averageTime': c.stats['averageTime'],
              'reviewTime': c.stats['totalTime'],
              'yesCount': (c.stats['new']['yes'] +
                           c.stats['young']['yes'] +
                           c.stats['old']['yes']),
              'noCount': (c.stats['new']['no'] +
                          c.stats['young']['no'] +
                          c.stats['old']['no']),
              'successive': c.stats['successivelyCorrect']}
             for c in oldDeck]
        if d:
            s.execute(cardsTable.insert(), d)
        # scheduler
        deck.description = uni(oldDeck.description)
        deck.created = oldDeck.created
        deck.maxScheduleTime = oldDeck.sched.maxScheduleTime
        deck.hardIntervalMin = oldDeck.sched.hardInterval[0]
        deck.hardIntervalMax = oldDeck.sched.hardInterval[1]
        deck.midIntervalMin = oldDeck.sched.midInterval[0]
        deck.midIntervalMax = oldDeck.sched.midInterval[1]
        deck.easyIntervalMin = oldDeck.sched.easyInterval[0]
        deck.easyIntervalMax = oldDeck.sched.easyInterval[1]
        deck.delay0 = oldDeck.sched.delay0
        deck.delay1 = oldDeck.sched.delay1
        deck.delay2 = oldDeck.sched.delay2
        deck.collapseTime = 3600 # oldDeck.sched.collapse
        deck.highPriority = u", ".join(oldDeck.sched.highPriority)
        deck.medPriority = u", ".join(oldDeck.sched.medPriority)
        deck.lowPriority = u", ".join(oldDeck.sched.lowPriority)
        deck.suspended = u", ".join(oldDeck.sched.suspendedTags)
        # scheduler global stats
        stats = Stats()
        stats.create(deck.s, 0, datetime.date.today())
        stats.day = datetime.date.fromtimestamp(oldDeck.created)
        stats.averageTime = oldDeck.sched.globalStats['averageTime']
        stats.reviewTime = oldDeck.sched.globalStats['totalTime']
        stats.distractedTime = 0
        stats.distractedReps = 0
        stats.newEase0 = oldDeck.sched.easeStats.get('new', {}).get(0, 0)
        stats.newEase1 = oldDeck.sched.easeStats.get('new', {}).get(1, 0)
        stats.newEase2 = oldDeck.sched.easeStats.get('new', {}).get(2, 0)
        stats.newEase3 = oldDeck.sched.easeStats.get('new', {}).get(3, 0)
        stats.newEase4 = oldDeck.sched.easeStats.get('new', {}).get(4, 0)
        stats.youngEase0 = oldDeck.sched.easeStats.get('young', {}).get(0, 0)
        stats.youngEase1 = oldDeck.sched.easeStats.get('young', {}).get(1, 0)
        stats.youngEase2 = oldDeck.sched.easeStats.get('young', {}).get(2, 0)
        stats.youngEase3 = oldDeck.sched.easeStats.get('young', {}).get(3, 0)
        stats.youngEase4 = oldDeck.sched.easeStats.get('young', {}).get(4, 0)
        stats.matureEase0 = oldDeck.sched.easeStats.get('old', {}).get(0, 0)
        stats.matureEase1 = oldDeck.sched.easeStats.get('old', {}).get(1, 0)
        stats.matureEase2 = oldDeck.sched.easeStats.get('old', {}).get(2, 0)
        stats.matureEase3 = oldDeck.sched.easeStats.get('old', {}).get(3, 0)
        stats.matureEase4 = oldDeck.sched.easeStats.get('old', {}).get(4, 0)
        yesCount = (oldDeck.sched.globalStats['new']['yes'] +
                    oldDeck.sched.globalStats['young']['yes'] +
                    oldDeck.sched.globalStats['old']['yes'])
        noCount = (oldDeck.sched.globalStats['new']['no'] +
                   oldDeck.sched.globalStats['young']['no'] +
                   oldDeck.sched.globalStats['old']['no'])
        stats.reps = yesCount + noCount
        stats.toDB(deck.s)
        # ignore daily stats & history, they make no sense on new version
        s.flush()
        deck.updateAllPriorities()
        # save without updating mod time
        deck.modified = oldDeck.modified
        deck.lastLoaded = deck.modified
        deck.s.commit()
        deck.save()
