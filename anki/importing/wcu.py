# -*- coding: utf-8 -*-
# Author Chris Aakre <caaakre@gmail.com>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Importing WCU files
====================
"""
__docformat__ = 'restructuredtext'

import codecs
from anki.importing import Importer, ForeignCard
from anki.lang import _
from anki.errors import *

class WCUImporter(Importer):
    def __init__(self, *args):
        Importer.__init__(self, *args)
        self.lines = None
        self.numFields=int(2)

    def foreignCards(self):
        from xml.dom import minidom, Node
        cards = []
        f = None
        try:
            f = codecs.open(self.file, encoding="utf-8")
        except:
            raise ImportFormatError(type="encodingError", info=_("The file was not in UTF8 format."))
        f.close()
        def wcuwalk(parent, cards, level=0):
                for node in parent.childNodes:
                    if node.nodeType == Node.ELEMENT_NODE:
                        myCard=ForeignCard()
                        if node.attributes.has_key("QuestionPicture"):
                            question = [unicode('<img src="'+node.attributes.get("QuestionPicture").nodeValue+'"><br/>'+node.attributes.get("Question").nodeValue)]
                        else:
                            question = [unicode(node.attributes.get("Question").nodeValue)]
                        if node.attributes.has_key("AnswerPicture"):
                            answer = [unicode('<img src="'+node.attributes.get("AnswerPicture").nodeValue+'"><br/>'+node.attributes.get("Answer").nodeValue)]
                        else:
                            answer = [unicode(node.attributes.get("Answer").nodeValue)]
                        myCard.fields.extend(question)
                        myCard.fields.extend(answer)
                        cards.append(myCard)
                        wcuwalk(node, cards, level+1)

        def importwcu(file):
            wcuwalk(minidom.parse(file).documentElement,cards)
        importwcu(self.file)
        return cards

    def fields(self):
        return self.numFields

    def setNumFields(self):
        self.numFields = int(2)
