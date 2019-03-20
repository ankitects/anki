# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

class AnkiError(Exception):
    def __init__(self, type, **data):
        super().__init__()
        self.type = type
        self.data = data
    def __str__(self):
        m = self.type
        if self.data:
            m += ": %s" % repr(self.data)
        return m

class DeckRenameError(Exception):
    def __init__(self, description):
        super().__init__()
        self.description = description
    def __str__(self):
        return "Couldn't rename deck: " + self.description
