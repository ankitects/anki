# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Sound support
==============================
"""
__docformat__ = 'restructuredtext'

import re, sys

try:
    import pygame
    pygame.mixer.pre_init(44100,-16,2, 1024 * 3)
    pygame.mixer.init()
    soundsAvailable = True
except:
    soundsAvailable = False
    print "Warning, pygame not available. No sounds will play."

def playPyGame(path):
    "Play a sound. Expects a unicode pathname."
    if not soundsAvailable:
        return
    path = path.encode(sys.getfilesystemencoding())
    try:
        if pygame.mixer.music.get_busy():
            pygame.mixer.music.queue(path)
        else:
            pygame.mixer.music.load(path)
            pygame.mixer.music.play()
    except pygame.error:
        return

def playFromText(text):
    for match in re.findall("\[sound:(.*?)\]", text):
        play(match)

def stripSounds(text):
    return re.sub("\[sound:.*?\]", "", text)

def hasSound(text):
    return re.search("\[sound:.*?\]", text) is not None

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

if sys.platform.startswith("darwin"):
    play = playOSX
else:
    play = playPyGame
