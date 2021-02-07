# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: disable=import-error,unused-import

"""
# this file is imported as part of the bundling process to ensure certain
# modules are included in the distribution

isort:skip_file
"""

# included implicitly in the past, and relied upon by some add-ons
import cgi
import decimal
import gettext

# useful for add-ons
import logging
import logging.config
import logging.handlers

# required by requests library
import queue
import typing
import uuid

# other modules we require that may not be automatically included
import PyQt5.QtSvg
import PyQt5.QtMultimedia
import socks
import pyaudio

# legacy compat
import anki.storage
import anki.sync
import anki.rsbackend

# platform-specifics
from anki.utils import isLin, isMac, isWin

if isWin:
    # external module access
    import pythoncom
    import pywintypes
    import win32com

if isLin:
    # file locking
    import fcntl
