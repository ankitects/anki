# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# type: ignore
# pylint: disable=unused-import

"""
Patches and aliases that provide a PyQt5 → PyQt6 compatibility shim for add-ons
"""

import sys

import PyQt6.QtCore

# Globally alias PyQt5 to PyQt6 ####

sys.modules["PyQt5"] = PyQt6
# Need to register QtCore early as sip otherwise raises an error about PyQt6.QtCore
# already being registered
sys.modules["PyQt5.QtCore"] = PyQt6.QtCore
