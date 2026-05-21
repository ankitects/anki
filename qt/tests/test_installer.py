# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import argparse
import shutil
from pathlib import Path
from typing import Any

import pytest
from tools.build_installer import (
    build,
    compile_sources,
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
    return dict(aqt_wheel=wheel_path, anki_wheel=wheel_path)


@pytest.fixture
def cmd_args(wheel_path: Path) -> argparse.Namespace:
    version = "0.0.1"
    return argparse.Namespace(version=version, **build_args(wheel_path))


@pytest.mark.parametrize(
    "platform, template",
    [
        ("win32", "windows-template"),
        ("darwin", "mac-template"),
        ("linux", "linux-template"),
        ("unknown", None),
    ],
)
def test_template_path(monkeypatch, platform: str, template: str | None) -> None:
    monkeypatch.setattr("sys.platform", platform)
    assert get_briefcase_template_path() == (
        (installer_dir / template) if template else None
    )


@pytest.mark.parametrize(
    "platform, root",
    [
        ("win32", "src"),
        ("darwin", "Resources"),
        ("linux", "anki-0.0.1"),
        ("unknown", None),
    ],
)
def test_sources_path(
    monkeypatch, out_dir: Path, platform: str, root: str | None
) -> None:
    monkeypatch.setattr("sys.platform", platform)
    sources_path = get_briefcase_sources_path(out_dir, "0.0.1")
    if root:
        assert sources_path.name == root
    else:
        assert sources_path is None


@pytest.mark.parametrize(
    "platform, output_format", [("linux", ["linux", "zip"]), ("unknown", [])]
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


def test_compile_pyc_skipped_on_unknown_platform(
    mocker, out_dir: Path, cmd_args: argparse.Namespace
) -> None:
    mocker.patch("tools.build_installer.get_briefcase_sources_path", return_value=False)
    assert compile_sources(out_dir, cmd_args.version) is False


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
        ("linux", "x86_64", "-linux-x86_64"),
        ("linux", "aarch64", "-linux-aarch64"),
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
        cmd_list.append(f"--{k}")
        cmd_list.append(str(v))
    return cmd_list


def test_main(mocker, out_dir: Path, wheel_path: Path) -> None:
    version_args = ["--version", "0.0.1"]

    build_mock = mocker.patch("tools.build_installer.build")
    args = main([*version_args, "build", *_to_cmd_list(build_args(wheel_path))])
    build_mock.assert_called_once_with(args)

    package_mock = mocker.patch("tools.build_installer.package")
    args = main([*version_args, "package"])
    package_mock.assert_called_once_with(args)


def test_build_and_package(out_dir: Path, cmd_args: argparse.Namespace) -> None:
    build(cmd_args)
    assert (out_dir / "LICENSE").exists()
    assert (out_dir / "CHANGELOG").exists()
    assert next(out_dir.rglob("qtwebengine_locales/*.pak"), None) is not None
    sources_root = get_briefcase_sources_path(out_dir, cmd_args.version)
    assert sources_root is not None
    for src_dir in (sources_root / "app", sources_root / "app_packages"):
        assert src_dir.exists()
        assert next(src_dir.rglob("*.py"), None) is None
        assert next(src_dir.rglob("*.pyc"), None) is not None

    package(cmd_args)
    package_path = next((out_dir / "dist").iterdir())
    assert package_path.stem.endswith(get_platform_suffix())
