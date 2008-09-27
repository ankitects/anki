def assertException(exception, func):
    found = False
    try:
        func()
    except exception:
        found = True
    assert found
