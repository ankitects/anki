# coding: utf-8

from tests.shared import getEmptyCol
from anki.utils import stripHTML, joinFields

def test_modelDelete():
    deck = getEmptyCol()
    f = deck.newNote()
    f['Front'] = u'1'
    f['Back'] = u'2'
    deck.addNote(f)
    assert deck.cardCount() == 1
    deck.models.rem(deck.models.current())
    assert deck.cardCount() == 0

def test_modelCopy():
    deck = getEmptyCol()
    m = deck.models.current()
    m2 = deck.models.copy(m)
    assert m2['name'] == "Basic copy"
    assert m2['id'] != m['id']
    assert len(m2['flds']) == 2
    assert len(m['flds']) == 2
    assert len(m2['flds']) == len(m['flds'])
    assert len(m['tmpls']) == 1
    assert len(m2['tmpls']) == 1
    assert deck.models.scmhash(m) == deck.models.scmhash(m2)

def test_fields():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u'1'
    f['Back'] = u'2'
    d.addNote(f)
    m = d.models.current()
    # make sure renaming a field updates the templates
    d.models.renameField(m, m['flds'][0], "NewFront")
    assert "{{NewFront}}" in m['tmpls'][0]['qfmt']
    h = d.models.scmhash(m)
    # add a field
    f = d.models.newField(m)
    f['name'] = "foo"
    d.models.addField(m, f)
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", "2", ""]
    assert d.models.scmhash(m) != h
    # rename it
    d.models.renameField(m, f, "bar")
    assert d.getNote(d.models.nids(m)[0])['bar'] == ''
    # delete back
    d.models.remField(m, m['flds'][1])
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", ""]
    # move 0 -> 1
    d.models.moveField(m, m['flds'][0], 1)
    assert d.getNote(d.models.nids(m)[0]).fields == ["", "1"]
    # move 1 -> 0
    d.models.moveField(m, m['flds'][1], 0)
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", ""]
    # add another and put in middle
    f = d.models.newField(m)
    f['name'] = "baz"
    d.models.addField(m, f)
    f = d.getNote(d.models.nids(m)[0])
    f['baz'] = "2"
    f.flush()
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", "", "2"]
    # move 2 -> 1
    d.models.moveField(m, m['flds'][2], 1)
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", "2", ""]
    # move 0 -> 2
    d.models.moveField(m, m['flds'][0], 2)
    assert d.getNote(d.models.nids(m)[0]).fields == ["2", "", "1"]
    # move 0 -> 1
    d.models.moveField(m, m['flds'][0], 1)
    assert d.getNote(d.models.nids(m)[0]).fields == ["", "2", "1"]

def test_templates():
    d = getEmptyCol()
    m = d.models.current(); mm = d.models
    t = mm.newTemplate("Reverse")
    t['qfmt'] = "{{Back}}"
    t['afmt'] = "{{Front}}"
    mm.addTemplate(m, t)
    mm.save(m)
    f = d.newNote()
    f['Front'] = u'1'
    f['Back'] = u'2'
    d.addNote(f)
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
    assert d.models.remTemplate(m, m['tmpls'][0])
    assert d.cardCount() == 1
    # and should have updated the other cards' ordinals
    c = f.cards()[0]
    assert c.ord == 0
    assert stripHTML(c.q()) == "1"
    # it shouldn't be possible to orphan notes by removing templates
    t = mm.newTemplate(m)
    mm.addTemplate(m, t)
    assert not d.models.remTemplate(m, m['tmpls'][0])

def test_cloze_ordinals():
    d = getEmptyCol()
    d.models.setCurrent(d.models.byName("Cloze"))
    m = d.models.current(); mm = d.models
    
    #We replace the default Cloze template
    t = mm.newTemplate("ChainedCloze")
    t['qfmt'] = "{{text:cloze:Text}}"
    t['afmt'] = "{{text:cloze:Text}}"
    mm.addTemplate(m, t)
    mm.save(m)
    d.models.remTemplate(m, m['tmpls'][0])
    
    f = d.newNote()
    f['Text'] = u'{{c1::firstQ::firstA}}{{c2::secondQ::secondA}}'
    d.addNote(f)
    assert d.cardCount() == 2
    (c, c2) = f.cards()
    # first card should have first ord
    assert c.ord == 0
    assert c2.ord == 1
    

def test_text():
    d = getEmptyCol()
    m = d.models.current()
    m['tmpls'][0]['qfmt'] = "{{text:Front}}"
    d.models.save(m)
    f = d.newNote()
    f['Front'] = u'hello<b>world'
    d.addNote(f)
    assert "helloworld" in f.cards()[0].q()

def test_cloze():
    d = getEmptyCol()
    d.models.setCurrent(d.models.byName("Cloze"))
    f = d.newNote()
    assert f.model()['name'] == "Cloze"
    # a cloze model with no clozes is not empty
    f['Text'] = u'nothing'
    assert d.addNote(f)
    # try with one cloze
    f = d.newNote()
    f['Text'] = "hello {{c1::world}}"
    assert d.addNote(f) == 1
    assert "hello <span class=cloze>[...]</span>" in f.cards()[0].q()
    assert "hello <span class=cloze>world</span>" in f.cards()[0].a()
    # and with a comment
    f = d.newNote()
    f['Text'] = "hello {{c1::world::typical}}"
    assert d.addNote(f) == 1
    assert "<span class=cloze>[typical]</span>" in f.cards()[0].q()
    assert "<span class=cloze>world</span>" in f.cards()[0].a()
    # and with 2 clozes
    f = d.newNote()
    f['Text'] = "hello {{c1::world}} {{c2::bar}}"
    assert d.addNote(f) == 2
    (c1, c2) = f.cards()
    assert "<span class=cloze>[...]</span> bar" in c1.q()
    assert "<span class=cloze>world</span> bar" in c1.a()
    assert "world <span class=cloze>[...]</span>" in c2.q()
    assert "world <span class=cloze>bar</span>" in c2.a()
    # if there are multiple answers for a single cloze, they are given in a
    # list
    f = d.newNote()
    f['Text'] = "a {{c1::b}} {{c1::c}}"
    assert d.addNote(f) == 1
    assert "<span class=cloze>b</span> <span class=cloze>c</span>" in (
        f.cards()[0].a())
    # if we add another cloze, a card should be generated
    cnt = d.cardCount()
    f['Text'] = "{{c2::hello}} {{c1::foo}}"
    f.flush()
    assert d.cardCount() == cnt + 1
    # 0 or negative indices are not supported
    f['Text'] += "{{c0::zero}} {{c-1:foo}}"
    f.flush()
    assert len(f.cards()) == 2

def test_chained_mods():
    d = getEmptyCol()
    d.models.setCurrent(d.models.byName("Cloze"))
    m = d.models.current(); mm = d.models
    
    #We replace the default Cloze template
    t = mm.newTemplate("ChainedCloze")
    t['qfmt'] = "{{cloze:text:Text}}"
    t['afmt'] = "{{cloze:text:Text}}"
    mm.addTemplate(m, t)
    mm.save(m)
    d.models.remTemplate(m, m['tmpls'][0])
    
    f = d.newNote()
    q1 = '<span style=\"color:red\">phrase</span>'
    a1 = '<b>sentence</b>'
    q2 = '<span style=\"color:red\">en chaine</span>'
    a2 = '<i>chained</i>'
    f['Text'] = "This {{c1::%s::%s}} demonstrates {{c1::%s::%s}} clozes." % (q1,a1,q2,a2)
    assert d.addNote(f) == 1
    assert "This <span class=cloze>[sentence]</span> demonstrates <span class=cloze>[chained]</span> clozes." in f.cards()[0].q()
    assert "This <span class=cloze>phrase</span> demonstrates <span class=cloze>en chaine</span> clozes." in f.cards()[0].a()

def test_modelChange():
    deck = getEmptyCol()
    basic = deck.models.byName("Basic")
    cloze = deck.models.byName("Cloze")
    # enable second template and add a note
    m = deck.models.current(); mm = deck.models
    t = mm.newTemplate("Reverse")
    t['qfmt'] = "{{Back}}"
    t['afmt'] = "{{Front}}"
    mm.addTemplate(m, t)
    mm.save(m)
    f = deck.newNote()
    f['Front'] = u'f'
    f['Back'] = u'b123'
    deck.addNote(f)
    # switch fields
    map = {0: 1, 1: 0}
    deck.models.change(basic, [f.id], basic, map, None)
    f.load()
    assert f['Front'] == 'b123'
    assert f['Back'] == 'f'
    # switch cards
    c0 = f.cards()[0]
    c1 = f.cards()[1]
    assert "b123" in c0.q()
    assert "f" in c1.q()
    assert c0.ord == 0
    assert c1.ord == 1
    deck.models.change(basic, [f.id], basic, None, map)
    f.load(); c0.load(); c1.load()
    assert "f" in c0.q()
    assert "b123" in c1.q()
    assert c0.ord == 1
    assert c1.ord == 0
    # .cards() returns cards in order
    assert f.cards()[0].id == c1.id
    # delete first card
    map = {0: None, 1: 1}
    deck.models.change(basic, [f.id], basic, None, map)
    f.load()
    c0.load()
    # the card was deleted
    try:
        c1.load()
        assert 0
    except TypeError:
        pass
    # but we have two cards, as a new one was generated
    assert len(f.cards()) == 2
    # an unmapped field becomes blank
    assert f['Front'] == 'b123'
    assert f['Back'] == 'f'
    deck.models.change(basic, [f.id], basic, map, None)
    f.load()
    assert f['Front'] == ''
    assert f['Back'] == 'f'
    # another note to try model conversion
    f = deck.newNote()
    f['Front'] = u'f2'
    f['Back'] = u'b2'
    deck.addNote(f)
    assert deck.models.useCount(basic) == 2
    assert deck.models.useCount(cloze) == 0
    map = {0: 0, 1: 1}
    deck.models.change(basic, [f.id], cloze, map, map)
    f.load()
    assert f['Text'] == "f2"
    assert len(f.cards()) == 2
    # back the other way, with deletion of second ord
    deck.models.remTemplate(basic, basic['tmpls'][1])
    assert deck.db.scalar("select count() from cards where nid = ?", f.id) == 2
    deck.models.change(cloze, [f.id], basic, map, map)
    assert deck.db.scalar("select count() from cards where nid = ?", f.id) == 1

def test_availOrds():
    d = getEmptyCol()
    m = d.models.current(); mm = d.models
    t = m['tmpls'][0]
    f = d.newNote()
    f['Front'] = "1"
    # simple templates
    assert mm.availOrds(m, joinFields(f.fields)) == [0]
    t['qfmt'] = "{{Back}}"
    mm.save(m, templates=True)
    assert not mm.availOrds(m, joinFields(f.fields))
    # AND
    t['qfmt'] = "{{#Front}}{{#Back}}{{Front}}{{/Back}}{{/Front}}"
    mm.save(m, templates=True)
    assert not mm.availOrds(m, joinFields(f.fields))
    t['qfmt'] = "{{#Front}}\n{{#Back}}\n{{Front}}\n{{/Back}}\n{{/Front}}"
    mm.save(m, templates=True)
    assert not mm.availOrds(m, joinFields(f.fields))
    # OR
    t['qfmt'] = "{{Front}}\n{{Back}}"
    mm.save(m, templates=True)
    assert mm.availOrds(m, joinFields(f.fields)) == [0]
    t['Front'] = ""
    t['Back'] = "1"
    assert mm.availOrds(m, joinFields(f.fields)) == [0]
