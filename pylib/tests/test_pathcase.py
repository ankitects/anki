from anki._vendor.stringcase import pathcase, branch_coverage, print_coverage

def test_pathcase():
    result = pathcase("")
    assert result == ""
    assert branch_coverage["empty_string"] == True
    assert branch_coverage["non_empty_string"] == False
    print_coverage()

    branch_coverage["empty_string"] = False
    branch_coverage["non_empty_string"] = False

    result = pathcase("Example String")
    assert result == "example//string"
    assert branch_coverage["empty_string"] == False
    assert branch_coverage["non_empty_string"] == True
    print_coverage()