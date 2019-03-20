# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# this file is imported as part of the bundling process to ensure certain
# modules are included in the distribution

# pylint: disable=import-error,unused-import

# required by requests library
import queue

from anki.utils import isWin

# external module access in Windows
if isWin:
    import pythoncom
    import win32com
    import pywintypes

# included implicitly in the past, and relied upon by some add-ons
import cgi
import uuid
