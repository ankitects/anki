# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import atexit
import html
import os
import random
import subprocess
import sys
import threading
import time
from typing import Any, Callable, Dict, List, Optional, Tuple

from anki.hooks import addHook, runHook
from anki.lang import _
from anki.sound import allSounds
from anki.utils import isLin, isMac, isWin, tmpdir
from aqt.mpv import MPV, MPVBase
from aqt.qt import *
from aqt.utils import restoreGeom, saveGeom, showWarning


def getAudio(parent, encode=True):
    "Record and return filename"
    # record first
    if not Recorder:
        showWarning("pyaudio not installed")
        return

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


# Shared utils
##########################################################################


def playFromText(text) -> None:
    for match in allSounds(text):
        # filename is html encoded
        match = html.unescape(match)
        play(match)


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


##########################################################################

processingSrc = "rec.wav"
processingDst = "rec.mp3"
processingChain: List[List[str]] = []
recFiles: List[str] = []

processingChain = [
    ["lame", processingSrc, processingDst, "--noreplaygain", "--quiet"],
]

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


def retryWait(proc) -> Any:
    # osx throws interrupted system call errors frequently
    while 1:
        try:
            return proc.wait()
        except OSError:
            continue


# MPV
##########################################################################


_player: Optional[Callable[[Any], Any]]
_queueEraser: Optional[Callable[[], Any]]
mpvManager: Optional["MpvManager"] = None

mpvPath, mpvEnv = _packagedCmd(["mpv"])


class MpvManager(MPV):

    executable = mpvPath[0]
    popenEnv = mpvEnv

    if not isLin:
        default_argv = MPVBase.default_argv + [
            "--input-media-keys=no",
        ]

    def __init__(self) -> None:
        super().__init__(window_id=None, debug=False)

    def queueFile(self, file) -> None:
        runHook("mpvWillPlay", file)

        path = os.path.join(os.getcwd(), file)
        self.command("loadfile", path, "append-play")

    def clearQueue(self) -> None:
        self.command("stop")

    def togglePause(self) -> None:
        self.set_property("pause", not self.get_property("pause"))

    def seekRelative(self, secs) -> None:
        self.command("seek", secs, "relative")

    def on_idle(self) -> None:
        runHook("mpvIdleHook")


def setMpvConfigBase(base) -> None:
    mpvConfPath = os.path.join(base, "mpv.conf")
    MpvManager.default_argv += [
        "--no-config",
        "--include=" + mpvConfPath,
    ]


def setupMPV() -> None:
    global mpvManager, _player, _queueEraser
    mpvManager = MpvManager()
    _player = mpvManager.queueFile
    _queueEraser = mpvManager.clearQueue
    atexit.register(cleanupMPV)


def cleanupMPV() -> None:
    global mpvManager, _player, _queueEraser
    if mpvManager:
        mpvManager.close()
        mpvManager = None
        _player = None
        _queueEraser = None


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


addHook("unloadProfile", stopMplayer)

# PyAudio recording
##########################################################################

try:
    import pyaudio
    import wave

    PYAU_FORMAT = pyaudio.paInt16
    PYAU_CHANNELS = 1
    PYAU_INPUT_INDEX: Optional[int] = None
except:
    pyaudio = None


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


if not pyaudio:
    PyAudioRecorder = None  # type: ignore

# Audio interface
##########################################################################

_player = queueMplayer
_queueEraser = clearMplayerQueue


def play(path) -> None:
    _player(path)


def clearAudioQueue() -> None:
    _queueEraser()


Recorder = PyAudioRecorder
if not Recorder:
    print("pyaudio not installed")

# add everything from this module into anki.sound for backwards compat
_exports = [i for i in locals().items() if not i[0].startswith("__")]
for (k, v) in _exports:
    sys.modules["anki.sound"].__dict__[k] = v
