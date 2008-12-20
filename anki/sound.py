# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Sound support
==============================
"""
__docformat__ = 'restructuredtext'

import re, sys, threading, time, subprocess, os

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

queue = []
manager = None

if sys.platform.startswith("win32"):
    externalPlayer = ["c:\\program files\\windows media player\\wmplayer.exe"]
else:
    externalPlayer = ["mplayer", "-really-quiet"]

# don't show box on windows
if sys.platform == "win32":
    si = subprocess.STARTUPINFO()
    si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
else:
    si = None

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
    path = os.path.abspath(path).encode(sys.getfilesystemencoding())
    queue.append(path)
    if not manager or not manager.isAlive():
        manager = QueueMonitor()
        manager.start()

# Pygame
##########################################################################

# try:
#     import pygame
#     pygame.mixer.pre_init(44100,-16,2, 1024 * 3)
#     pygame.mixer.init()
#     soundsAvailable = True
# except:
#     soundsAvailable = False
#     print "Warning, pygame not available. No sounds will play."

# def playPyGame(path):
#     "Play a sound. Expects a unicode pathname."
#     if not soundsAvailable:
#         return
#     path = path.encode(sys.getfilesystemencoding())
#     try:
#         if pygame.mixer.music.get_busy():
#             pygame.mixer.music.queue(path)
#         else:
#             pygame.mixer.music.load(path)
#             pygame.mixer.music.play()
#     except pygame.error:
#         return

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
else:
    play = playExternal
