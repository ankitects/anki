# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# make sure not to optimize imports on this file
# pylint: skip-file

"""
PyQt5 imports
"""

from PyQt5.QtCore import *  # type: ignore
from PyQt5.QtGui import *  # type: ignore
from PyQt5.QtNetwork import QLocalServer, QLocalSocket, QNetworkProxy  # type: ignore
from PyQt5.QtWebChannel import QWebChannel  # type: ignore
from PyQt5.QtWebEngineCore import *  # type: ignore
from PyQt5.QtWebEngineWidgets import *  # type: ignore
from PyQt5.QtWidgets import *  # type: ignore

try:
    from PyQt5 import sip  # type: ignore
except ImportError:
    import sip  # type: ignore
