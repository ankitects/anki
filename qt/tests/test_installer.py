# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import argparse
import shutil
from pathlib import Path
from typing import Any
from unittest.mock import MagicMock

import pytest
from tools.build_installer import (
    _find_fcitx_file,
    build,
    bundle_fcitx,
    get_briefcase_config_args,
    get_briefcase_output_format,
    get_briefcase_sources_path,
    get_briefcase_template_path,
    get_platform_suffix,
    get_signing_args,
    installer_dir,
    main,
    normalize_wheel_path,
    package,
)

support_dir = Path(__file__).parent / "support"
dummy_wheel_path = support_dir / "dummy_package-0.1.0-py3-none-any.whl"


@pytest.fixture
def out_dir(tmp_path, monkeypatch) -> Path:
    monkeypatch.setattr("tools.build_installer.out_dir", tmp_path)
    shutil.copy(dummy_wheel_path, tmp_path / dummy_wheel_path.name)
    return tmp_path


@pytest.fixture
def wheel_path(out_dir: Path) -> Path:
    return out_dir / dummy_wheel_path.name


def build_args(wheel_path: Path) -> dict[str, Any]:
    return dict(aqt_wheel=wheel_path, anki_wheel=wheel_path, skip_fcitx=True)


@pytest.fixture
def cmd_args(wheel_path: Path) -> argparse.Namespace:
    version = "0.0.1"
    return argparse.Namespace(version=version, **build_args(wheel_path))


@pytest.fixture
def bundle_dir_with_fcitx(
    monkeypatch, mocker, tmp_path: Path
) -> tuple[Path, MagicMock]:
    sources = tmp_path / "sources"
    pyqt6_qt6 = sources / "app_packages" / "PyQt6" / "Qt6"
    pic_dest = pyqt6_qt6 / "plugins" / "platforminputcontexts"
    pic_dest.mkdir(parents=True)

    fake_plugin = tmp_path / "libfcitx5platforminputcontextplugin.so"
    fake_plugin.touch()
    fake_dbus = tmp_path / "libFcitx5Qt6DBusAddons.so.1"
    fake_dbus.touch()

    monkeypatch.setattr("sys.platform", "linux")
    mocker.patch(
        "tools.build_installer.get_briefcase_sources_path", return_value=sources
    )
    mocker.patch("tools.build_installer._find_fcitx_file", return_value=fake_plugin)

    def mock_copy2(src: Path, dst: Path) -> None:
        dst = Path(dst)
        if dst.is_dir():
            (dst / Path(src).name).touch()

    mocker.patch("tools.build_installer.shutil.copy2", side_effect=mock_copy2)

    def intercept_glob(_, pattern: str):
        if "libFcitx5Qt6DBusAddons" in pattern:
            return iter([fake_dbus])
        return iter([])

    mocker.patch.object(Path, "glob", intercept_glob)
    mock_patchelf = mocker.patch("subprocess.check_call")
    return (tmp_path, mock_patchelf)


@pytest.mark.parametrize(
    "platform, template",
    [
        ("win32", "windows-template"),
        ("darwin", "mac-template"),
        ("linux", "linux-template"),
    ],
)
def test_template_path(monkeypatch, platform: str, template: str) -> None:
    monkeypatch.setattr("sys.platform", platform)
    assert get_briefcase_template_path() == (installer_dir / template)


@pytest.mark.parametrize(
    "platform, root",
    [
        ("win32", "src"),
        ("darwin", "Resources"),
        ("linux", "anki"),
    ],
)
def test_sources_path(monkeypatch, tmp_path: Path, platform: str, root: str) -> None:
    monkeypatch.setattr("sys.platform", platform)
    sources_path = get_briefcase_sources_path(tmp_path)
    assert sources_path.name == root


@pytest.mark.parametrize(
    "platform, output_format", [("linux", ["linux", "zip"]), ("win32", [])]
)
def test_output_format(monkeypatch, platform: str, output_format: list[str]) -> None:
    monkeypatch.setattr("sys.platform", platform)
    assert get_briefcase_output_format() == output_format


def test_briefcase_config(out_dir: Path, cmd_args: argparse.Namespace) -> None:
    config = get_briefcase_config_args(cmd_args)
    assert f'version="{cmd_args.version}"' in config
    assert (
        f'requires=["{normalize_wheel_path(cmd_args.aqt_wheel)}[qt,audio]","{normalize_wheel_path(cmd_args.anki_wheel)}"]'
        in config
    )
    assert any(s.startswith("template=") for s in config)


def test_compile_fails_loudly(
    mocker, out_dir: Path, cmd_args: argparse.Namespace
) -> None:
    mocker.patch("compileall.compile_dir", return_value=False)
    with pytest.raises(RuntimeError):
        build(cmd_args)


def test_signing_args(monkeypatch) -> None:
    monkeypatch.setenv("SIGN_IDENTITY", "")
    assert get_signing_args() == ["--adhoc-sign"]
    monkeypatch.setenv("SIGN_IDENTITY", "foo")
    assert get_signing_args() == ["--identity", "foo"]


@pytest.mark.parametrize(
    "platform, machine, suffix",
    [
        ("win32", "AMD64", "-win-x64"),
        ("win32", "ARM64", "-win-arm64"),
        ("darwin", "arm64", "-mac-apple"),
        ("darwin", "x86_64", "-mac-intel"),
        ("linux", "x86_64", "-linux-x86_64.tar"),
        ("linux", "aarch64", "-linux-aarch64.tar"),
        ("unknown", "unknown", ""),
    ],
)
def test_platform_suffix(monkeypatch, platform: str, machine: str, suffix: str) -> None:
    monkeypatch.setattr("sys.platform", platform)
    monkeypatch.setattr("platform.machine", lambda: machine)
    assert get_platform_suffix() == suffix


def _to_cmd_list(parsed: dict[str, str]) -> list[str]:
    cmd_list = []
    for k, v in parsed.items():
        print(k, v)
        if isinstance(v, bool):
            if v is True:
                cmd_list.append(f"--{k}")
        else:
            cmd_list.append(f"--{k}")
            cmd_list.append(str(v))
    return cmd_list


def test_main(mocker, wheel_path: Path) -> None:
    version_args = ["--version", "0.0.1"]

    build_mock = mocker.patch("tools.build_installer.build")
    args = main([*version_args, "build", *_to_cmd_list(build_args(wheel_path))])
    build_mock.assert_called_once_with(args)

    package_mock = mocker.patch("tools.build_installer.package")
    args = main([*version_args, "package"])
    package_mock.assert_called_once_with(args)


def test_find_fcitx_file_returns_match(tmp_path: Path) -> None:
    plugin = tmp_path / "libfcitx5platforminputcontextplugin.so"
    plugin.touch()
    assert _find_fcitx_file([tmp_path], plugin.name) == plugin


def test_find_fcitx_file_returns_none_when_missing(tmp_path: Path) -> None:
    assert _find_fcitx_file([tmp_path], "nonexistent.so") is None


def test_bundle_fcitx_raises_when_plugin_missing(
    monkeypatch, mocker, tmp_path: Path
) -> None:
    monkeypatch.setattr("sys.platform", "linux")
    mocker.patch(
        "tools.build_installer.get_briefcase_sources_path", return_value=tmp_path
    )
    mocker.patch("tools.build_installer._find_fcitx_file", return_value=None)
    with pytest.raises(RuntimeError, match="fcitx5-qt6 plugin not found"):
        bundle_fcitx(tmp_path)


def test_bundle_fcitx_copies_and_patches(
    bundle_dir_with_fcitx: tuple[Path, MagicMock],
) -> None:
    bundle_dir, mock_patchelf = bundle_dir_with_fcitx
    bundle_fcitx(bundle_dir)
    assert mock_patchelf.call_count == 2


@pytest.mark.parametrize(
    "platform, called", [("linux", True), ("darwin", False), ("win32", False)]
)
def test_bundle_fcitx_skipped_if_not_linux(
    monkeypatch,
    mocker,
    bundle_dir_with_fcitx: tuple[Path, MagicMock],
    platform: str,
    called: bool,
) -> None:
    monkeypatch.setattr("sys.platform", platform)
    mock = mocker.patch("tools.build_installer.get_briefcase_sources_path")
    bundle_dir, _ = bundle_dir_with_fcitx
    bundle_fcitx(bundle_dir)
    if called:
        mock.assert_called_once()
    else:
        mock.assert_not_called()


def test_build_and_package(out_dir: Path, cmd_args: argparse.Namespace) -> None:
    build(cmd_args)
    assert (out_dir / "LICENSE").exists()
    assert (out_dir / "CHANGELOG").exists()
    assert next(out_dir.rglob("qtwebengine_locales/*.pak"), None) is not None
    sources_root = get_briefcase_sources_path(out_dir)
    for src_dir in (sources_root / "app", sources_root / "app_packages"):
        assert src_dir.exists()
        assert next(src_dir.rglob("*.py"), None) is None
        assert next(src_dir.rglob("*.pyc"), None) is not None

    package(cmd_args)
    package_path = next((out_dir / "dist").iterdir())
    assert package_path.stem.endswith(get_platform_suffix())
