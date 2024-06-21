from anki._vendor.stringcase import capitalcase

def test_empty_string():
    result = capitalcase("")
    assert(result == "")

def test_nonempty_string():
    result = capitalcase("software")
    assert(result == "Software")

test_empty_string()
test_nonempty_string()