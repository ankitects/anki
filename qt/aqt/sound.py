# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import atexit
import os
import re
import subprocess
import sys
import threading
import time
import wave
from abc import ABC, abstractmethod
from concurrent.futures import Future
from operator import itemgetter
from typing import TYPE_CHECKING, Any, Callable, Dict, List, Optional, Tuple

import aqt
from anki import hooks
from anki.cards import Card
from anki.sound import AV_REF_RE, AVTag, SoundOrVideoTag
from anki.utils import isLin, isMac, isWin, namedtmp
from aqt import gui_hooks
from aqt.mpv import MPV, MPVBase, MPVCommandError
from aqt.qt import *
from aqt.taskman import TaskManager
from aqt.utils import TR, restoreGeom, saveGeom, showWarning, startup_info, tr

if TYPE_CHECKING:
    from PyQt5.QtMultimedia import QAudioRecorder

# AV player protocol
##########################################################################

OnDoneCallback = Callable[[], None]


class Player(ABC):
    @abstractmethod
    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        """Play a file.

        When reimplementing, make sure to call
        gui_hooks.av_player_did_begin_playing(self, tag)
        on the main thread after playback begins.
        """

    @abstractmethod
    def rank_for_tag(self, tag: AVTag) -> Optional[int]:
        """How suited this player is to playing tag.

        AVPlayer will choose the player that returns the highest rank
        for a given tag.

        If None, this player can not play the tag.
        """

    def stop(self) -> None:
        """Optional.

        If implemented, the player must not call on_done() when the audio is stopped."""

    def seek_relative(self, secs: int) -> None:
        "Jump forward or back by secs. Optional."

    def toggle_pause(self) -> None:
        "Optional."

    def shutdown(self) -> None:
        "Do any cleanup required at program termination. Optional."


AUDIO_EXTENSIONS = {
    "3gp",
    "flac",
    "m4a",
    "mp3",
    "oga",
    "ogg",
    "opus",
    "spx",
    "wav",
}


def is_audio_file(fname: str) -> bool:
    ext = fname.split(".")[-1].lower()
    return ext in AUDIO_EXTENSIONS


class SoundOrVideoPlayer(Player):  # pylint: disable=abstract-method
    default_rank = 0

    def rank_for_tag(self, tag: AVTag) -> Optional[int]:
        if isinstance(tag, SoundOrVideoTag):
            return self.default_rank
        else:
            return None


class SoundPlayer(Player):  # pylint: disable=abstract-method
    default_rank = 0

    def rank_for_tag(self, tag: AVTag) -> Optional[int]:
        if isinstance(tag, SoundOrVideoTag) and is_audio_file(tag.filename):
            return self.default_rank
        else:
            return None


class VideoPlayer(Player):  # pylint: disable=abstract-method
    default_rank = 0

    def rank_for_tag(self, tag: AVTag) -> Optional[int]:
        if isinstance(tag, SoundOrVideoTag) and not is_audio_file(tag.filename):
            return self.default_rank
        else:
            return None


# Main playing interface
##########################################################################


class AVPlayer:
    players: List[Player] = []
    # when a new batch of audio is played, shoud the currently playing
    # audio be stopped?
    interrupt_current_audio = True

    def __init__(self) -> None:
        self._enqueued: List[AVTag] = []
        self.current_player: Optional[Player] = None

    def play_tags(self, tags: List[AVTag]) -> None:
        """Clear the existing queue, then start playing provided tags."""
        self.clear_queue_and_maybe_interrupt()
        self._enqueued = tags[:]
        self._play_next_if_idle()

    def stop_and_clear_queue(self) -> None:
        self._enqueued = []
        self._stop_if_playing()

    def clear_queue_and_maybe_interrupt(self) -> None:
        self._enqueued = []
        if self.interrupt_current_audio:
            self._stop_if_playing()

    def play_file(self, filename: str) -> None:
        self.play_tags([SoundOrVideoTag(filename=filename)])

    def insert_file(self, filename: str) -> None:
        self._enqueued.insert(0, SoundOrVideoTag(filename=filename))
        self._play_next_if_idle()

    def toggle_pause(self) -> None:
        if self.current_player:
            self.current_player.toggle_pause()

    def seek_relative(self, secs: int) -> None:
        if self.current_player:
            self.current_player.seek_relative(secs)

    def shutdown(self) -> None:
        self.stop_and_clear_queue()
        for player in self.players:
            player.shutdown()

    def _stop_if_playing(self) -> None:
        if self.current_player:
            self.current_player.stop()

    def _pop_next(self) -> Optional[AVTag]:
        if not self._enqueued:
            return None
        return self._enqueued.pop(0)

    def _on_play_finished(self) -> None:
        gui_hooks.av_player_did_end_playing(self.current_player)
        self.current_player = None
        self._play_next_if_idle()

    def _play_next_if_idle(self) -> None:
        if self.current_player:
            return

        next = self._pop_next()
        if next is not None:
            self._play(next)

    def _play(self, tag: AVTag) -> None:
        best_player = self._best_player_for_tag(tag)
        if best_player:
            self.current_player = best_player
            gui_hooks.av_player_will_play(tag)
            self.current_player.play(tag, self._on_play_finished)
        else:
            print("no players found for", tag)

    def _best_player_for_tag(self, tag: AVTag) -> Optional[Player]:
        ranked = []
        for p in self.players:
            rank = p.rank_for_tag(tag)
            if rank is not None:
                ranked.append((rank, p))

        ranked.sort(key=itemgetter(0))

        if ranked:
            return ranked[-1][1]
        else:
            return None


av_player = AVPlayer()

# Packaged commands
##########################################################################

# return modified command array that points to bundled command, and return
# required environment
def _packagedCmd(cmd: List[str]) -> Tuple[Any, Dict[str, str]]:
    cmd = cmd[:]
    env = os.environ.copy()
    if "LD_LIBRARY_PATH" in env:
        del env["LD_LIBRARY_PATH"]
    if isMac:
        dir = os.path.dirname(os.path.abspath(__file__))
        exeDir = os.path.abspath(dir + "/../../Resources/audio")
    else:
        exeDir = os.path.dirname(os.path.abspath(sys.argv[0]))
        if isWin and not cmd[0].endswith(".exe"):
            cmd[0] += ".exe"
    path = os.path.join(exeDir, cmd[0])
    if not os.path.exists(path):
        return cmd, env
    cmd[0] = path
    return cmd, env


# Platform hacks
##########################################################################

# legacy global for add-ons
si = startup_info()

# osx throws interrupted system call errors frequently
def retryWait(proc: subprocess.Popen) -> int:
    while 1:
        try:
            return proc.wait()
        except OSError:
            continue


# Simple player implementations
##########################################################################


class SimpleProcessPlayer(Player):  # pylint: disable=abstract-method
    "A player that invokes a new process for each tag to play."

    args: List[str] = []
    env: Optional[Dict[str, str]] = None

    def __init__(self, taskman: TaskManager) -> None:
        self._taskman = taskman
        self._terminate_flag = False
        self._process: Optional[subprocess.Popen] = None

    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        self._terminate_flag = False
        self._taskman.run_in_background(
            lambda: self._play(tag), lambda res: self._on_done(res, on_done)
        )

    def stop(self) -> None:
        self._terminate_flag = True

    # note: mplayer implementation overrides this
    def _play(self, tag: AVTag) -> None:
        assert isinstance(tag, SoundOrVideoTag)
        self._process = subprocess.Popen(
            self.args + [tag.filename],
            env=self.env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        self._wait_for_termination(tag)

    def _wait_for_termination(self, tag: AVTag) -> None:
        self._taskman.run_on_main(
            lambda: gui_hooks.av_player_did_begin_playing(self, tag)
        )

        while True:
            # should we abort playing?
            if self._terminate_flag:
                self._process.terminate()
                self._process = None
                return

            # wait for completion
            try:
                self._process.wait(0.1)
                if self._process.returncode != 0:
                    print(f"player got return code: {self._process.returncode}")
                self._process = None
                return
            except subprocess.TimeoutExpired:
                # process still running, repeat loop
                pass

    def _on_done(self, ret: Future, cb: OnDoneCallback) -> None:
        try:
            ret.result()
        except FileNotFoundError:
            showWarning(tr(TR.MEDIA_SOUND_AND_VIDEO_ON_CARDS_WILL))
            # must call cb() here, as we don't currently have another way
            # to flag to av_player that we've stopped
        cb()


class SimpleMpvPlayer(SimpleProcessPlayer, VideoPlayer):
    default_rank = 1

    args, env = _packagedCmd(
        [
            "mpv",
            "--no-terminal",
            "--force-window=no",
            "--ontop",
            "--audio-display=no",
            "--keep-open=no",
            "--input-media-keys=no",
            "--autoload-files=no",
        ]
    )

    def __init__(self, taskman: TaskManager, base_folder: str) -> None:
        super().__init__(taskman)
        self.args += ["--config-dir=" + base_folder]


class SimpleMplayerPlayer(SimpleProcessPlayer, SoundOrVideoPlayer):
    args, env = _packagedCmd(["mplayer", "-really-quiet", "-noautosub"])
    if isWin:
        args += ["-ao", "win32"]


# MPV
##########################################################################


class MpvManager(MPV, SoundOrVideoPlayer):

    if not isLin:
        default_argv = MPVBase.default_argv + [
            "--input-media-keys=no",
        ]

    def __init__(self, base_path: str) -> None:
        mpvPath, self.popenEnv = _packagedCmd(["mpv"])
        self.executable = mpvPath[0]
        self._on_done: Optional[OnDoneCallback] = None
        self.default_argv += ["--config-dir=" + base_path]
        super().__init__(window_id=None, debug=False)

    def on_init(self) -> None:
        # if mpv dies and is restarted, tell Anki the
        # current file is done
        if self._on_done:
            self._on_done()

        try:
            self.command("keybind", "q", "stop")
            self.command("keybind", "Q", "stop")
            self.command("keybind", "CLOSE_WIN", "stop")
            self.command("keybind", "ctrl+w", "stop")
            self.command("keybind", "ctrl+c", "stop")
        except MPVCommandError:
            print("mpv too old for key rebinding")

    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        assert isinstance(tag, SoundOrVideoTag)
        self._on_done = on_done
        filename = hooks.media_file_filter(tag.filename)
        path = os.path.join(os.getcwd(), filename)

        self.command("loadfile", path, "append-play")
        gui_hooks.av_player_did_begin_playing(self, tag)

    def stop(self) -> None:
        self.command("stop")

    def toggle_pause(self) -> None:
        self.set_property("pause", not self.get_property("pause"))

    def seek_relative(self, secs: int) -> None:
        self.command("seek", secs, "relative")

    def on_property_idle_active(self, value: bool) -> None:
        if value and self._on_done:
            self._on_done()

    def shutdown(self) -> None:
        self.close()

    # Legacy, not used
    ##################################################

    togglePause = toggle_pause
    seekRelative = seek_relative

    def queueFile(self, file: str) -> None:
        return

    def clearQueue(self) -> None:
        return


# Mplayer in slave mode
##########################################################################


class SimpleMplayerSlaveModePlayer(SimpleMplayerPlayer):
    def __init__(self, taskman: TaskManager):
        super().__init__(taskman)
        self.args.append("-slave")

    def _play(self, tag: AVTag) -> None:
        assert isinstance(tag, SoundOrVideoTag)

        filename = hooks.media_file_filter(tag.filename)

        self._process = subprocess.Popen(
            self.args + [filename],
            env=self.env,
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            startupinfo=startup_info(),
        )
        self._wait_for_termination(tag)

    def command(self, *args: Any) -> None:
        """Send a command over the slave interface.

        The trailing newline is automatically added."""
        str_args = [str(x) for x in args]
        if self._process:
            self._process.stdin.write(" ".join(str_args).encode("utf8") + b"\n")
            self._process.stdin.flush()

    def seek_relative(self, secs: int) -> None:
        self.command("seek", secs, 0)

    def toggle_pause(self) -> None:
        self.command("pause")


# MP3 transcoding
##########################################################################


def _encode_mp3(src_wav: str, dst_mp3: str) -> None:
    cmd = ["lame", src_wav, dst_mp3, "--noreplaygain", "--quiet"]
    cmd, env = _packagedCmd(cmd)
    try:
        retcode = retryWait(subprocess.Popen(cmd, startupinfo=startup_info(), env=env))
    except Exception as e:
        raise Exception(tr(TR.MEDIA_ERROR_RUNNING, val=" ").join(cmd)) from e
    if retcode != 0:
        raise Exception(tr(TR.MEDIA_ERROR_RUNNING, val=" ").join(cmd))

    os.unlink(src_wav)


def encode_mp3(mw: aqt.AnkiQt, src_wav: str, on_done: Callable[[str], None]) -> None:
    "Encode the provided wav file to .mp3, and call on_done() with the path."
    dst_mp3 = src_wav.replace(".wav", "%d.mp3" % time.time())

    def _on_done(fut: Future):
        fut.result()
        on_done(dst_mp3)

    mw.taskman.run_in_background(lambda: _encode_mp3(src_wav, dst_mp3), _on_done)


# Recording dialog
##########################################################################


class RecordDialog(QDialog):
    _recorder: QAudioRecorder

    def __init__(
        self,
        parent: QWidget,
        mw: aqt.AnkiQt,
        on_success: Callable[[str], None],
    ):
        QDialog.__init__(self, parent)
        self._parent = parent
        self.mw = mw
        self._on_success = on_success

        self._start_recording()
        self._setup_dialog()

    def _setup_dialog(self):
        self.setWindowTitle("Anki")
        icon = QLabel()
        icon.setPixmap(QPixmap(":/icons/media-record.png"))
        self.label = QLabel("...")
        hbox = QHBoxLayout()
        hbox.addWidget(icon)
        hbox.addWidget(self.label)
        v = QVBoxLayout()
        v.addLayout(hbox)
        buts = QDialogButtonBox.Save | QDialogButtonBox.Cancel
        b = QDialogButtonBox(buts)  # type: ignore
        v.addWidget(b)
        self.setLayout(v)
        save_button = b.button(QDialogButtonBox.Save)
        save_button.setDefault(True)
        save_button.setAutoDefault(True)
        qconnect(save_button.clicked, self.accept)
        cancel_button = b.button(QDialogButtonBox.Cancel)
        cancel_button.setDefault(False)
        cancel_button.setAutoDefault(False)
        qconnect(cancel_button.clicked, self.reject)
        restoreGeom(self, "audioRecorder2")
        self.show()

    def _save_diag(self):
        saveGeom(self, "audioRecorder2")

    def _start_recording(self):
        from PyQt5.QtMultimedia import QAudioRecorder

        # start recording
        self._recorder = QAudioRecorder(self._parent)
        self._output = namedtmp("rec.wav")
        audio = self._recorder.audioSettings()
        audio.setSampleRate(44100)
        audio.setChannelCount(1)
        self._recorder.setEncodingSettings(
            audio,
            self._recorder.videoSettings(),
            "audio/x-wav",
        )
        self._recorder.setOutputLocation(QUrl.fromLocalFile(self._output))
        self._recorder.setMuted(True)
        self._recorder.record()

        self._timer = t = QTimer(self._parent)
        t.timeout.connect(self._on_timer)  # type: ignore
        t.setSingleShot(False)
        t.start(100)

    def _on_timer(self):
        duration = self._recorder.duration()
        # disable mute after recording starts to avoid clicks/pops
        if duration < 300:
            return
        if self._recorder.isMuted():
            self._recorder.setMuted(False)
        self.label.setText(
            tr(TR.MEDIA_RECORDINGTIME, secs="%0.1f" % (duration / 1000.0))
        )

    def accept(self):
        try:
            self._recorder.stop()
            self._save_diag()
        finally:
            QDialog.accept(self)

        self._on_success(self._output)

    def reject(self):
        try:
            self._recorder.stop()
            os.unlink(self._output)
        finally:
            QDialog.reject(self)


def record_audio(
    parent: QWidget, mw: aqt.AnkiQt, encode: bool, on_done: Callable[[str], None]
):
    def after_record(path: str):
        if not encode:
            on_done(path)
        else:
            encode_mp3(mw, path, on_done)

    _diag = RecordDialog(parent, mw, after_record)


# Legacy audio interface
##########################################################################
# these will be removed in the future


def clearAudioQueue() -> None:
    av_player.stop_and_clear_queue()


def play(filename: str) -> None:
    av_player.play_file(filename)


def playFromText(text) -> None:
    print("playFromText() deprecated")


# legacy globals
_player = play
_queueEraser = clearAudioQueue
mpvManager: Optional["MpvManager"] = None

# add everything from this module into anki.sound for backwards compat
_exports = [i for i in locals().items() if not i[0].startswith("__")]
for (k, v) in _exports:
    sys.modules["anki.sound"].__dict__[k] = v

# Tag handling
##########################################################################


def av_refs_to_play_icons(text: str) -> str:
    """Add play icons into the HTML.

    When clicked, the icon will call eg pycmd('play:q:1').
    """

    def repl(match: re.Match) -> str:
        return f"""
<a class="replay-button soundLink" href=# onclick="pycmd('{match.group(1)}'); return false;">
    <svg class="playImage" viewBox="0 0 64 64" version="1.1">
        <circle cx="32" cy="32" r="29" />
        <path d="M56.502,32.301l-37.502,20.101l0.329,-40.804l37.173,20.703Z" />
    </svg>
</a>"""

    return AV_REF_RE.sub(repl, text)


def play_clicked_audio(pycmd: str, card: Card) -> None:
    """eg. if pycmd is 'play:q:0', play the first audio on the question side."""
    play, context, str_idx = pycmd.split(":")
    idx = int(str_idx)
    if context == "q":
        tags = card.question_av_tags()
    else:
        tags = card.answer_av_tags()
    av_player.play_tags([tags[idx]])


# Init defaults
##########################################################################


def setup_audio(taskman: TaskManager, base_folder: str) -> None:
    # legacy global var
    global mpvManager

    try:
        mpvManager = MpvManager(base_folder)
    except FileNotFoundError:
        print("mpv not found, reverting to mplayer")
    except aqt.mpv.MPVProcessError:
        print("mpv too old, reverting to mplayer")

    if mpvManager is not None:
        av_player.players.append(mpvManager)

        if isWin:
            mpvPlayer = SimpleMpvPlayer(taskman, base_folder)
            av_player.players.append(mpvPlayer)
    else:
        mplayer = SimpleMplayerSlaveModePlayer(taskman)
        av_player.players.append(mplayer)

    # tts support
    if isMac:
        from aqt.tts import MacTTSPlayer

        av_player.players.append(MacTTSPlayer(taskman))
    elif isWin:
        from aqt.tts import WindowsTTSPlayer

        av_player.players.append(WindowsTTSPlayer(taskman))

    # cleanup at shutdown
    atexit.register(av_player.shutdown)
