# coding: utf-8
from contextlib import contextmanager

import aqt
from aqt import _run
from aqt.profiles import ProfileManager


@contextmanager
def temporaryUser(name="__Temporary Test User__"):

    pm = ProfileManager(base="")

    if name in pm.profiles():
        raise Exception(f"Could not create a temporary user with name {name}")

    pm.create(name)
    pm.name = name

    yield name

    pm.remove(name)

def test_run():

    # we need a new user for the test
    with temporaryUser() as name:
        app = _run(argv=["anki", "-p", name], exec=False)
        assert app

    aqt.mw.cleanupAndExit()

    # clean up what was spoiled
    aqt.mw = None

    # remove hooks added during app initialization
    from anki import hooks
    hooks._hooks = {}

    # test_nextIvl will fail on some systems if the locales are not restored
    import locale
    locale.setlocale(locale.LC_ALL, locale.getdefaultlocale())
