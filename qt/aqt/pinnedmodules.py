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
try:
    import PyQt6.QtSvg  # type: ignore
    import PyQt6.QtMultimedia  # type: ignore
except:
    import PyQt5.QtSvg  # type: ignore
    import PyQt5.QtMultimedia  # type: ignore
import socks

# legacy compat
import anki.storage
import anki.sync
import anki.rsbackend

# platform-specifics
from anki.utils import is_lin, is_mac, is_win

if is_win:
    # external module access
    import pythoncom
    import pywintypes
    import win32com

if is_lin:
    # file locking
    import fcntl
