# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Sound support
==============================
"""
__docformat__ = 'restructuredtext'

import re, sys, threading, time, subprocess, os, signal

# Shared utils
##########################################################################

def playFromText(text):
    for match in re.findall("\[sound:(.*?)\]", text):
        play(match)

def stripSounds(text):
    return re.sub("\[sound:.*?\]", "", text)

def hasSound(text):
    return re.search("\[sound:.*?\]", text) is not None

# External audio
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
    ["lame", "tmp3.wav", processingDst, "--noreplaygain"],
    ]

queue = []
manager = None

if sys.platform.startswith("win32"):
    base = os.path.join(os.path.dirname(sys.argv[0]), "mplayer.exe")
    #base = "C:\mplayer.exe"
    externalPlayer = [base, "-ao", "win32", "-really-quiet"]
    externalRecorder = ["rec", processingSrc]
else:
    externalPlayer = ["mplayer", "-really-quiet"]
    externalRecorder = ["ecasound", "-x", "-f:16,1,44100", "-i",
                        "alsahw,1,0", "-o", processingSrc]

# don't show box on windows
if sys.platform == "win32":
    si = subprocess.STARTUPINFO()
    si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
else:
    si = None

# noise profiles
##########################################################################

def checkForNoiseProfile():
    cmd = ["sox", processingSrc, "tmp2.wav"]
    if os.path.exists(noiseProfile):
        cmd = cmd + ["noisered", noiseProfile, NOISE_AMOUNT]
    processingChain[0] = cmd

def generateNoiseProfile(file):
    try:
        os.unlink(noiseProfile)
    except OSError:
        pass
    subprocess.Popen(["sox", processingSrc, tmpFiles[0], "trim", "1.5", "1.5"])
    subprocess.Popen(["sox", tmpFiles[0], tmpFiles[1],
                      "noiseprof", noiseProfile]).wait()
    processingChain[0] = ["sox", processingSrc, "tmp2.wav",
                          "noisered", noiseProfile, NOISE_AMOUNT]

# External playing
##########################################################################

class QueueMonitor(threading.Thread):

    def run(self):
        while 1:
            time.sleep(0.1)
            if queue:
                path = queue.pop(0)
                p = subprocess.Popen(externalPlayer + [path],
                                     startupinfo=si)
                p.wait()
            else:
                return

def playExternal(path):
    global manager
    path = path.encode(sys.getfilesystemencoding())
    queue.append(path)
    if not manager or not manager.isAlive():
        manager = QueueMonitor()
        manager.start()

def clearQueueExternal():
    global queue
    queue = []

# External recording
##########################################################################

class _Recorder(object):

    def postprocess(self):
        for c in processingChain:
            print c
            if subprocess.Popen(c, startupinfo=si).wait():
                raise Exception("problem with" + str(c))

class ExternalUnixRecorder(_Recorder):

    def __init__(self):
        for t in tmpFiles + [processingSrc, processingDst]:
            try:
                os.unlink(t)
            except OSError:
                pass

    def start(self):
        self.proc = subprocess.Popen(
            externalRecorder, startupinfo=si)

    def stop(self):
        os.kill(self.proc.pid, signal.SIGINT)
        self.proc.wait()

    def file(self):
        return processingDst

# Mac audio support
##########################################################################

try:
    from AppKit import NSSound, NSObject

    queue = []
    current = None

    class Sound(NSObject):

        def init(self):
            return self

        def sound_didFinishPlaying_(self, sound, bool):
            global current
            while 1:
                if not queue:
                    break
                next = queue.pop(0)
                if play_(next):
                    break

    s = Sound.new()

    def playOSX(path):
        global current
        if current:
            if current.isPlaying():
                queue.append(path)
                return
        # new handle
        play_(path)

    def clearQueueOSX():
        global queue
        queue = []

    def play_(path):
        global current
        current = NSSound.alloc()
        current = current.initWithContentsOfFile_byReference_(path, True)
        if not current:
            return False
        current.setDelegate_(s)
        current.play()
        return True
except ImportError:
    pass

# Default audio player
##########################################################################

if sys.platform.startswith("darwin"):
    play = playOSX
    clearAudioQueue = clearQueueOSX
    Recorder = None
else:
    play = playExternal
    clearAudioQueue = clearQueueExternal
    if sys.platform.startswith("win32"):
        Recorder = None
    else:
        Recorder = ExternalUnixRecorder
