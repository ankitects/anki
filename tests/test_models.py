# coding: utf-8

from tests.shared import getEmptyDeck
from anki.models import Model, Template
from anki.utils import stripHTML

def test_modelDelete():
    deck = getEmptyDeck()
    f = deck.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    deck.addFact(f)
    assert deck.cardCount() == 1
    deck.deleteModel(deck.conf['currentModelId'])
    assert deck.cardCount() == 0

def test_modelCopy():
    deck = getEmptyDeck()
    m = deck.currentModel()
    m2 = m.copy()
    assert m2.name == "Basic copy"
    assert m2.id != m.id
    assert m2.templates[0].id != m.templates[0].id
    assert len(m2.fields) == 2
    assert len(m.fields) == 2
    assert len(m2.fields) == len(m.fields)
    assert len(m.templates) == 2
    assert len(m2.templates) == 2

def test_modelChange():
    print "model change"
    return
    deck = getEmptyDeck()
    m2 = deck.currentModel()
    # taken from jp support plugin
    m1 = Model(deck)
    m1.name = "Japanese"
    # field 1
    fm = m1.newField()
    fm['name'] = "Expression"
    fm['req'] = True
    fm['uniq'] = True
    m1.addField(fm)
    # field2
    fm = m1.newField()
    fm['name'] = "Meaning"
    m1.addField(fm)
    # field3
    fm = m1.newField()
    fm['name'] = "Reading"
    m1.addField(fm)
    # template1
    t = Template(deck)
    t.name = "Recognition"
    t.qfmt = "{{Expression}}"
    t.afmt = "{{Reading}}<br>{{Meaning}}"
    m1.addTemplate(t)
    # template2
    t = Template(deck)
    t.name = "Recall"
    t.qfmt = "{{Meaning}}"
    t.afmt = "{{Expression}}<br>{{Reading}}"
    #t.active = False
    m1.addTemplate(t)
    deck.addModel(m1)

    # add some facts
    f = deck.newFact()
    f['Expression'] = u'e'
    f['Meaning'] = u'm'
    f['Reading'] = u'r'
    deck.addFact(f)
    f2 = deck.newFact()
    f2['Expression'] = u'e2'
    f2['Meaning'] = u'm2'
    f2['Reading'] = u'r2'
    deck.addFact(f2)

    # convert to basic
    assert deck.modelUseCount(m1) == 2
    assert deck.modelUseCount(m2) == 0
    assert deck.cardCount() == 4
    assert deck.factCount() == 2
    fmap = {m1.fields[0]: m2.fields[0],
            m1.fields[1]: None,
            m1.fields[2]: m2.fields[1]}
    cmap = {m1.templates[0]: m2.templates[0],
            m1.templates[1]: None}
    deck.changeModel([f.id], m2, fmap, cmap)
    assert deck.modelUseCount(m1) == 1
    assert deck.modelUseCount(m2) == 1
    assert deck.cardCount() == 3
    assert deck.factCount() == 2
    c = deck.getCard(deck.db.scalar("select id from cards where fid = ?", f.id))
    assert stripHTML(c.q()) == u"e"
    assert stripHTML(c.a()) == u"r"
