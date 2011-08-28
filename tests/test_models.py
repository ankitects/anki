# coding: utf-8

from tests.shared import getEmptyDeck
from anki.utils import stripHTML

def test_modelDelete():
    deck = getEmptyDeck()
    f = deck.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    deck.addFact(f)
    assert deck.cardCount() == 1
    deck.models.rem(deck.models.get(deck.conf['currentModelId']))
    assert deck.cardCount() == 0

def test_modelCopy():
    deck = getEmptyDeck()
    m = deck.models.current()
    m2 = deck.models.copy(m)
    assert m2['name'] == "Basic copy"
    assert m2['id'] != m['id']
    assert len(m2['flds']) == 2
    assert len(m['flds']) == 2
    assert len(m2['flds']) == len(m['flds'])
    assert len(m['tmpls']) == 2
    assert len(m2['tmpls']) == 2

def test_fields():
    d = getEmptyDeck()
    f = d.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    d.addFact(f)
    m = d.models.current()
    # make sure renaming a field updates the templates
    d.models.renameField(m, m['flds'][0], "NewFront")
    assert m['tmpls'][0]['qfmt'] == "{{NewFront}}"
    # add a field
    f = d.models.newField(m)
    f['name'] = "foo"
    d.models.addField(m, f)
    assert d.getFact(d.models.fids(m)[0]).fields == ["1", "2", ""]
    # rename it
    d.models.renameField(m, f, "bar")
    assert d.getFact(d.models.fids(m)[0])['bar'] == ''
    # delete back
    d.models.delField(m, m['flds'][1])
    assert d.getFact(d.models.fids(m)[0]).fields == ["1", ""]
    # move 0 -> 1
    d.models.moveField(m, m['flds'][0], 1)
    assert d.getFact(d.models.fids(m)[0]).fields == ["", "1"]
    # move 1 -> 0
    d.models.moveField(m, m['flds'][1], 0)
    assert d.getFact(d.models.fids(m)[0]).fields == ["1", ""]
    # add another and put in middle
    f = d.models.newField(m)
    f['name'] = "baz"
    d.models.addField(m, f)
    f = d.getFact(d.models.fids(m)[0])
    f['baz'] = "2"
    f.flush()
    assert d.getFact(d.models.fids(m)[0]).fields == ["1", "", "2"]
    # move 2 -> 1
    d.models.moveField(m, m['flds'][2], 1)
    assert d.getFact(d.models.fids(m)[0]).fields == ["1", "2", ""]
    # move 0 -> 2
    d.models.moveField(m, m['flds'][0], 2)
    assert d.getFact(d.models.fids(m)[0]).fields == ["2", "", "1"]
    # move 0 -> 1
    d.models.moveField(m, m['flds'][0], 1)
    assert d.getFact(d.models.fids(m)[0]).fields == ["", "2", "1"]

def test_templates():
    d = getEmptyDeck()
    m = d.models.current()
    m['tmpls'][1]['actv'] = True
    d.models.save(m)
    f = d.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    d.addFact(f)
    assert d.cardCount() == 2
    (c, c2) = f.cards()
    # first card should have first ord
    assert c.ord == 0
    assert c2.ord == 1
    # switch templates
    d.models.moveTemplate(m, c.template(), 1)
    c.load(); c2.load()
    assert c.ord == 1
    assert c2.ord == 0
    # removing a template should delete its cards
    d.models.delTemplate(m, m['tmpls'][0])
    assert d.cardCount() == 1
    # and should have updated the other cards' ordinals
    c = f.cards()[0]
    assert c.ord == 0
    stripHTML(c.q()) == "2"

def test_text():
    d = getEmptyDeck()
    m = d.models.current()
    m['tmpls'][0]['qfmt'] = "{{text:Front}}"
    d.models.save(m)
    f = d.newFact()
    f['Front'] = u'hello<b>world'
    d.addFact(f)
    assert "helloworld" in f.cards()[0].q()

def test_cloze():
    d = getEmptyDeck()
    d.conf['currentModelId'] = d.models.byName("Cloze")['id']
    f = d.newFact()
    assert f.model()['name'] == "Cloze"
    # a cloze model with no clozes is empty
    f['Text'] = u'nothing'
    assert d.addFact(f) == 0
    # try with one cloze
    f['Text'] = "hello {{c1::world}}"
    assert d.addFact(f) == 1
    assert "hello <span class=cloze>[...]</span>" in f.cards()[0].q()
    # the default is no context
    assert "<span class=cloze>world</span>" in f.cards()[0].a()
    assert "hello <span class=cloze>world</span>" not in f.cards()[0].a()
    # check context works too
    f.model()['clozectx'] = True
    assert "hello <span class=cloze>world</span>" in f.cards()[0].a()
    # and with a comment
    f = d.newFact()
    f['Text'] = "hello {{c1::world::typical}}"
    assert d.addFact(f) == 1
    assert "<span class=cloze>[...(typical)]</span>" in f.cards()[0].q()
    assert "<span class=cloze>world</span>" in f.cards()[0].a()
    # and with 2 clozes
    f = d.newFact()
    f['Text'] = "hello {{c1::world}} {{c2::bar}}"
    assert d.addFact(f) == 2
    (c1, c2) = f.cards()
    assert "<span class=cloze>[...]</span> bar" in c1.q()
    assert "<span class=cloze>world</span> bar" in c1.a()
    assert "world <span class=cloze>[...]</span>" in c2.q()
    assert "world <span class=cloze>bar</span>" in c2.a()
    # if there are multiple answers for a single cloze, they are given in a
    # list
    f.model()['clozectx'] = False
    f = d.newFact()
    f['Text'] = "a {{c1::b}} {{c1::c}}"
    assert d.addFact(f) == 1
    assert "<span class=cloze>b</span>, <span class=cloze>c</span>" in (
        f.cards()[0].a())
    # clozes should be supported in sections too
    m = d.models.current()
    m['tmpls'][0]['qfmt'] = "{{#cloze:1:Text}}{{Notes}}{{/cloze:1:Text}}"
    d.models.save(m)
    f = d.newFact()
    f['Text'] = "hello"
    f['Notes'] = "world"
    assert d.addFact(f) == 0
    f['Text'] = "hello {{c1::foo}}"
    assert d.addFact(f) == 1

def test_modelChange():
    deck = getEmptyDeck()
    basic = deck.models.byName("Basic")
    cloze = deck.models.byName("Cloze")
    # enable second template and add a fact
    basic['tmpls'][1]['actv'] = True
    deck.models.save(basic)
    f = deck.newFact()
    f['Front'] = u'f'
    f['Back'] = u'b'
    deck.addFact(f)
    # switch fields
    map = {0: 1, 1: 0}
    deck.models.change(basic, [f.id], basic, map, None)
    f.load()
    assert f['Front'] == 'b'
    assert f['Back'] == 'f'
    # switch cards
    c0 = f.cards()[0]
    c1 = f.cards()[1]
    assert stripHTML(c0.q()) == "b"
    assert stripHTML(c1.q()) == "f"
    assert c0.ord == 0
    assert c1.ord == 1
    deck.models.change(basic, [f.id], basic, None, map)
    f.load(); c0.load(); c1.load()
    assert stripHTML(c0.q()) == "f"
    assert stripHTML(c1.q()) == "b"
    assert c0.ord == 1
    assert c1.ord == 0
    # .cards() returns cards in order
    assert f.cards()[0].id == c1.id
    # delete first card
    map = {0: None, 1: 1}
    deck.models.change(basic, [f.id], basic, None, map)
    f.load()
    c0.load()
    try:
        c1.load()
        assert 0
    except TypeError:
        pass
    assert len(f.cards()) == 1
    # an unmapped field becomes blank
    assert f['Front'] == 'b'
    assert f['Back'] == 'f'
    deck.models.change(basic, [f.id], basic, map, None)
    f.load()
    assert f['Front'] == ''
    assert f['Back'] == 'f'
    # another fact to try model conversion
    f = deck.newFact()
    f['Front'] = u'f2'
    f['Back'] = u'b2'
    deck.addFact(f)
    assert deck.models.useCount(basic) == 2
    assert deck.models.useCount(cloze) == 0
    map = {0: 0, 1: 1}
    deck.models.change(basic, [f.id], cloze, map, map)
    f.load()
    assert f['Text'] == "f2"
    assert f['Notes'] == "b2"
    assert len(f.cards()) == 2
    assert "b2" in f.cards()[0].a()

def test_css():
    deck = getEmptyDeck()
    basic = deck.models.byName("Basic")
    assert "arial" in basic['css']
    assert "helvetica" not in basic['css']
    basic['flds'][0]['font'] = "helvetica"
    deck.models.save(basic)
    assert "helvetica" in basic['css']
