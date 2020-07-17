from tests.shared import getEmptyCol


def test_deferred_frontside():
    d = getEmptyCol()
    m = d.models.current()
    m["tmpls"][0]["qfmt"] = "{{custom:Front}}"
    d.models.save(m)

    note = d.newNote()
    note["Front"] = "xxtest"
    note["Back"] = ""
    d.addNote(note)

    assert "xxtest" in note.cards()[0].a()
