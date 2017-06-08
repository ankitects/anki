# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from abc import ABCMeta, abstractmethod

class Backend(metaclass=ABCMeta):
    @abstractmethod
    def play(self, path):
        pass

    @abstractmethod
    def clearAudioQueue(self):
        pass

from pathlib import Path
import os.path
import importlib

imported = {}

p = Path(os.path.dirname(__file__))
for module in [x.name for x in p.iterdir() if x.is_dir()]:
    if os.path.isfile(str(p) + '/' + module + '/__init__.py'):
        imported[module] = importlib.import_module('.' + module, __name__)
del module
del p

available = {}
for module in imported:
    if imported[module].available:
        available[module] = imported[module]
