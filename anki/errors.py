# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Errors
==============================
"""
__docformat__ = 'restructuredtext'

class Error(Exception):
    def __init__(self, message="", **data):
        self.data = data
        self._message = message
    def __str__(self):
        m = self._message
        if self.data:
            m += ": %s" % repr(self.data)
        return m

class DeckAccessError(Error):
    pass

class ImportFileError(Error):
    "Unable to load file to import from."
    pass

class ImportFormatError(Error):
    "Unable to determine pattern in text file."
    pass

class ImportEncodingError(Error):
    "The file was not in utf-8."
    pass

class ExportFileError(Error):
    "Unable to save file."
    pass

class SyncError(Error):
    "A problem occurred during syncing."
    pass

# facts, models
class FactInvalidError(Error):
    """A fact was invalid/not unique according to the model.
'field' defines the problem field.
'type' defines the type of error ('fieldEmpty', 'fieldNotUnique')"""
    pass
