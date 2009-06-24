# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Sound support
==============================
"""
__docformat__ = 'restructuredtext'

import re, sys, threading, time, subprocess, os, signal, atexit, errno
from anki.hooks import addHook

# Shared utils
##########################################################################

def playFromText(text):
    for match in re.findall("\[sound:(.*?)\]", text):
        play(match)

def stripSounds(text):
    return re.sub("\[sound:.*?\]", "", text)

def hasSound(text):
    return re.search("\[sound:.*?\]", text) is not None

##########################################################################

# the amount of noise to cancel
NOISE_AMOUNT = "0.1"
# the amount of amplification
NORM_AMOUNT = "-3"
# the amount of bass
BASS_AMOUNT = "+0"
# the amount to fade at end
FADE_AMOUNT = "0.25"

noiseProfile = ""

processingSrc = "tmp.wav"
processingDst = "tmp.mp3"
processingChain = []
tmpFiles = ["tmp2.wav", "tmp3.wav"]

cmd = ["sox", processingSrc, "tmp2.wav"]
processingChain = [
    None, # placeholder
    ["sox", "tmp2.wav", "tmp3.wav", "norm", NORM_AMOUNT,
     "bass", BASS_AMOUNT, "fade", FADE_AMOUNT, "0"],
    ["lame", "tmp3.wav", processingDst, "--noreplaygain", "--quiet"],
    ]

# don't show box on windows
if sys.platform == "win32":
    si = subprocess.STARTUPINFO()
    si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
else:
    si = None

if sys.platform.startswith("darwin"):
    # make sure lame, which is installed in /usr/local/bin, is in the path
    os.environ['PATH'] += ":" + "/usr/local/bin"
    dir = os.path.dirname(os.path.abspath(__file__))
    dir = os.path.abspath(dir + "/../../../..")
    os.environ['PATH'] += ":" + dir + "/audio"

def retryWait(proc):
    # osx throws interrupted system call errors frequently
    while 1:
        try:
            return proc.wait()
        except OSError:
            continue

# Noise profiles
##########################################################################

def checkForNoiseProfile():
    global processingChain
    if sys.platform.startswith("darwin"):
        # not currently supported
        processingChain = [
            ["lame", "tmp.wav", "tmp.mp3", "--noreplaygain", "--quiet"]]
    else:
        cmd = ["sox", processingSrc, "tmp2.wav"]
        if os.path.exists(noiseProfile):
            cmd = cmd + ["noisered", noiseProfile, NOISE_AMOUNT]
        processingChain[0] = cmd

def generateNoiseProfile():
    try:
        os.unlink(noiseProfile)
    except OSError:
        pass
    retryWait(subprocess.Popen(
        ["sox", processingSrc, tmpFiles[0], "trim", "1.5", "1.5"],
        startupinfo=si))
    retryWait(subprocess.Popen(["sox", tmpFiles[0], tmpFiles[1],
                                "noiseprof", noiseProfile],
                               startupinfo=si))
    processingChain[0] = ["sox", processingSrc, "tmp2.wav",
                          "noisered", noiseProfile, NOISE_AMOUNT]

# Mplayer
##########################################################################

if sys.platform.startswith("win32"):
    mplayerCmd = ["mplayer.exe", "-ao", "win32", "-really-quiet",
                  "-slave", "-idle"]
    dir = os.path.dirname(os.path.abspath(sys.argv[0]))
    os.environ['PATH'] += ";" + dir
    os.environ['PATH'] += ";" + dir + "\\..\\dist" # for testing
else:
    mplayerCmd = ["mplayer", "-really-quiet", "-slave", "-idle"]

mplayerQueue = []
mplayerManager = None
mplayerReader = None
mplayerCond = threading.Condition()

class MplayerReader(threading.Thread):
    "Read any debugging info to prevent mplayer from blocking."

    def run(self):
        while 1:
            mplayerCond.acquire()
            mplayerCond.wait()
            mplayerCond.release()
            try:
                mplayerManager.mplayer.stdout.read()
            except:
                pass

class MplayerMonitor(threading.Thread):

    def run(self):
        self.mplayer = None
        while 1:
            mplayerCond.acquire()
            while not mplayerQueue:
                if not mplayerCond:
                    return
                mplayerCond.wait()
            if not self.mplayer:
                self.startProcess()
            if self.mplayer != -1 and self.mplayer.poll() is not None:
                self.mplayer.wait()
                self.startProcess()
            nextClears = False
            while mplayerQueue:
                item = mplayerQueue.pop(0)
                if item is None:
                    nextClears = True
                    continue
                if nextClears:
                    nextClears = False
                    extra = ""
                else:
                    extra = " 1"
                cmd = "loadfile %s%s\n" % (item, extra)
                self.mplayer.stdin.write(cmd)
            mplayerCond.release()

    def startProcess(self):
        try:
            self.mplayer = subprocess.Popen(
                mplayerCmd, startupinfo=si, stdin=subprocess.PIPE,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        except OSError:
            mplayerCond.release()
            raise Exception("Audio player not found")

def queueMplayer(path):
    ensureMplayerThreads()
    path = path.encode(sys.getfilesystemencoding())
    mplayerCond.acquire()
    mplayerQueue.append(path)
    mplayerCond.notifyAll()
    mplayerCond.release()

def clearMplayerQueue():
    mplayerCond.acquire()
    mplayerQueue.append(None)
    mplayerCond.release()

def ensureMplayerThreads():
    global mplayerManager, mplayerReader
    if not mplayerManager:
        mplayerManager = MplayerMonitor()
        mplayerManager.daemon = True
        mplayerManager.start()
        mplayerReader = MplayerReader()
        mplayerReader.daemon = True
        mplayerReader.start()
        atexit.register(stopMplayer)

def stopMplayer(restart=False):
    if not mplayerManager:
        return
    mplayerCond.acquire()
    if mplayerManager.mplayer:
        while 1:
            try:
                mplayerManager.mplayer.stdin.write("quit\n")
                break
            except OSError, e:
                if e.errno != errno.EINTR:
                    # osx throws interrupt errors regularly, but we want to
                    # ignore other errors on shutdown
                    break
            except IOError:
                # already closed
                break
            except ValueError:
                # already closed
                break
    if not restart:
        mplayerManager.mplayer = -1
    mplayerCond.notifyAll()
    mplayerCond.release()

def stopMplayerOnce():
    stopMplayer(restart=True)

addHook("deckClosed", stopMplayerOnce)

# PyAudio recording
##########################################################################

try:
    import pyaudio
    import wave

    PYAU_FORMAT = pyaudio.paInt16
    PYAU_CHANNELS = 1
    PYAU_RATE = 44100
    PYAU_INPUT_INDEX = 0
except:
    pass

class _Recorder(object):

    def postprocess(self):
        for c in processingChain:
            #print c
            ret = retryWait(subprocess.Popen(c, startupinfo=si))
            if ret:
                raise Exception(_("""
Error processing audio.

If you're on Linux and don't have sox 14.1+, you
need to disable normalization. See the wiki.

Command was:\n""") + " ".join(c))

class PyAudioThreadedRecorder(threading.Thread):

    def __init__(self):
        threading.Thread.__init__(self)
        self.finish = False

    def run(self):
        chunk = 1024
        try:
            p = pyaudio.PyAudio()
        except NameError:
            raise Exception("Recording not supported on OSX10.3.")
        stream = p.open(format=PYAU_FORMAT,
                        channels=PYAU_CHANNELS,
                        rate=PYAU_RATE,
                        input=True,
                        input_device_index=PYAU_INPUT_INDEX,
                        frames_per_buffer=chunk)
        all = []
        while not self.finish:
            try:
                data = stream.read(chunk)
            except IOError, e:
                if e[1] == pyaudio.paInputOverflowed:
                    data = None
                else:
                    raise
            if data:
                all.append(data)
        stream.close()
        p.terminate()
        data = ''.join(all)
        wf = wave.open(processingSrc, 'wb')
        wf.setnchannels(PYAU_CHANNELS)
        wf.setsampwidth(p.get_sample_size(PYAU_FORMAT))
        wf.setframerate(PYAU_RATE)
        wf.writeframes(data)
        wf.close()

class PyAudioRecorder(_Recorder):

    def __init__(self):
        for t in tmpFiles + [processingSrc, processingDst]:
            try:
                os.unlink(t)
            except OSError:
                pass

    def start(self):
        self.thread = PyAudioThreadedRecorder()
        self.thread.start()

    def stop(self):
        self.thread.finish = True
        self.thread.join()

    def file(self):
        return processingDst

# Default audio player
##########################################################################

play = queueMplayer
clearAudioQueue = clearMplayerQueue

Recorder = PyAudioRecorder
