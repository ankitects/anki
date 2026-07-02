# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import shutil
import subprocess
import wave
from pathlib import Path
from unittest.mock import MagicMock

import pytest

import aqt
from anki.sound import SoundOrVideoTag
from anki.utils import is_mac, is_win
from aqt.sound import MpvManager, _packagedCmd, is_audio_file


def test_is_audio_file_recognizes_common_formats():
    for ext in ("mp3", "wav", "ogg", "flac", "m4a", "opus", "spx", "oga"):
        assert is_audio_file(f"test.{ext}")


def test_is_audio_file_is_case_insensitive():
    for ext in ("MP3", "WAV", "OGG", "FLAC"):
        assert is_audio_file(f"test.{ext}")


def test_is_audio_file_rejects_non_audio():
    # mp4/avi are video-only; jpg/png/pdf are not media Anki plays via mpv.
    for ext in ("mp4", "avi", "jpg", "png", "pdf"):
        assert not is_audio_file(f"test.{ext}")


def test_is_audio_file_rejects_no_extension():
    assert not is_audio_file("audiofile")


def test_packagedcmd_returns_absolute_path_when_anki_audio_available():
    # _packagedCmd should prefer the binary bundled in anki_audio over a
    # system-wide one on macOS and Windows. This is the regression caught by
    # issue #5015: an updated anki_audio build was not being picked up.
    if not (is_mac or is_win):
        pytest.skip("anki_audio binary preference is only used on macOS/Windows")

    try:
        from pathlib import Path as _Path

        import anki_audio

        audio_pkg_path = _Path(anki_audio.__file__).parent
    except ImportError:
        pytest.skip("anki_audio package not installed")

    cmd, _env = _packagedCmd(["mpv"])
    mpv_path = Path(cmd[0])

    assert mpv_path.is_absolute(), "expected an absolute path to the packaged binary"
    assert str(audio_pkg_path) in str(mpv_path), (
        f"expected binary inside anki_audio package at {audio_pkg_path}, got {mpv_path}"
    )


def _resolved_mpv_command(args: list[str]) -> tuple[list[str], dict[str, str]]:
    cmd, env = _packagedCmd(args)
    mpv = cmd[0]
    if not Path(mpv).is_absolute():
        mpv = shutil.which(mpv)
        if mpv is None:
            pytest.skip("mpv not found")
        cmd[0] = mpv

    return cmd, env


def test_mpv_binary_runs():
    cmd, env = _resolved_mpv_command(["mpv"])
    result = subprocess.run(cmd + ["--version"], env=env, capture_output=True)
    assert result.returncode == 0, result.stderr.decode()


@pytest.fixture
def generated_wav(tmp_path: Path) -> Path:
    wav_path = tmp_path / "silence.wav"
    with wave.open(str(wav_path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(44_100)
        wav.writeframes(b"\0\0" * 4_410)
    return wav_path


def test_mpv_can_play_generated_wav(generated_wav: Path):
    cmd, env = _resolved_mpv_command(
        [
            "mpv",
            "--no-terminal",
            "--force-window=no",
            "--audio-display=no",
            "--keep-open=no",
            "--autoload-files=no",
            "--ao=null",
            "--vo=null",
            "--",
            str(generated_wav),
        ]
    )

    result = subprocess.run(cmd, env=env, capture_output=True, timeout=10)
    assert result.returncode == 0, result.stderr.decode()


def test_mpvmanager_can_play_generated_wav(
    monkeypatch, tmp_path: Path, generated_wav: Path
):
    monkeypatch.setattr(
        MpvManager, "default_argv", MpvManager.default_argv + ["--ao=null", "--vo=null"]
    )
    mock_mw = MagicMock()
    mock_mw.taskman.run_in_background.side_effect = (
        lambda task, on_done=None, **kwargs: task()
    )
    monkeypatch.setattr(aqt, "mw", mock_mw)
    manager = MpvManager(tmp_path, tmp_path)
    manager.play(SoundOrVideoTag(filename=str(generated_wav.name)), lambda _: None)
