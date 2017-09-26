# coding: utf-8
import shutil
import tempfile
from contextlib import contextmanager

import os

import aqt
from aqt import _run
from aqt.profiles import ProfileManager


@contextmanager
def temporaryUser(dirName, name="__Temporary Test User__", lang="en_US"):

    # prevent popping up language selection dialog
    original = ProfileManager._setDefaultLang
    def setDefaultLang(profileManager):
        profileManager.setLang(lang)

    ProfileManager._setDefaultLang = setDefaultLang

    pm = ProfileManager(base=dirName)

    if name in pm.profiles():
        raise Exception(f"Could not create a temporary user with name {name}")

    pm.create(name)
    pm.name = name

    yield name

    pm.remove(name)
    ProfileManager._setDefaultLang = original

@contextmanager
def temporaryDir(name):
    path = os.path.join(tempfile.gettempdir(), name)
    yield path
    shutil.rmtree(path)

def test_run():

    # we need a new user for the test
    with temporaryDir("anki_temp_base") as dirName:
        with temporaryUser(dirName) as userName:
            app = _run(argv=["anki", "-p", userName, "-b", dirName], exec=False)
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
