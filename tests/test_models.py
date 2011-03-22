# coding: utf-8

from tests.shared import getEmptyDeck
from anki.models import Model
from anki.utils import stripHTML

def test_modelDelete():
    deck = getEmptyDeck()
    f = deck.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    deck.addFact(f)
    assert deck.cardCount() == 1
    deck.delModel(deck.conf['currentModelId'])
    assert deck.cardCount() == 0

def test_modelCopy():
    deck = getEmptyDeck()
    m = deck.currentModel()
    m2 = m.copy()
    assert m2.name == "Basic copy"
    assert m2.id != m.id
    assert len(m2.fields) == 2
    assert len(m.fields) == 2
    assert len(m2.fields) == len(m.fields)
    assert len(m.templates) == 2
    assert len(m2.templates) == 2

def test_fields():
    d = getEmptyDeck()
    f = d.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    d.addFact(f)
    m = d.currentModel()
    # make sure renaming a field updates the templates
    m.renameField(m.fields[0], "NewFront")
    assert m.templates[0]['qfmt'] == "{{NewFront}}"
    # add a field
    f = m.newField()
    f['name'] = "foo"
    m.addField(f)
    assert d.getFact(m.fids()[0])._fields == ["1", "2", ""]
    # rename it
    m.renameField(f, "bar")
    assert d.getFact(m.fids()[0])['bar'] == ''
    # delete back
    m.delField(m.fields[1])
    assert d.getFact(m.fids()[0])._fields == ["1", ""]
    # move 0 -> 1
    m.moveField(m.fields[0], 1)
    assert d.getFact(m.fids()[0])._fields == ["", "1"]
    # move 1 -> 0
    m.moveField(m.fields[1], 0)
    assert d.getFact(m.fids()[0])._fields == ["1", ""]
    # add another and put in middle
    f = m.newField()
    f['name'] = "baz"
    m.addField(f)
    f = d.getFact(m.fids()[0])
    f['baz'] = "2"
    f.flush()
    assert d.getFact(m.fids()[0])._fields == ["1", "", "2"]
    # move 2 -> 1
    m.moveField(m.fields[2], 1)
    assert d.getFact(m.fids()[0])._fields == ["1", "2", ""]
    # move 0 -> 2
    m.moveField(m.fields[0], 2)
    assert d.getFact(m.fids()[0])._fields == ["2", "", "1"]
    # move 0 -> 1
    m.moveField(m.fields[0], 1)
    assert d.getFact(m.fids()[0])._fields == ["", "2", "1"]

def test_templates():
    d = getEmptyDeck()
    m = d.currentModel()
    m.templates[1]['actv'] = True
    m.flush()
    f = d.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    d.addFact(f)
    assert d.cardCount() == 2
    # removing a template should delete its cards
    m.delTemplate(m.templates[0])
    assert d.cardCount() == 1
    # and should have updated the other cards' ordinals
    c = f.cards()[0]
    assert c.ord == 0
    stripHTML(c.q()) == "2"

def test_text():
    d = getEmptyDeck()
    m = d.currentModel()
    m.templates[0]['qfmt'] = "{{text:Front}}"
    m.flush()
    f = d.newFact()
    f['Front'] = u'hello<b>world'
    d.addFact(f)
    assert "helloworld" in f.cards()[0].q()

def test_cloze():
    d = getEmptyDeck()
    d.conf['currentModelId'] = 2
    f = d.newFact()
    assert f.model().name == "Cloze"
    # a cloze model with no clozes is empty
    f['Text'] = u'nothing'
    assert d.addFact(f) == 0
    # try with one cloze
    f['Text'] = "hello {{c1::world}}"
    assert d.addFact(f) == 1
    assert "hello <b>...</b>" in f.cards()[0].q()
    assert "hello <b>world</b>" in f.cards()[0].a()
    # and with a comment
    f = d.newFact()
    f['Text'] = "hello {{c1::world::typical}}"
    assert d.addFact(f) == 1
    assert "<b>...(typical)</b>" in f.cards()[0].q()
    assert "<b>world</b>" in f.cards()[0].a()
    # and with 2 clozes
    f = d.newFact()
    f['Text'] = "hello {{c1::world}} {{c2::bar}}"
    assert d.addFact(f) == 2
    (c1, c2) = f.cards()
    assert "<b>...</b> bar" in c1.q()
    assert "<b>world</b> bar" in c1.a()
    assert "world <b>...</b>" in c2.q()
    assert "world <b>bar</b>" in c2.a()
    # clozes should be supported in sections too
    m = d.currentModel()
    m.templates[0]['qfmt'] = "{{#cloze:1:Text}}{{Notes}}{{/cloze:1:Text}}"
    m.flush()
    f = d.newFact()
    f['Text'] = "hello"
    f['Notes'] = "world"
    assert d.addFact(f) == 0
    f['Text'] = "hello {{c1::foo}}"
    assert d.addFact(f) == 1

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
