# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import atexit
import os
import platform
import re
import subprocess
import sys
import threading
import time
import wave
from abc import ABC, abstractmethod
from concurrent.futures import Future
from operator import itemgetter
from typing import TYPE_CHECKING, Any, Callable, Dict, List, Optional, Tuple, cast

from markdown import markdown

import aqt
from anki import hooks
from anki.cards import Card
from anki.sound import AV_REF_RE, AVTag, SoundOrVideoTag
from anki.types import assert_exhaustive
from anki.utils import isLin, isMac, isWin, namedtmp
from aqt import gui_hooks
from aqt.mpv import MPV, MPVBase, MPVCommandError
from aqt.profiles import RecordingDriver
from aqt.qt import *
from aqt.taskman import TaskManager
from aqt.utils import (
    disable_help_button,
    restoreGeom,
    saveGeom,
    showWarning,
    startup_info,
    tooltip,
    tr,
)

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
    # when a new batch of audio is played, should the currently playing
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
            tooltip(f"no players found for {tag}")

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
        exeDir = os.path.abspath(f"{dir}/../../Resources/audio")
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
            showWarning(tr.media_sound_and_video_on_cards_will())
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
        self.args += [f"--config-dir={base_folder}"]


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
        self.default_argv += [f"--config-dir={base_path}"]
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

        self.command("loadfile", path, "replace", "pause=no")
        gui_hooks.av_player_did_begin_playing(self, tag)

    def stop(self) -> None:
        self.command("stop")

    def toggle_pause(self) -> None:
        self.command("cycle", "pause")

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
    def __init__(self, taskman: TaskManager) -> None:
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
        raise Exception(tr.media_error_running(val=" ").join(cmd)) from e
    if retcode != 0:
        raise Exception(tr.media_error_running(val=" ").join(cmd))

    os.unlink(src_wav)


def encode_mp3(mw: aqt.AnkiQt, src_wav: str, on_done: Callable[[str], None]) -> None:
    "Encode the provided wav file to .mp3, and call on_done() with the path."
    dst_mp3 = src_wav.replace(".wav", "%d.mp3" % time.time())

    def _on_done(fut: Future) -> None:
        if exc := fut.exception():
            print(exc)
            showWarning(tr.editing_couldnt_record_audio_have_you_installed())
            return

        on_done(dst_mp3)

    mw.taskman.run_in_background(lambda: _encode_mp3(src_wav, dst_mp3), _on_done)


# Recording interface
##########################################################################


class Recorder(ABC):
    # seconds to wait before recording
    STARTUP_DELAY = 0.3

    def __init__(self, output_path: str) -> None:
        self.output_path = output_path

    def start(self, on_done: Callable[[], None]) -> None:
        "Start recording, then call on_done() when started."
        self._started_at = time.time()
        on_done()

    def stop(self, on_done: Callable[[str], None]) -> None:
        "Stop recording, then call on_done() when finished."
        on_done(self.output_path)

    def duration(self) -> float:
        "Seconds since recording started."
        return time.time() - self._started_at

    def on_timer(self) -> None:
        "Will be called periodically."


# QAudioInput recording
##########################################################################


class QtAudioInputRecorder(Recorder):
    def __init__(self, output_path: str, mw: aqt.AnkiQt, parent: QWidget) -> None:
        super().__init__(output_path)

        self.mw = mw
        self._parent = parent

        from PyQt5.QtMultimedia import QAudioDeviceInfo, QAudioFormat, QAudioInput

        format = QAudioFormat()
        format.setChannelCount(1)
        format.setSampleRate(44100)
        format.setSampleSize(16)
        format.setCodec("audio/pcm")
        format.setByteOrder(QAudioFormat.LittleEndian)
        format.setSampleType(QAudioFormat.SignedInt)

        device = QAudioDeviceInfo.defaultInputDevice()
        if not device.isFormatSupported(format):
            format = device.nearestFormat(format)
            print("format changed")
            print("channels", format.channelCount())
            print("rate", format.sampleRate())
            print("size", format.sampleSize())
        self._format = format

        self._audio_input = QAudioInput(device, format, parent)

    def start(self, on_done: Callable[[], None]) -> None:
        self._iodevice = self._audio_input.start()
        self._buffer = b""
        self._iodevice.readyRead.connect(self._on_read_ready)  # type: ignore
        super().start(on_done)

    def _on_read_ready(self) -> None:
        self._buffer += cast(bytes, self._iodevice.readAll())

    def stop(self, on_done: Callable[[str], None]) -> None:
        def on_stop_timer() -> None:
            # read anything remaining in buffer & stop
            self._on_read_ready()
            self._audio_input.stop()

            if err := self._audio_input.error():
                showWarning(f"recording failed: {err}")
                return

            def write_file() -> None:
                # swallow the first 300ms to allow audio device to quiesce
                wait = int(44100 * self.STARTUP_DELAY)
                if len(self._buffer) <= wait:
                    return
                self._buffer = self._buffer[wait:]

                # write out the wave file
                wf = wave.open(self.output_path, "wb")
                wf.setnchannels(self._format.channelCount())
                wf.setsampwidth(self._format.sampleSize() // 8)
                wf.setframerate(self._format.sampleRate())
                wf.writeframes(self._buffer)
                wf.close()

            def and_then(fut: Future) -> None:
                fut.result()
                Recorder.stop(self, on_done)

            self.mw.taskman.run_in_background(write_file, and_then)

        # schedule the stop for half a second in the future,
        # to avoid truncating the end of the recording
        self._stop_timer = t = QTimer(self._parent)
        t.timeout.connect(on_stop_timer)  # type: ignore
        t.setSingleShot(True)
        t.start(500)


# PyAudio recording
##########################################################################

try:
    import pyaudio
except:
    pyaudio = None


PYAU_CHANNELS = 1
PYAU_INPUT_INDEX: Optional[int] = None


class PyAudioThreadedRecorder(threading.Thread):
    def __init__(self, output_path: str, startup_delay: float) -> None:
        threading.Thread.__init__(self)
        self._output_path = output_path
        self._startup_delay = startup_delay
        self.finish = False
        # though we're using pyaudio here, we rely on Qt to trigger
        # the permission prompt on macOS
        if isMac and qtminor > 12:
            from PyQt5.QtMultimedia import QAudioDeviceInfo

            QAudioDeviceInfo.defaultInputDevice()

    def run(self) -> None:
        chunk = 1024
        p = pyaudio.PyAudio()

        rate = int(p.get_default_input_device_info()["defaultSampleRate"])
        PYAU_FORMAT = pyaudio.paInt16

        stream = p.open(
            format=PYAU_FORMAT,
            channels=PYAU_CHANNELS,
            rate=rate,
            input=True,
            input_device_index=PYAU_INPUT_INDEX,
            frames_per_buffer=chunk,
        )

        # swallow the first 300ms to allow audio device to quiesce
        wait = int(rate * self._startup_delay)
        stream.read(wait, exception_on_overflow=False)

        # read data in a loop until self.finish is set
        data = b""
        while not self.finish:
            data += stream.read(chunk, exception_on_overflow=False)

        # write out the wave file
        stream.close()
        p.terminate()
        wf = wave.open(self._output_path, "wb")
        wf.setnchannels(PYAU_CHANNELS)
        wf.setsampwidth(p.get_sample_size(PYAU_FORMAT))
        wf.setframerate(rate)
        wf.writeframes(data)
        wf.close()


class PyAudioRecorder(Recorder):
    def __init__(self, mw: aqt.AnkiQt, output_path: str) -> None:
        super().__init__(output_path)
        self.mw = mw

    def start(self, on_done: Callable[[], None]) -> None:
        self.thread = PyAudioThreadedRecorder(self.output_path, self.STARTUP_DELAY)
        self.thread.start()
        super().start(on_done)

    def stop(self, on_done: Callable[[str], None]) -> None:
        # ensure at least a second captured
        while self.duration() < 1:
            time.sleep(0.1)

        def func(fut: Future) -> None:
            Recorder.stop(self, on_done)

        self.thread.finish = True
        self.mw.taskman.run_in_background(self.thread.join, func)


# Recording dialog
##########################################################################


class RecordDialog(QDialog):
    _recorder: Recorder

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
        disable_help_button(self)

        self._start_recording()
        self._setup_dialog()

    def _setup_dialog(self) -> None:
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

    def _save_diag(self) -> None:
        saveGeom(self, "audioRecorder2")

    def _start_recording(self) -> None:
        driver = self.mw.pm.recording_driver()
        if driver is RecordingDriver.PyAudio:
            self._recorder = PyAudioRecorder(self.mw, namedtmp("rec.wav"))
        elif driver is RecordingDriver.QtAudioInput:
            self._recorder = QtAudioInputRecorder(
                namedtmp("rec.wav"), self.mw, self._parent
            )
        else:
            assert_exhaustive(driver)
        self._recorder.start(self._start_timer)

    def _start_timer(self) -> None:
        self._timer = t = QTimer(self._parent)
        t.timeout.connect(self._on_timer)  # type: ignore
        t.setSingleShot(False)
        t.start(100)

    def _on_timer(self) -> None:
        self._recorder.on_timer()
        duration = self._recorder.duration()
        self.label.setText(tr.media_recordingtime(secs=f"{duration:0.1f}"))

    def accept(self) -> None:
        self._timer.stop()

        try:
            self._save_diag()
            self._recorder.stop(self._on_success)
        finally:
            QDialog.accept(self)

    def reject(self) -> None:
        self._timer.stop()

        def cleanup(out: str) -> None:
            os.unlink(out)

        try:
            self._recorder.stop(cleanup)
        finally:
            QDialog.reject(self)


def record_audio(
    parent: QWidget, mw: aqt.AnkiQt, encode: bool, on_done: Callable[[str], None]
) -> None:
    def after_record(path: str) -> None:
        if not encode:
            on_done(path)
        else:
            encode_mp3(mw, path, on_done)

    try:
        _diag = RecordDialog(parent, mw, after_record)
    except Exception as e:
        err_str = str(e)
        showWarning(markdown(tr.qt_misc_unable_to_record(error=err_str)))


# Legacy audio interface
##########################################################################
# these will be removed in the future


def clearAudioQueue() -> None:
    av_player.stop_and_clear_queue()


def play(filename: str) -> None:
    av_player.play_file(filename)


def playFromText(text: Any) -> None:
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

        if platform.release() == "10":
            from aqt.tts import WindowsRTTTSFilePlayer

            # If Windows 10, ensure it's October 2018 update or later
            if int(platform.version().split(".")[-1]) >= 17763:
                av_player.players.append(WindowsRTTTSFilePlayer(taskman))

    # cleanup at shutdown
    atexit.register(av_player.shutdown)
