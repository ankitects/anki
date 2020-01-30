# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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
from typing import Any, Callable, Dict, List, Optional, Tuple

import pyaudio

import aqt
from anki.cards import Card
from anki.lang import _
from anki.sound import AV_REF_RE, AVTag, SoundOrVideoTag
from anki.utils import isLin, isMac, isWin
from aqt import gui_hooks
from aqt.mpv import MPV, MPVBase
from aqt.qt import *
from aqt.taskman import TaskManager
from aqt.utils import restoreGeom, saveGeom, startup_info

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


class SoundOrVideoPlayer(Player):  # pylint: disable=abstract-method
    default_rank = 0

    def rank_for_tag(self, tag: AVTag) -> Optional[int]:
        if isinstance(tag, SoundOrVideoTag):
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

    def __init__(self):
        self._enqueued: List[AVTag] = []
        self.current_player: Optional[Player] = None

    def play_tags(self, tags: List[AVTag]) -> None:
        """Clear the existing queue, then start playing provided tags."""
        self._enqueued = tags[:]
        if self.interrupt_current_audio:
            self._stop_if_playing()
        self._play_next_if_idle()

    def stop_and_clear_queue(self) -> None:
        self._enqueued = []
        self._stop_if_playing()

    def play_file(self, filename: str) -> None:
        self.play_tags([SoundOrVideoTag(filename=filename)])

    def insert_file(self, filename: str) -> None:
        self._enqueued.insert(0, SoundOrVideoTag(filename=filename))
        self._play_next_if_idle()

    def toggle_pause(self):
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
        self.current_player = None

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
def _packagedCmd(cmd) -> Tuple[Any, Dict[str, str]]:
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
def retryWait(proc) -> Any:
    while 1:
        try:
            return proc.wait()
        except OSError:
            continue


# Simple player implementations
##########################################################################


class PlayerInterrupted(Exception):
    pass


class SimpleProcessPlayer(Player):  # pylint: disable=abstract-method
    "A player that invokes a new process for each tag to play."

    args: List[str] = []
    env: Optional[Dict[str, str]] = None

    def __init__(self, taskman: TaskManager):
        self._taskman = taskman
        self._terminate_flag = False
        self._process: Optional[subprocess.Popen] = None

    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        self._taskman.run_in_background(
            lambda: self._play(tag), lambda res: self._on_done(res, on_done)
        )

    def stop(self):
        self._terminate_flag = True
        # block until stopped
        t = time.time()
        while self._terminate_flag and time.time() - t < 1:
            time.sleep(0.1)

    def _play(self, tag: AVTag) -> None:
        assert isinstance(tag, SoundOrVideoTag)
        self._process = subprocess.Popen(
            self.args + [tag.filename], env=self.env, startupinfo=startup_info()
        )
        self._wait_for_termination(tag)

    def _wait_for_termination(self, tag: AVTag):
        self._taskman.run_on_main(
            lambda: gui_hooks.av_player_did_begin_playing(self, tag)
        )

        try:
            while True:
                try:
                    self._process.wait(0.1)
                    if self._process.returncode != 0:
                        print(f"player got return code: {self._process.returncode}")
                    return
                except subprocess.TimeoutExpired:
                    pass
                if self._terminate_flag:
                    self._process.terminate()
                    raise PlayerInterrupted()
        finally:
            self._process = None
            self._terminate_flag = False

    def _on_done(self, ret: Future, cb: OnDoneCallback) -> None:
        try:
            ret.result()
        except PlayerInterrupted:
            # don't fire done callback when interrupted
            return
        cb()


class SimpleMpvPlayer(SimpleProcessPlayer, SoundOrVideoPlayer):
    args, env = _packagedCmd(
        [
            "mpv",
            "--no-terminal",
            "--force-window=no",
            "--ontop",
            "--audio-display=no",
            "--keep-open=no",
            "--input-media-keys=no",
            "--no-config",
        ]
    )

    def __init__(self, taskman: TaskManager, base_folder: str) -> None:
        super().__init__(taskman)
        conf_path = os.path.join(base_folder, "mpv.conf")
        self.args += ["--include=" + conf_path]


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
        conf_path = os.path.join(base_path, "mpv.conf")
        self.default_argv += ["--no-config", "--include=" + conf_path]
        super().__init__(window_id=None, debug=False)

    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        assert isinstance(tag, SoundOrVideoTag)
        self._on_done = on_done
        path = os.path.join(os.getcwd(), tag.filename)
        self.command("loadfile", path, "append-play")
        gui_hooks.av_player_did_begin_playing(self, tag)

    def stop(self) -> None:
        self.command("stop")

    def toggle_pause(self) -> None:
        self.set_property("pause", not self.get_property("pause"))

    def seek_relative(self, secs) -> None:
        self.command("seek", secs, "relative")

    def on_idle(self) -> None:
        if self._on_done:
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
        self._process = subprocess.Popen(
            self.args + [tag.filename],
            env=self.env,
            stdin=subprocess.PIPE,
            startupinfo=startup_info(),
        )
        self._wait_for_termination(tag)

    def command(self, *args) -> None:
        """Send a command over the slave interface.

        The trailing newline is automatically added."""
        str_args = [str(x) for x in args]
        self._process.stdin.write(" ".join(str_args).encode("utf8") + b"\n")
        self._process.stdin.flush()

    def seek_relative(self, secs: int) -> None:
        self.command("seek", secs, 0)

    def toggle_pause(self):
        self.command("pause")


# PyAudio recording
##########################################################################


PYAU_FORMAT = pyaudio.paInt16
PYAU_CHANNELS = 1
PYAU_INPUT_INDEX: Optional[int] = None

processingSrc = "rec.wav"
processingDst = "rec.mp3"
recFiles: List[str] = []

processingChain: List[List[str]] = [
    ["lame", processingSrc, processingDst, "--noreplaygain", "--quiet"],
]


class _Recorder:
    def postprocess(self, encode=True) -> None:
        self.encode = encode
        for c in processingChain:
            # print c
            if not self.encode and c[0] == "lame":
                continue
            try:
                cmd, env = _packagedCmd(c)
                ret = retryWait(
                    subprocess.Popen(cmd, startupinfo=startup_info(), env=env)
                )
            except:
                ret = True
            finally:
                self.cleanup()
            if ret:
                raise Exception(_("Error running %s") % " ".join(cmd))

    def cleanup(self) -> None:
        if os.path.exists(processingSrc):
            os.unlink(processingSrc)


class PyAudioThreadedRecorder(threading.Thread):
    def __init__(self, startupDelay) -> None:
        threading.Thread.__init__(self)
        self.startupDelay = startupDelay
        self.finish = False

    def run(self) -> Any:
        chunk = 1024
        p = pyaudio.PyAudio()

        rate = int(p.get_default_input_device_info()["defaultSampleRate"])
        wait = int(rate * self.startupDelay)

        stream = p.open(
            format=PYAU_FORMAT,
            channels=PYAU_CHANNELS,
            rate=rate,
            input=True,
            input_device_index=PYAU_INPUT_INDEX,
            frames_per_buffer=chunk,
        )

        stream.read(wait)

        data = b""
        while not self.finish:
            data += stream.read(chunk, exception_on_overflow=False)
        stream.close()
        p.terminate()
        wf = wave.open(processingSrc, "wb")
        wf.setnchannels(PYAU_CHANNELS)
        wf.setsampwidth(p.get_sample_size(PYAU_FORMAT))
        wf.setframerate(rate)
        wf.writeframes(data)
        wf.close()


class PyAudioRecorder(_Recorder):

    # discard first 250ms which may have pops/cracks
    startupDelay = 0.25

    def __init__(self):
        for t in recFiles + [processingSrc, processingDst]:
            try:
                os.unlink(t)
            except OSError:
                pass
        self.encode = False

    def start(self):
        self.thread = PyAudioThreadedRecorder(startupDelay=self.startupDelay)
        self.thread.start()

    def stop(self):
        self.thread.finish = True
        self.thread.join()

    def file(self):
        if self.encode:
            tgt = "rec%d.mp3" % time.time()
            os.rename(processingDst, tgt)
            return tgt
        else:
            return processingSrc


Recorder = PyAudioRecorder

# Recording dialog
##########################################################################


def getAudio(parent, encode=True):
    "Record and return filename"
    # record first
    r = Recorder()
    mb = QMessageBox(parent)
    restoreGeom(mb, "audioRecorder")
    mb.setWindowTitle("Anki")
    mb.setIconPixmap(QPixmap(":/icons/media-record.png"))
    but = QPushButton(_("Save"))
    mb.addButton(but, QMessageBox.AcceptRole)
    but.setDefault(True)
    but = QPushButton(_("Cancel"))
    mb.addButton(but, QMessageBox.RejectRole)
    mb.setEscapeButton(but)
    t = time.time()
    r.start()
    time.sleep(r.startupDelay)
    QApplication.instance().processEvents()
    while not mb.clickedButton():
        txt = _("Recording...<br>Time: %0.1f")
        mb.setText(txt % (time.time() - t))
        mb.show()
        QApplication.instance().processEvents()
    if mb.clickedButton() == mb.escapeButton():
        r.stop()
        r.cleanup()
        return
    saveGeom(mb, "audioRecorder")
    # ensure at least a second captured
    while time.time() - t < 1:
        time.sleep(0.1)
    r.stop()
    # process
    r.postprocess(encode)
    return r.file()


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

    if not isWin:
        try:
            mpvManager = MpvManager(base_folder)
        except FileNotFoundError:
            print("mpv not found, reverting to mplayer")
        except aqt.mpv.MPVProcessError:
            print("mpv too old, reverting to mplayer")

    if mpvManager is not None:
        av_player.players.append(mpvManager)
    else:
        mplayer = SimpleMplayerSlaveModePlayer(taskman)
        av_player.players.append(mplayer)

    # currently unused
    # mpv = SimpleMpvPlayer(base_folder)
    # av_player.players.append(mpv)

    # tts support
    if isMac:
        from aqt.tts import MacTTSPlayer

        av_player.players.append(MacTTSPlayer(taskman))
    elif isWin:
        from aqt.tts import WindowsTTSPlayer

        av_player.players.append(WindowsTTSPlayer(taskman))

    # cleanup at shutdown
    atexit.register(av_player.shutdown)
