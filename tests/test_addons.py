import os.path
from nose.tools import assert_equals
from mock import MagicMock
from tempfile import TemporaryDirectory
from zipfile import ZipFile

from aqt.addons import AddonManager


def test_readMinimalManifest():
    assertReadManifest(
        '{"package": "yes", "name": "no"}',
        {"package": "yes", "name": "no"}
    )


def test_readExtraKeys():
    assertReadManifest(
        '{"package": "a", "name": "b", "mod": 3, "conflicts": ["d", "e"]}',
        {"package": "a", "name": "b", "mod": 3, "conflicts": ["d", "e"]}
    )


def test_invalidManifest():
    assertReadManifest(
        '{"one": 1}',
        {}
    )


def test_mustHaveName():
    assertReadManifest(
        '{"package": "something"}',
        {}
    )


def test_mustHavePackage():
    assertReadManifest(
        '{"name": "something"}',
        {}
    )


def test_invalidJson():
    assertReadManifest(
        'this is not a JSON dictionary',
        {}
    )


def test_missingManifest():
    assertReadManifest(
        '{"package": "what", "name": "ever"}',
        {},
        nameInZip="not-manifest.bin"
    )


def test_ignoreExtraKeys():
    assertReadManifest(
        '{"package": "a", "name": "b", "game": "c"}',
        {"package": "a", "name": "b"}
    )


def test_conflictsMustBeStrings():
    assertReadManifest(
        '{"package": "a", "name": "b", "conflicts": ["c", 4, {"d": "e"}]}',
        {}
    )


def assertReadManifest(contents, expectedManifest, nameInZip="manifest.json"):
    with TemporaryDirectory() as td:
        zfn = os.path.join(td, "addon.zip")
        with ZipFile(zfn, "w") as zfile:
            zfile.writestr(nameInZip, contents)

        adm = AddonManager(MagicMock())

        with ZipFile(zfn, "r") as zfile:
            assert_equals(adm.readManifestFile(zfile), expectedManifest)
