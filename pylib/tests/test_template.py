from tests.shared import getEmptyCol


def test_deferred_frontside():
    d = getEmptyCol()
    m = d.models.current()
    m["tmpls"][0]["qfmt"] = "{{custom:Front}}"
    d.models.save(m)

    f = d.newNote()
    f["Front"] = "xxtest"
    f["Back"] = ""
    d.addNote(f)

    assert "xxtest" in f.cards()[0].a()
