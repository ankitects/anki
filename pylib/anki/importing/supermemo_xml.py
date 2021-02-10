# Copyright: petr.michalec@gmail.com
# License: GNU GPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# pytype: disable=attribute-error
# type: ignore

import re
import sys
import time
import unicodedata
from string import capwords
from typing import List, Optional, Union
from xml.dom import minidom
from xml.dom.minidom import Element, Text

from anki.collection import Collection
from anki.importing.noteimp import ForeignCard, ForeignNote, NoteImporter
from anki.stdmodels import addBasicModel


class SmartDict(dict):
    """
    See http://www.peterbe.com/plog/SmartDict
    Copyright 2005, Peter Bengtsson, peter@fry-it.com

    A smart dict can be instanciated either from a pythonic dict
    or an instance object (eg. SQL recordsets) but it ensures that you can
    do all the convenient lookups such as x.first_name, x['first_name'] or
    x.get('first_name').
    """

    def __init__(self, *a, **kw) -> None:
        if a:
            if isinstance(type(a[0]), dict):
                kw.update(a[0])
            elif isinstance(type(a[0]), object):
                kw.update(a[0].__dict__)
            elif hasattr(a[0], "__class__") and a[0].__class__.__name__ == "SmartDict":
                kw.update(a[0].__dict__)

        dict.__init__(self, **kw)
        self.__dict__ = self


class SuperMemoElement(SmartDict):
    "SmartDict wrapper to store SM Element data"

    def __init__(self, *a, **kw) -> None:
        SmartDict.__init__(self, *a, **kw)
        # default content
        self.__dict__["lTitle"] = None
        self.__dict__["Title"] = None
        self.__dict__["Question"] = None
        self.__dict__["Answer"] = None
        self.__dict__["Count"] = None
        self.__dict__["Type"] = None
        self.__dict__["ID"] = None
        self.__dict__["Interval"] = None
        self.__dict__["Lapses"] = None
        self.__dict__["Repetitions"] = None
        self.__dict__["LastRepetiton"] = None
        self.__dict__["AFactor"] = None
        self.__dict__["UFactor"] = None


# This is an AnkiImporter
class SupermemoXmlImporter(NoteImporter):

    needMapper = False
    allowHTML = True

    """
    Supermemo XML export's to Anki parser.
    Goes through a SM collection and fetch all elements.

    My SM collection was a big mess where topics and items were mixed.
    I was unable to parse my content in a regular way like for loop on
    minidom.getElementsByTagName() etc. My collection had also an
    limitation, topics were splited into branches with max 100 items
    on each. Learning themes were in deep structure. I wanted to have
    full title on each element to be stored in tags.

    Code should be upgrade to support importing of SM2006 exports.
    """

    def __init__(self, col: Collection, file: str) -> None:
        """Initialize internal varables.
        Pameters to be exposed to GUI are stored in self.META"""
        NoteImporter.__init__(self, col, file)
        m = addBasicModel(self.col)
        m["name"] = "Supermemo"
        self.col.models.save(m)
        self.initMapping()

        self.lines = None
        self.numFields = int(2)

        # SmXmlParse VARIABLES
        self.xmldoc = None
        self.pieces = []
        self.cntBuf = []  # to store last parsed data
        self.cntElm = []  # to store SM Elements data
        self.cntCol = []  # to store SM Colections data

        # store some meta info related to parse algorithm
        # SmartDict works like dict / class wrapper
        self.cntMeta = SmartDict()
        self.cntMeta.popTitles = False
        self.cntMeta.title = []

        # META stores controls of import scritp, should be
        # exposed to import dialog. These are default values.
        self.META = SmartDict()
        self.META.resetLearningData = False  # implemented
        self.META.onlyMemorizedItems = False  # implemented
        self.META.loggerLevel = 2  # implemented 0no,1info,2error,3debug
        self.META.tagAllTopics = True
        self.META.pathsToBeTagged = [
            "English for begginers",
            "Advanced English 97",
            "Phrasal Verbs",
        ]  # path patterns to be tagged - in gui entered like 'Advanced English 97|My Vocablary'
        self.META.tagMemorizedItems = True  # implemented
        self.META.logToStdOutput = False  # implemented

        self.notes = []

    ## TOOLS

    def _fudgeText(self, text: str) -> str:
        "Replace sm syntax to Anki syntax"
        text = text.replace("\n\r", "<br>")
        text = text.replace("\n", "<br>")
        return text

    def _unicode2ascii(self, str: str) -> str:
        "Remove diacritic punctuation from strings (titles)"
        return "".join(
            [
                c
                for c in unicodedata.normalize("NFKD", str)
                if not unicodedata.combining(c)
            ]
        )

    def _decode_htmlescapes(self, html: str) -> str:
        """Unescape HTML code."""
        # In case of bad formated html you can import MinimalSoup etc.. see BeautifulSoup source code
        from bs4 import BeautifulSoup

        # my sm2004 also ecaped & char in escaped sequences.
        html = re.sub("&amp;", "&", html)

        # https://anki.tenderapp.com/discussions/ankidesktop/39543-anki-is-replacing-the-character-by-when-i-exit-the-html-edit-mode-ctrlshiftx
        if html.find(">") < 0:
            return html

        # unescaped solitary chars < or > that were ok for minidom confuse btfl soup
        # html = re.sub(u'>',u'&gt;',html)
        # html = re.sub(u'<',u'&lt;',html)

        return str(BeautifulSoup(html, "html.parser"))

    def _afactor2efactor(self, af: float) -> float:
        # Adapted from <http://www.supermemo.com/beta/xml/xml-core.htm>

        # Ranges for A-factors and E-factors
        af_min = 1.2
        af_max = 6.9
        ef_min = 1.3
        ef_max = 3.3

        # Sanity checks for the A-factor
        if af < af_min:
            af = af_min
        elif af > af_max:
            af = af_max

        # Scale af to the range 0..1
        af_scaled = (af - af_min) / (af_max - af_min)
        # Rescale to the interval ef_min..ef_max
        ef = ef_min + af_scaled * (ef_max - ef_min)

        return ef

    ## DEFAULT IMPORTER METHODS

    def foreignNotes(self) -> List[ForeignNote]:

        # Load file and parse it by minidom
        self.loadSource(self.file)

        # Migrating content / time consuming part
        # addItemToCards is called for each sm element
        self.logger("Parsing started.")
        self.parse()
        self.logger("Parsing done.")

        # Return imported cards
        self.total = len(self.notes)
        self.log.append("%d cards imported." % self.total)
        return self.notes

    def fields(self) -> int:
        return 2

    ## PARSER METHODS

    def addItemToCards(self, item: SuperMemoElement) -> None:
        "This method actually do conversion"

        # new anki card
        note = ForeignNote()

        # clean Q and A
        note.fields.append(self._fudgeText(self._decode_htmlescapes(item.Question)))
        note.fields.append(self._fudgeText(self._decode_htmlescapes(item.Answer)))
        note.tags = []

        # pre-process scheduling data
        # convert learning data
        if (
            not self.META.resetLearningData
            and int(item.Interval) >= 1
            and getattr(item, "LastRepetition", None)
        ):
            # migration of LearningData algorithm
            tLastrep = time.mktime(time.strptime(item.LastRepetition, "%d.%m.%Y"))
            tToday = time.time()
            card = ForeignCard()
            card.ivl = int(item.Interval)
            card.lapses = int(item.Lapses)
            card.reps = int(item.Repetitions) + int(item.Lapses)
            nextDue = tLastrep + (float(item.Interval) * 86400.0)
            remDays = int((nextDue - time.time()) / 86400)
            card.due = self.col.sched.today + remDays
            card.factor = int(
                self._afactor2efactor(float(item.AFactor.replace(",", "."))) * 1000
            )
            note.cards[0] = card

        # categories & tags
        # it's worth to have every theme (tree structure of sm collection) stored in tags, but sometimes not
        # you can deceide if you are going to tag all toppics or just that containing some pattern
        tTaggTitle = False
        for pattern in self.META.pathsToBeTagged:
            if (
                item.lTitle is not None
                and pattern.lower() in " ".join(item.lTitle).lower()
            ):
                tTaggTitle = True
                break
        if tTaggTitle or self.META.tagAllTopics:
            # normalize - remove diacritic punctuation from unicode chars to ascii
            item.lTitle = [self._unicode2ascii(topic) for topic in item.lTitle]

            # Transfrom xyz / aaa / bbb / ccc on Title path to Tag  xyzAaaBbbCcc
            #  clean things like [999] or [111-2222] from title path, example: xyz / [1000-1200] zyx / xyz
            #  clean whitespaces
            #  set Capital letters for first char of the word
            tmp = list(
                {re.sub(r"(\[[0-9]+\])", " ", i).replace("_", " ") for i in item.lTitle}
            )
            tmp = list({re.sub(r"(\W)", " ", i) for i in tmp})
            tmp = list({re.sub("^[0-9 ]+$", "", i) for i in tmp})
            tmp = list({capwords(i).replace(" ", "") for i in tmp})
            tags = [j[0].lower() + j[1:] for j in tmp if j.strip() != ""]

            note.tags += tags

            if self.META.tagMemorizedItems and int(item.Interval) > 0:
                note.tags.append("Memorized")

            self.logger("Element tags\t- " + repr(note.tags), level=3)

        self.notes.append(note)

    def logger(self, text: str, level: int = 1) -> None:
        "Wrapper for Anki logger"

        dLevels = {0: "", 1: "Info", 2: "Verbose", 3: "Debug"}
        if level <= self.META.loggerLevel:
            # self.deck.updateProgress(_(text))

            if self.META.logToStdOutput:
                print(
                    self.__class__.__name__
                    + " - "
                    + dLevels[level].ljust(9)
                    + " -\t"
                    + text
                )

    # OPEN AND LOAD
    def openAnything(self, source):
        """Open any source / actually only opening of files is used
        @return an open handle which must be closed after use, i.e., handle.close()"""

        if source == "-":
            return sys.stdin

        # try to open with urllib (if source is http, ftp, or file URL)
        import urllib.error
        import urllib.parse
        import urllib.request

        try:
            return urllib.request.urlopen(source)
        except OSError:
            pass

        # try to open with native open function (if source is pathname)
        try:
            return open(source)
        except OSError:
            pass

        # treat source as string
        import io

        return io.StringIO(str(source))

    def loadSource(self, source: str) -> None:
        """Load source file and parse with xml.dom.minidom"""
        self.source = source
        self.logger("Load started...")
        sock = open(self.source)
        self.xmldoc = minidom.parse(sock).documentElement
        sock.close()
        self.logger("Load done.")

    # PARSE
    def parse(self, node: Optional[Union[Text, Element]] = None) -> None:
        "Parse method - parses document elements"

        if node is None and self.xmldoc is not None:
            node = self.xmldoc

        _method = "parse_%s" % node.__class__.__name__
        if hasattr(self, _method):
            parseMethod = getattr(self, _method)
            parseMethod(node)
        else:
            self.logger("No handler for method %s" % _method, level=3)

    def parse_Document(self, node):
        "Parse XML document"

        self.parse(node.documentElement)

    def parse_Element(self, node: Element) -> None:
        "Parse XML element"

        _method = "do_%s" % node.tagName
        if hasattr(self, _method):
            handlerMethod = getattr(self, _method)
            handlerMethod(node)
        else:
            self.logger("No handler for method %s" % _method, level=3)
            # print traceback.print_exc()

    def parse_Text(self, node: Text) -> None:
        "Parse text inside elements. Text is stored into local buffer."

        text = node.data
        self.cntBuf.append(text)

    # def parse_Comment(self, node):
    #    """
    #    Source can contain XML comments, but we ignore them
    #    """
    #    pass

    # DO
    def do_SuperMemoCollection(self, node: Element) -> None:
        "Process SM Collection"

        for child in node.childNodes:
            self.parse(child)

    def do_SuperMemoElement(self, node: Element) -> None:
        "Process SM Element (Type - Title,Topics)"

        self.logger("=" * 45, level=3)

        self.cntElm.append(SuperMemoElement())
        self.cntElm[-1]["lTitle"] = self.cntMeta["title"]

        # parse all child elements
        for child in node.childNodes:
            self.parse(child)

        # strip all saved strings, just for sure
        for key in list(self.cntElm[-1].keys()):
            if hasattr(self.cntElm[-1][key], "strip"):
                self.cntElm[-1][key] = self.cntElm[-1][key].strip()

        # pop current element
        smel = self.cntElm.pop()

        # Process cntElm if is valid Item (and not an Topic etc..)
        # if smel.Lapses != None and smel.Interval != None and smel.Question != None and smel.Answer != None:
        if smel.Title is None and smel.Question is not None and smel.Answer is not None:
            if smel.Answer.strip() != "" and smel.Question.strip() != "":

                # migrate only memorized otherway skip/continue
                if self.META.onlyMemorizedItems and not (int(smel.Interval) > 0):
                    self.logger("Element skiped  \t- not memorized ...", level=3)
                else:
                    # import sm element data to Anki
                    self.addItemToCards(smel)
                    self.logger("Import element \t- " + smel["Question"], level=3)

                    # print element
                    self.logger("-" * 45, level=3)
                    for key in list(smel.keys()):
                        self.logger(
                            "\t%s %s" % ((key + ":").ljust(15), smel[key]), level=3
                        )
            else:
                self.logger("Element skiped  \t- no valid Q and A ...", level=3)

        else:
            # now we know that item was topic
            # parseing of whole node is now finished

            # test if it's really topic
            if smel.Title is not None:
                # remove topic from title list
                t = self.cntMeta["title"].pop()
                self.logger("End of topic \t- %s" % (t), level=2)

    def do_Content(self, node: Element) -> None:
        "Process SM element Content"

        for child in node.childNodes:
            if hasattr(child, "tagName") and child.firstChild is not None:
                self.cntElm[-1][child.tagName] = child.firstChild.data

    def do_LearningData(self, node: Element) -> None:
        "Process SM element LearningData"

        for child in node.childNodes:
            if hasattr(child, "tagName") and child.firstChild is not None:
                self.cntElm[-1][child.tagName] = child.firstChild.data

    # It's being processed in do_Content now
    # def do_Question(self, node):
    #    for child in node.childNodes: self.parse(child)
    #    self.cntElm[-1][node.tagName]=self.cntBuf.pop()

    # It's being processed in do_Content now
    # def do_Answer(self, node):
    #    for child in node.childNodes: self.parse(child)
    #    self.cntElm[-1][node.tagName]=self.cntBuf.pop()

    def do_Title(self, node: Element) -> None:
        "Process SM element Title"

        t = self._decode_htmlescapes(node.firstChild.data)
        self.cntElm[-1][node.tagName] = t
        self.cntMeta["title"].append(t)
        self.cntElm[-1]["lTitle"] = self.cntMeta["title"]
        self.logger("Start of topic \t- " + " / ".join(self.cntMeta["title"]), level=2)

    def do_Type(self, node: Element) -> None:
        "Process SM element Type"

        if len(self.cntBuf) >= 1:
            self.cntElm[-1][node.tagName] = self.cntBuf.pop()


# if __name__ == '__main__':

# for testing you can start it standalone

# file = u'/home/epcim/hg2g/dev/python/sm2anki/ADVENG2EXP.xxe.esc.zaloha_FINAL.xml'
# file = u'/home/epcim/hg2g/dev/python/anki/libanki/tests/importing/supermemo/original_ENGLISHFORBEGGINERS_noOEM.xml'
# file = u'/home/epcim/hg2g/dev/python/anki/libanki/tests/importing/supermemo/original_ENGLISHFORBEGGINERS_oem_1250.xml'
# file = str(sys.argv[1])
# impo = SupermemoXmlImporter(Deck(),file)
# impo.foreignCards()

# sys.exit(1)

# vim: ts=4 sts=2 ft=python
