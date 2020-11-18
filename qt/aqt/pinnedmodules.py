# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# this file is imported as part of the bundling process to ensure certain
# modules are included in the distribution

# pylint: disable=import-error,unused-import

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

import PyQt5.QtSvg
import socks

# legacy compat
import anki.storage
import anki.sync
from anki.utils import isLin, isMac, isWin

if isWin:
    # external module access
    import pythoncom
    import pywintypes
    import win32com

if isLin:
    # file locking
    import fcntl

if isMac:
    # recording
    import PyQt5.QtMultimedia
