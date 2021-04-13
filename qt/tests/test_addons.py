# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os.path
from tempfile import TemporaryDirectory
from zipfile import ZipFile

from mock import MagicMock

from aqt.addons import AddonManager, extract_update_info


def test_readMinimalManifest():
    assertReadManifest(
        '{"package": "yes", "name": "no"}', {"package": "yes", "name": "no"}
    )


def test_readExtraKeys():
    assertReadManifest(
        '{"package": "a", "name": "b", "mod": 3, "conflicts": ["d", "e"]}',
        {"package": "a", "name": "b", "mod": 3, "conflicts": ["d", "e"]},
    )


def test_invalidManifest():
    assertReadManifest('{"one": 1}', {})


def test_mustHaveName():
    assertReadManifest('{"package": "something"}', {})


def test_mustHavePackage():
    assertReadManifest('{"name": "something"}', {})


def test_invalidJson():
    assertReadManifest("this is not a JSON dictionary", {})


def test_missingManifest():
    assertReadManifest(
        '{"package": "what", "name": "ever"}', {}, nameInZip="not-manifest.bin"
    )


def test_ignoreExtraKeys():
    assertReadManifest(
        '{"package": "a", "name": "b", "game": "c"}', {"package": "a", "name": "b"}
    )


def test_conflictsMustBeStrings():
    assertReadManifest(
        '{"package": "a", "name": "b", "conflicts": ["c", 4, {"d": "e"}]}', {}
    )


def assertReadManifest(contents, expectedManifest, nameInZip="manifest.json"):
    with TemporaryDirectory() as td:
        zfn = os.path.join(td, "addon.zip")
        with ZipFile(zfn, "w") as zfile:
            zfile.writestr(nameInZip, contents)

        adm = AddonManager(MagicMock())

        with ZipFile(zfn, "r") as zfile:
            assert adm.readManifestFile(zfile) == expectedManifest


def test_update_info():
    json_info = dict(
        id=999,
        branches=[
            {"minpt": 0, "maxpt": -15, "fmod": 222},
            {"minpt": 20, "maxpt": -25, "fmod": 333},
            {"minpt": 30, "maxpt": 35, "fmod": 444},
        ],
    )

    r = extract_update_info(5, 0, json_info)
    assert r.current_branch_max_point_ver == -15
    assert r.suitable_branch_last_modified == 222

    r = extract_update_info(5, 1, json_info)
    assert r.current_branch_max_point_ver == -25
    assert r.suitable_branch_last_modified == 222

    r = extract_update_info(19, 1, json_info)
    assert r.current_branch_max_point_ver == -25
    assert r.suitable_branch_last_modified == 0

    r = extract_update_info(20, 1, json_info)
    assert r.current_branch_max_point_ver == -25
    assert r.suitable_branch_last_modified == 333
