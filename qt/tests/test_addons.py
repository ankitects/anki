# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os.path
from tempfile import TemporaryDirectory
from zipfile import ZipFile

import pytest
from mock import MagicMock

import aqt.addons as addons_mod
from aqt.addons import (
    AddonManager,
    DownloadError,
    DownloadOk,
    InstallError,
    InstallOk,
    SaveOk,
    _safe_addon_filename,
    download_and_save_addon,
    download_encountered_problem,
    package_name_valid,
)


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


def test_package_name_validation():
    assert not package_name_valid("")
    assert not package_name_valid("/")
    assert not package_name_valid("a/b")
    assert not package_name_valid("..")
    assert package_name_valid("ab")


@pytest.fixture
def addon_manager(tmp_path) -> AddonManager:
    adm = AddonManager(MagicMock())
    adm.mw.pm.addonFolder.return_value = tmp_path
    return adm


def test_install_extracts_safe_files(tmp_path, addon_manager):
    zfn = os.path.join(tmp_path, "addon.zip")
    with ZipFile(zfn, "w") as zfile:
        zfile.writestr("main.py", "content")
        zfile.writestr("../unsafe.txt", "content")
        zfile.writestr("subdir/helper.py", "content")
    with ZipFile(zfn) as zfile:
        addon_manager._install("12345", zfile)
    addon_dir = os.path.join(tmp_path, "12345")
    assert os.path.exists(os.path.join(addon_dir, "main.py"))
    assert os.path.exists(os.path.join(addon_dir, "subdir", "helper.py"))
    assert not os.path.exists(os.path.join(tmp_path, "unsafe.txt"))


def test_safe_addon_filename():
    assert _safe_addon_filename("Cool Add-on.ankiaddon", 1) == "Cool Add-on.ankiaddon"
    # directory components are stripped
    assert _safe_addon_filename("../evil.ankiaddon", 1) == "evil.ankiaddon"
    # empty/traversal names fall back to the id
    assert _safe_addon_filename("", 7) == "7.ankiaddon"
    assert _safe_addon_filename("..", 5) == "5.ankiaddon"
    # a missing extension is added
    assert _safe_addon_filename("noext", 9) == "noext.ankiaddon"
    # characters that are invalid on Windows (incl. the NTFS ADS ':') are replaced
    assert (
        _safe_addon_filename("invalid:char*name?.ankiaddon", 1)
        == "invalid_char_name_.ankiaddon"
    )


def _download_ok(data=b"payload", filename="My_Addon.ankiaddon"):
    return DownloadOk(
        data=data,
        filename=filename,
        mod_time=1,
        min_point_version=0,
        max_point_version=0,
        branch_index=0,
    )


def test_download_and_save_addon_writes_file(tmp_path, monkeypatch):
    monkeypatch.setattr(addons_mod, "download_addon", lambda client, id: _download_ok())

    id_, result = download_and_save_addon(MagicMock(), 123, str(tmp_path))

    assert id_ == 123
    assert isinstance(result, SaveOk)
    saved = os.path.join(str(tmp_path), "My_Addon.ankiaddon")
    assert os.path.exists(saved)
    with open(saved, "rb") as f:
        assert f.read() == b"payload"
    # underscores are turned into spaces and the extension dropped for display
    assert result.name == "My Addon"


def test_download_and_save_addon_avoids_overwrite(tmp_path, monkeypatch):
    monkeypatch.setattr(addons_mod, "download_addon", lambda client, id: _download_ok())

    first = download_and_save_addon(MagicMock(), 1, str(tmp_path))[1]
    second = download_and_save_addon(MagicMock(), 1, str(tmp_path))[1]

    assert isinstance(first, SaveOk)
    assert isinstance(second, SaveOk)
    # the second download must not clobber the first
    assert first.path != second.path
    assert os.path.exists(first.path)
    assert os.path.exists(second.path)
    assert os.path.basename(second.path) == "My_Addon (1).ankiaddon"


def test_download_and_save_addon_passes_through_errors(tmp_path, monkeypatch):
    monkeypatch.setattr(
        addons_mod,
        "download_addon",
        lambda client, id: DownloadError(status_code=404),
    )

    id_, result = download_and_save_addon(MagicMock(), 5, str(tmp_path))

    assert id_ == 5
    assert isinstance(result, DownloadError)
    assert not os.listdir(str(tmp_path))


def test_download_encountered_problem():
    # a saved-to-disk add-on is a success, not a problem
    assert not download_encountered_problem([(1, SaveOk(path="p", name="n"))])
    assert not download_encountered_problem(
        [(1, InstallOk(name="n", conflicts=set(), compatible=True))]
    )
    assert download_encountered_problem([(1, DownloadError(status_code=404))])
    assert download_encountered_problem([(1, InstallError(errmsg="x"))])
