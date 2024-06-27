from anki._vendor.stringcase import capitalcase,camelcase

def test_empty_string():
    result = capitalcase("")
    assert(result == "")

    result = camelcase("")
    assert(result == "")

def test_nonempty_string():
    result = capitalcase("software")
    assert(result == "Software")

    result = camelcase("software_eng_pr")
    assert(result == "softwareEngPr")

test_empty_string()
test_nonempty_string()