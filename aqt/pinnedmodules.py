# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# this file is imported as part of the bundling process to ensure certain
# modules are included in the distribution

# pylint: disable=import-error,unused-import

# required by requests library
import queue  # noqa

from anki.utils import isWin

# included implicitly in the past, and relied upon by some add-ons
import cgi  # noqa
import uuid  # noqa

# external module access in Windows
if isWin:
    import pythoncom  # noqa
    import win32com  # noqa
    import pywintypes  # noqa
