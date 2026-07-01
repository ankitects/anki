# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import shutil
import subprocess
from pathlib import Path

import pytest

from aqt.sound import _packagedCmd, is_audio_file


def test_is_audio_file_recognizes_common_formats():
    for ext in ("mp3", "wav", "ogg", "flac", "m4a", "opus", "spx", "oga"):
        assert is_audio_file(f"test.{ext}")


def test_is_audio_file_rejects_non_audio():
    for ext in ("mp4", "avi", "jpg", "png", "pdf"):
        assert not is_audio_file(f"test.{ext}")


def test_mpv_binary_runs():
    cmd, env = _packagedCmd(["mpv"])
    mpv = cmd[0]
    if not Path(mpv).is_absolute():
        mpv = shutil.which(mpv)
        if mpv is None:
            pytest.skip("mpv not found")

    result = subprocess.run([mpv, "--version"], env=env, capture_output=True)
    assert result.returncode == 0, result.stderr.decode()
