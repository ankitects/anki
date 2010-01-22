# -*- coding: utf-8 -*-
# Copyright: rick@vanosten.net
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Importing DingsBums?! decks (see dingsbums.vanosten.net)
========================================================

GENERAL:
* DingsBums?! files are xml with relational content.
* DingsBums?!'s data format is more relational than Anki's. Therefore some of the relations are denormalized.

* A stack in DingsBums?! is a deck in Anki
* An entry type in DingsBums?! is a model in Anki
* An entry type attribute in DingsBums?! is a field in Anki
* An entry type attribute item in DingsBums?! does not exist in Anki. It is just the contents of a field denormalized.
* There is not concept of units and categories in Anki.
* An entry in DingsBums?! is basically a fact in Anki
* There are no cards in DingsBums?!
* There is a special plugin in Anki for Pinyin. Therefore syllable settings from DingsBums?! are ignored.
* The locale settings in DingsBums?! have never been active and are therefore ignored.
* All statistics will get lost - i.e. no historic informaiton about progress will be migrated to Anki.
* The DingsBums?! stack needs to end with *.xml in order to be recognizable in Anki import.
* The learning levels from DingsBums?! are not taken into account because they do not really match spaced repetition.

DESIGN OF MAPPING FROM DingsBums?! TO Anki
*
* The contents of units and categories are transferred as tags to Anki: unit/category label + "_" + unit/category name.
* If unit/category name has space, then it is replaced by "_"
* The fields "base", "target", explanation", example", "pronounciation" and "relation" are created as fields in Anki
* The fields are only created and used in Anki, if they were visible in DingsBums?!, i.e. < 3:
VISIBILITY_ALWAYS = 0;
VISIBILITY_QUERY = 1;
VISIBILITY_SOLUTION = 2;
VISIBILITY_NEVER = 3;

* The name of the fields in Anki is taken from the labels defined in the stack properties
* The description field of Anki is not used/displayed. Therefore there is not much sense to transfer the contents of title, author, notes, copyright and license.
* The visibility options in DingsBums?! are used as hints to make cards in Anki:
    + Two card templates are made for each model and then applied to each fact when importing.
    + "Forward": Base -> "Question", target -> "Answer"; if "always", then part of question; if "solution" or "part of query" then part of answer
    + "Reverse": Target -> "Answer", base -> "Question"
    + Unit and category are not shown, as they are tags and there is no possibility to distinguish between visibility settings in this case.

CHANGES MADE TO LIBANKI:
* Added libanki/anki/import/dingsbums.py
* Added DingsBumsImporter to importers at end of file libanki/anki/import/__init__.py
* Added libanki/tests/importing/dingsbums.xml
* Added method test_dingsbums() to libanki/anki/tests/test_importing.py
"""

from anki.importing import Importer
from anki import DeckStorage
from anki.facts import Fact
from anki.models import FieldModel
from anki.models import CardModel
from anki.models import Model
from anki.lang import _

from xml.sax import make_parser
from xml.sax.handler import ContentHandler
import sys

class DingsBumsHandler(ContentHandler):

    def __init__(self, deck):
        self.eid = "0"
        self.attributeItems = {}
        self.unitCategories = {}
        self.attributes = {}
        self.currentContent = ""
        self.labels = {}
        self.labels["pro"] = u"Pronunciation" # the user cannot change this label and therefore not in xml-file
        self.labels["rel"] = u"Relation"
        self.visibility = {}
        self.models = {}
        self.typeAttributes = {} # mapping of entry type and attribute name (e.g. "ET8_A1", "ET8_A2", ...)
        self.deck = deck
        self.f = None # the current fact
        self.countFacts = 0

    def startElement(self, name, attrs):
        """Implements SAX interface"""
        if name in ["etai", "unit", "category"]:
            self.eid = attrs["eid"]
        elif "eta" == name:
            self.attributes[attrs["eid"]] = attrs["n"]
        elif "entrytype" == name:
            self.createModel(attrs)
        elif "e" == name:
            self.createFact(attrs)

    def endElement(self, name):
        """Implements SAX interface"""
        if "vocabulary" == name:
            self.deck.updateProgress()
        elif name.endswith("label"):
            self.labels[name.replace("label", "")] = self.currentContent
        elif name.startswith("vis"):
            self.visibility[name.replace("vis", "")] = self.currentContent
        elif "etai" == name:
            self.attributeItems[self.eid] = self.currentContent
        elif "etattributes" == name:
            self.deck.updateProgress()
        elif "entrytypes" == name:
            self.deck.updateProgress()
        elif "name" == name:
            self.unitCategories[self.eid] = self.prepareTag(self.currentContent)
        elif "units" == name:
            self.deck.updateProgress()
        elif "categories" == name:
            self.deck.updateProgress()
        elif "entries" == name:
            self.deck.updateProgress()
        elif "e" == name:
            self.deck.addFact(self.f)
            self.countFacts += 1
        # there is a not logical mapping between the tags for fields and names in VocabInfo
        # See net.vanosten.dings.consts.Constants.XML_*
        elif "o" == name:
            self.f.__setitem__(self.labels["b"], self.currentContent)
        elif "d" == name:
            self.f.__setitem__(self.labels["t"], self.currentContent)
        elif "ep" == name:
            self.f.__setitem__(self.labels["exp"], self.currentContent)
        elif "ea" == name:
            self.f.__setitem__(self.labels["ex"], self.currentContent)
        elif "p" == name:
            self.f.__setitem__(self.labels["pro"], self.currentContent)
        elif "r" == name:
            self.f.__setitem__(self.labels["rel"], self.currentContent)

    def characters(self, content):
        """Implements SAX interface"""
        self.currentContent = content.strip()

    def createModel(self, attrs):
        """Makes a new Anki (fact) model from an entry type.
        The card models are made each time from scratch in order that evt. model specific fields (attributes) can make part."""
        m = Model(attrs["n"])
        # field model for standard fields
        m.addFieldModel(FieldModel(self.labels["b"], True, False)) #there is no uniqueness check in DingsBums?!
        m.addFieldModel(FieldModel(self.labels["t"], True, False))
        for aField in ["exp", "ex", "pro", "rel"]:
            if self.visibility[aField] in "012":
                m.addFieldModel(FieldModel(self.labels[aField], False, False))
        # field models for attributes
        for attr in ["a1", "a2" "a3", "a4"]:
            if attr in attrs.keys():
                m.addFieldModel(FieldModel(self.attributes[attrs[attr]], False, False))
                self.typeAttributes[attrs["eid"] + "_" + attr] = self.attributes[attrs[attr]]

        # card model for front
        frontStrings = ["%(" + self.labels["b"] + ")s"]
        backStrings = ["%(" + self.labels["t"] + ")s"]
        for aField in ["exp", "ex", "pro", "rel"]:
            if self.visibility[aField] in "01":
                frontStrings.append("%(" + self.labels[aField] + ")s")
            if self.visibility[aField] in "02":
                backStrings.append("%(" + self.labels[aField] + ")s")
        m.addCardModel(CardModel(u'Forward', "<br>".join(frontStrings), "<br>".join(backStrings)))
        # card model for back
        m.addCardModel(CardModel(u'Reverse', unicode("%(" + self.labels["t"] + ")s"), unicode("%(" + self.labels["b"] + ")s")))
        # tags is just the name without spaces
        m.tags = self.prepareTag(m.name)

        # link
        self.models[attrs["eid"]] = m
        self.deck.addModel(m)

    def createFact(self, attrs):
        """Makes a new Anki fact from an entry."""
        model = self.models[attrs["et"]]
        self.f = Fact(model)
        # process attributes
        for attr in ["a1", "a2" "a3", "a4"]:
            if attr in attrs.keys():
                self.f.__setitem__(self.typeAttributes[attrs["et"] + "_" + attr], self.attributeItems[attrs[attr]])
        # process tags. Unit, Category plus entry type name
        tagString = unicode(self.unitCategories[attrs["u"]] + " " + self.unitCategories[attrs["c"]] + " " + model.tags)
        self.f.tags = tagString

    def prepareTag(self, stringWithSpace):
        parts = stringWithSpace.split()
        return "_".join(parts)

class DingsBumsImporter(Importer):
    needMapper = False # needs to overwrite default in Importer - otherwise Mapping dialog is shown in GUI

    def __init__(self, deck, file):
        Importer.__init__(self, deck, file)
        self.deck = deck
        self.file = file
        self.total = 0

    def doImport(self):
        """Totally overrides the method in Importer"""
        num = 7 # the number of updates to progress bar (see references in method endElement in DingsBumsHandler
        self.deck.startProgress(num)
        self.deck.updateProgress(_("Importing..."))

        # parse the DingsBums?! xml file
        handler = DingsBumsHandler(self.deck)
        saxparser = make_parser(  )
        saxparser.setContentHandler(handler)
        saxparser.parse(self.file)
        self.total = handler.countFacts
        self.deck.finishProgress()
        self.deck.setModified()

if __name__ == '__main__':
    print "Starting ..."

    # for testing you can start it standalone. Use an argument to specify the file to import
    filename = str(sys.argv[1])

    mydeck = DeckStorage.Deck()
    i = DingsBumsImporter(mydeck, filename)
    i.doImport()
    assert 7 == i.total
    mydeck.s.close()

    print "... Finished"
    sys.exit(1)
