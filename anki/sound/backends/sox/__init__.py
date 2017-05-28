# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import subprocess
from anki.sound.backends import Backend as BaseBackend
from anki.utils import isLin

def playAvailable():
    try:
        play = subprocess.Popen(
            ['play', '--version'], stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        play.wait()
        return True
    except OSError:
        return False

class Backend(BaseBackend):
    def play(self, path):
        subprocess.Popen(
            ['play', path], stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def clearAudioQueue(self):
        pass

available = isLin and playAvailable()
