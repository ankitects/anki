# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import atexit
import os
import random
import subprocess
import sys
import threading
import time
import wave
from abc import ABC, abstractmethod
from concurrent.futures import Future
from typing import Any, Callable, Dict, List, Optional, Tuple, cast

import pyaudio

import anki
import aqt
from anki.lang import _
from anki.sound import AVTag, SoundOrVideoTag
from anki.utils import isLin, isMac, isWin, tmpdir
from aqt import gui_hooks
from aqt.mpv import MPV, MPVBase
from aqt.qt import *
from aqt.taskman import TaskManager
from aqt.utils import restoreGeom, saveGeom

# AV player protocol
##########################################################################

OnDoneCallback = Callable[[], None]


class Player(ABC):
    @abstractmethod
    def can_play(self, tag: AVTag) -> bool:
        pass

    @abstractmethod
    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        pass

    def stop(self) -> None:
        """Optional.

        If implemented, the player must not call on_done() when the audio is stopped."""


class SoundOrVideoPlayer(Player):  # pylint: disable=abstract-method
    def can_play(self, tag: AVTag) -> bool:
        return isinstance(tag, SoundOrVideoTag)


# Main playing interface
##########################################################################


class AVPlayer:
    players: List[Player] = []
    # when a new batch of audio is played, shoud the currently playing
    # audio be stopped?
    interrupt_current_audio = True

    def __init__(self):
        self._enqueued: List[AVTag] = []
        self._current_player: Optional[Player] = None

    def play_tags(self, tags: List[AVTag]) -> None:
        """Clear the existing queue, then start playing provided tags."""
        self._enqueued = tags
        if self.interrupt_current_audio:
            self._stop_if_playing()
        self._play_next_if_idle()

    def extend_and_play(self, tags: List[AVTag]) -> None:
        """Add extra tags to queue, without clearing it."""
        self._enqueued.extend(tags)
        self._play_next_if_idle()

    def play_from_text(self, col: anki.storage._Collection, text: str) -> None:
        tags = col.backend.get_av_tags(text)
        self.play_tags(tags)

    def extend_from_text(self, col: anki.storage._Collection, text: str) -> None:
        tags = col.backend.get_av_tags(text)
        self.extend_and_play(tags)

    def stop_and_clear_queue(self) -> None:
        self._enqueued = []
        self._stop_if_playing()

    def play_file(self, filename: str) -> None:
        self.play_tags([SoundOrVideoTag(filename=filename)])

    def _stop_if_playing(self) -> None:
        if self._current_player:
            self._current_player.stop()
        self._current_player = None

    def _pop_next(self) -> Optional[AVTag]:
        if not self._enqueued:
            return None
        return self._enqueued.pop(0)

    def _on_play_finished(self) -> None:
        self._current_player = None
        gui_hooks.av_player_did_play()
        self._play_next_if_idle()

    def _play_next_if_idle(self) -> None:
        if self._current_player:
            return

        next = self._pop_next()
        if next is not None:
            self._play(next)

    def _play(self, tag: AVTag) -> None:
        for player in self.players:
            if player.can_play(tag):
                self._current_player = player
                gui_hooks.av_player_will_play(tag)
                player.play(tag, self._on_play_finished)
                return
        print("no players found for", tag)


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


# Simple player implementations
##########################################################################


class PlayerInterrupted(Exception):
    pass


class SimpleProcessPlayer(SoundOrVideoPlayer):
    "A player that invokes a new process for each file to play."

    args: List[str] = []
    env: Optional[Dict[str, str]] = None

    def __init__(self, taskman: TaskManager):
        self._taskman = taskman
        _terminate_flag = False

    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        stag = cast(SoundOrVideoTag, tag)
        self._terminate_flag = False
        self._taskman.run(
            lambda: self._play(stag.filename), lambda res: self._on_done(res, on_done)
        )

    def stop(self):
        self._terminate_flag = True
        # block until stopped
        while self._terminate_flag:
            time.sleep(0.1)

    def _play(self, filename: str) -> None:
        process = subprocess.Popen(self.args + [filename], env=self.env)
        while True:
            try:
                process.wait(0.1)
                if process.returncode != 0:
                    print(f"player got return code: {process.returncode}")
                return
            except subprocess.TimeoutExpired:
                pass
            if self._terminate_flag:
                process.terminate()
                self._terminate_flag = False
                raise PlayerInterrupted()

    def _on_done(self, ret: Future, cb: OnDoneCallback) -> None:
        try:
            ret.result()
        except PlayerInterrupted:
            # don't fire done callback when interrupted
            return
        cb()


class SimpleMpvPlayer(SimpleProcessPlayer):
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
        self.args += ["--no-config", "--include=" + conf_path]


class SimpleMplayerPlayer(SimpleProcessPlayer):
    args, env = _packagedCmd(["mplayer", "-really-quiet", "-noautosub"])
    if isWin:
        args += ["-ao", "win32"]


# Platform hacks
##########################################################################

# don't show box on windows
si: Optional[Any]
if sys.platform == "win32":
    si = subprocess.STARTUPINFO()  # pytype: disable=module-attr
    try:
        si.dwFlags |= subprocess.STARTF_USESHOWWINDOW  # pytype: disable=module-attr
    except:
        # pylint: disable=no-member
        # python2.7+
        si.dwFlags |= (
            subprocess._subprocess.STARTF_USESHOWWINDOW
        )  # pytype: disable=module-attr
else:
    si = None


# osx throws interrupted system call errors frequently
def retryWait(proc) -> Any:
    while 1:
        try:
            return proc.wait()
        except OSError:
            continue


# MPV
##########################################################################


class MpvManager(MPV, SoundOrVideoPlayer):

    if not isLin:
        default_argv = MPVBase.default_argv + [
            "--input-media-keys=no",
        ]

    def __init__(self, base_path: str) -> None:
        super().__init__(window_id=None, debug=False)
        mpvPath, self.popenEnv = _packagedCmd(["mpv"])
        self.executable = mpvPath[0]
        self._on_done: Optional[OnDoneCallback] = None
        conf_path = os.path.join(base_path, "mpv.conf")
        self.default_argv += ["--no-config", "--include=" + conf_path]

    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        stag = cast(SoundOrVideoTag, tag)
        self._on_done = on_done
        path = os.path.join(os.getcwd(), stag.filename)
        self.command("loadfile", path, "append-play")

    def stop(self) -> None:
        self.command("stop")

    def togglePause(self) -> None:
        self.set_property("pause", not self.get_property("pause"))

    def seekRelative(self, secs) -> None:
        self.command("seek", secs, "relative")

    def on_idle(self) -> None:
        if self._on_done:
            self._on_done()

    # Legacy, not used
    ##################################################

    def queueFile(self, file: str) -> None:
        path = os.path.join(os.getcwd(), file)
        self.command("loadfile", path, "append-play")

    def clearQueue(self) -> None:
        self.command("stop")


def cleanupMPV() -> None:
    global mpvManager
    if mpvManager:
        mpvManager.close()
        mpvManager = None


# Mplayer in slave mode
##########################################################################

# if anki crashes, an old mplayer instance may be left lying around,
# which prevents renaming or deleting the profile
def cleanupOldMplayerProcesses() -> None:
    # pylint: disable=import-error
    import psutil  # pytype: disable=import-error

    exeDir = os.path.dirname(os.path.abspath(sys.argv[0]))

    for proc in psutil.process_iter():
        try:
            info = proc.as_dict(attrs=["pid", "name", "exe"])
            if not info["exe"] or info["name"] != "mplayer.exe":
                continue

            # not anki's bundled mplayer
            if os.path.dirname(info["exe"]) != exeDir:
                continue

            print("terminating old mplayer process...")
            proc.kill()
        except:
            print("error iterating mplayer processes")


mplayerCmd = ["mplayer", "-really-quiet", "-noautosub"]
if isWin:
    mplayerCmd += ["-ao", "win32"]

    cleanupOldMplayerProcesses()

mplayerQueue: List[str] = []
mplayerEvt = threading.Event()
mplayerClear = False


class MplayerMonitor(threading.Thread):

    mplayer: Optional[subprocess.Popen] = None
    deadPlayers: List[subprocess.Popen] = []

    def run(self) -> None:
        global mplayerClear
        self.mplayer = None
        self.deadPlayers = []
        while 1:
            mplayerEvt.wait()
            mplayerEvt.clear()
            # clearing queue?
            if mplayerClear and self.mplayer:
                try:
                    self.mplayer.stdin.write(b"stop\n")
                    self.mplayer.stdin.flush()
                except:
                    # mplayer quit by user (likely video)
                    self.deadPlayers.append(self.mplayer)
                    self.mplayer = None
            # loop through files to play
            while mplayerQueue:
                # ensure started
                if not self.mplayer:
                    self.mplayer = self.startProcess()
                # pop a file
                try:
                    item = mplayerQueue.pop(0)
                except IndexError:
                    # queue was cleared by main thread
                    continue
                if mplayerClear:
                    mplayerClear = False
                    extra = b""
                else:
                    extra = b" 1"
                cmd = b'loadfile "%s"%s\n' % (item.encode("utf8"), extra)
                try:
                    self.mplayer.stdin.write(cmd)
                    self.mplayer.stdin.flush()
                except:
                    # mplayer has quit and needs restarting
                    self.deadPlayers.append(self.mplayer)
                    self.mplayer = None
                    self.mplayer = self.startProcess()
                    self.mplayer.stdin.write(cmd)
                    self.mplayer.stdin.flush()
                # if we feed mplayer too fast it loses files
                time.sleep(1)
            # wait() on finished processes. we don't want to block on the
            # wait, so we keep trying each time we're reactivated
            def clean(pl):
                if pl.poll() is not None:
                    pl.wait()
                    return False
                else:
                    return True

            self.deadPlayers = [pl for pl in self.deadPlayers if clean(pl)]

    def kill(self) -> None:
        if not self.mplayer:
            return
        try:
            self.mplayer.stdin.write(b"quit\n")
            self.mplayer.stdin.flush()
            self.deadPlayers.append(self.mplayer)
        except:
            pass
        self.mplayer = None

    def startProcess(self) -> subprocess.Popen:
        try:
            cmd = mplayerCmd + ["-slave", "-idle"]
            cmd, env = _packagedCmd(cmd)
            return subprocess.Popen(
                cmd,
                startupinfo=si,
                stdin=subprocess.PIPE,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                env=env,
            )
        except OSError:
            mplayerEvt.clear()
            raise Exception("Did you install mplayer?")


mplayerManager: Optional[MplayerMonitor] = None


def queueMplayer(path) -> None:
    ensureMplayerThreads()
    if isWin and os.path.exists(path):
        # mplayer on windows doesn't like the encoding, so we create a
        # temporary file instead. oddly, foreign characters in the dirname
        # don't seem to matter.
        dir = tmpdir()
        name = os.path.join(
            dir, "audio%s%s" % (random.randrange(0, 1000000), os.path.splitext(path)[1])
        )
        f = open(name, "wb")
        f.write(open(path, "rb").read())
        f.close()
        # it wants unix paths, too!
        path = name.replace("\\", "/")
    mplayerQueue.append(path)
    mplayerEvt.set()


def clearMplayerQueue() -> None:
    global mplayerClear, mplayerQueue
    mplayerQueue = []
    mplayerClear = True
    mplayerEvt.set()


def ensureMplayerThreads() -> None:
    global mplayerManager
    if not mplayerManager:
        mplayerManager = MplayerMonitor()
        mplayerManager.daemon = True
        mplayerManager.start()
        # ensure the tmpdir() exit handler is registered first so it runs
        # after the mplayer exit
        tmpdir()
        # clean up mplayer on exit
        atexit.register(stopMplayer)


def stopMplayer(*args) -> None:
    if not mplayerManager:
        return
    mplayerManager.kill()
    if isWin:
        cleanupOldMplayerProcesses()


gui_hooks.profile_will_close.append(stopMplayer)

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
                ret = retryWait(subprocess.Popen(cmd, startupinfo=si, env=env))
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
    from aqt import mw

    av_player.extend_from_text(mw.col, text)


# legacy globals
_player = play
_queueEraser = clearAudioQueue
mpvManager: Optional["MpvManager"] = None

# add everything from this module into anki.sound for backwards compat
_exports = [i for i in locals().items() if not i[0].startswith("__")]
for (k, v) in _exports:
    sys.modules["anki.sound"].__dict__[k] = v

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
        atexit.register(cleanupMPV)
    else:
        # fall back on mplayer
        mplayer = SimpleMplayerPlayer(taskman)
        av_player.players.append(mplayer)

    # currently unused
    # mpv = SimpleMpvPlayer(base_folder)
    # av_player.players.append(mpv)

    # tts support
    if isMac:
        from aqt.tts import MacTTSPlayer

        av_player.players.append(MacTTSPlayer(taskman))
