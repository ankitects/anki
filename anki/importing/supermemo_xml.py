# -*- coding: utf-8 -*-
# Copyright: petr.michalec@gmail.com
# License: GNU GPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys

from anki.stdmodels import addBasicModel
from anki.importing.noteimp import NoteImporter, ForeignNote, ForeignCard
from anki.lang import _
from anki.lang import ngettext

from xml.dom import minidom
from types import DictType, InstanceType
from string import capwords
import re, unicodedata, time

class SmartDict(dict):
    """
    See http://www.peterbe.com/plog/SmartDict
    Copyright 2005, Peter Bengtsson, peter@fry-it.com

    A smart dict can be instanciated either from a pythonic dict
    or an instance object (eg. SQL recordsets) but it ensures that you can
    do all the convenient lookups such as x.first_name, x['first_name'] or
    x.get('first_name').
    """

    def __init__(self, *a, **kw):
        if a:
            if type(a[0]) is DictType:
                kw.update(a[0])
            elif type(a[0]) is InstanceType:
                kw.update(a[0].__dict__)
            elif hasattr(a[0], '__class__') and a[0].__class__.__name__=='SmartDict':
                kw.update(a[0].__dict__)

        dict.__init__(self, **kw)
        self.__dict__ = self

class SuperMemoElement(SmartDict):
  "SmartDict wrapper to store SM Element data"

  def __init__(self, *a, **kw):
    SmartDict.__init__(self, *a, **kw)
    #default content
    self.__dict__['lTitle'] = None
    self.__dict__['Title'] = None
    self.__dict__['Question'] = None
    self.__dict__['Answer'] = None
    self.__dict__['Count'] = None
    self.__dict__['Type'] = None
    self.__dict__['ID'] = None
    self.__dict__['Interval'] = None
    self.__dict__['Lapses'] = None
    self.__dict__['Repetitions'] = None
    self.__dict__['LastRepetiton'] = None
    self.__dict__['AFactor'] = None
    self.__dict__['UFactor'] = None



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

    def __init__(self, *args):
        """Initialize internal varables.
        Pameters to be exposed to GUI are stored in self.META"""
        NoteImporter.__init__(self, *args)
        m = addBasicModel(self.col)
        m['name'] = "Supermemo"
        self.col.models.save(m)
        self.initMapping()

        self.lines = None
        self.numFields=int(2)

        # SmXmlParse VARIABLES
        self.xmldoc = None
        self.pieces = []
        self.cntBuf = [] #to store last parsed data
        self.cntElm = [] #to store SM Elements data
        self.cntCol = [] #to store SM Colections data

        # store some meta info related to parse algorithm
        # SmartDict works like dict / class wrapper
        self.cntMeta = SmartDict()
        self.cntMeta.popTitles = False
        self.cntMeta.title     = []

        # META stores controls of import scritp, should be
        # exposed to import dialog. These are default values.
        self.META = SmartDict()
        self.META.resetLearningData  = False            # implemented
        self.META.onlyMemorizedItems = False            # implemented
        self.META.loggerLevel = 2                       # implemented 0no,1info,2error,3debug
        self.META.tagAllTopics = True
        self.META.pathsToBeTagged = ['English for begginers', 'Advanced English 97', 'Phrasal Verbs']                # path patterns to be tagged - in gui entered like 'Advanced English 97|My Vocablary'
        self.META.tagMemorizedItems = True              # implemented
        self.META.logToStdOutput   = False              # implemented

        self.notes = []

## TOOLS

    def _fudgeText(self, text):
        "Replace sm syntax to Anki syntax"
        text = text.replace("\n\r", u"<br>")
        text = text.replace("\n", u"<br>")
        return text

    def _unicode2ascii(self,str):
        "Remove diacritic punctuation from strings (titles)"
        return u"".join([ c for c in unicodedata.normalize('NFKD', str) if not unicodedata.combining(c)])

    def _decode_htmlescapes(self,s):
        """Unescape HTML code."""
        #In case of bad formated html you can import MinimalSoup etc.. see btflsoup source code
        from BeautifulSoup import BeautifulStoneSoup as btflsoup

        #my sm2004 also ecaped & char in escaped sequences.
        s = re.sub(u'&amp;',u'&',s)
        #unescaped solitary chars < or > that were ok for minidom confuse btfl soup
        #s = re.sub(u'>',u'&gt;',s)
        #s = re.sub(u'<',u'&lt;',s)

        return unicode(btflsoup(s,convertEntities=btflsoup.HTML_ENTITIES ))


    def _unescape(self,s,initilize):
        """Note: This method is not used, BeautifulSoup does better job.
        """

        if self._unescape_trtable == None:
            self._unescape_trtable = (
              ('&euro;',u'€'), ('&#32;',u' '), ('&#33;',u'!'), ('&#34;',u'"'), ('&#35;',u'#'), ('&#36;',u'$'), ('&#37;',u'%'), ('&#38;',u'&'), ('&#39;',u"'"),
              ('&#40;',u'('), ('&#41;',u')'), ('&#42;',u'*'), ('&#43;',u'+'), ('&#44;',u','), ('&#45;',u'-'), ('&#46;',u'.'), ('&#47;',u'/'), ('&#48;',u'0'),
              ('&#49;',u'1'), ('&#50;',u'2'), ('&#51;',u'3'), ('&#52;',u'4'), ('&#53;',u'5'), ('&#54;',u'6'), ('&#55;',u'7'), ('&#56;',u'8'), ('&#57;',u'9'),
              ('&#58;',u':'), ('&#59;',u';'), ('&#60;',u'<'), ('&#61;',u'='), ('&#62;',u'>'), ('&#63;',u'?'), ('&#64;',u'@'), ('&#65;',u'A'), ('&#66;',u'B'),
              ('&#67;',u'C'), ('&#68;',u'D'), ('&#69;',u'E'), ('&#70;',u'F'), ('&#71;',u'G'), ('&#72;',u'H'), ('&#73;',u'I'), ('&#74;',u'J'), ('&#75;',u'K'),
              ('&#76;',u'L'), ('&#77;',u'M'), ('&#78;',u'N'), ('&#79;',u'O'), ('&#80;',u'P'), ('&#81;',u'Q'), ('&#82;',u'R'), ('&#83;',u'S'), ('&#84;',u'T'),
              ('&#85;',u'U'), ('&#86;',u'V'), ('&#87;',u'W'), ('&#88;',u'X'), ('&#89;',u'Y'), ('&#90;',u'Z'), ('&#91;',u'['), ('&#92;',u'\\'), ('&#93;',u']'),
              ('&#94;',u'^'), ('&#95;',u'_'), ('&#96;',u'`'), ('&#97;',u'a'), ('&#98;',u'b'), ('&#99;',u'c'), ('&#100;',u'd'), ('&#101;',u'e'), ('&#102;',u'f'),
              ('&#103;',u'g'), ('&#104;',u'h'), ('&#105;',u'i'), ('&#106;',u'j'), ('&#107;',u'k'), ('&#108;',u'l'), ('&#109;',u'm'), ('&#110;',u'n'),
              ('&#111;',u'o'), ('&#112;',u'p'), ('&#113;',u'q'), ('&#114;',u'r'), ('&#115;',u's'), ('&#116;',u't'), ('&#117;',u'u'), ('&#118;',u'v'),
              ('&#119;',u'w'), ('&#120;',u'x'), ('&#121;',u'y'), ('&#122;',u'z'), ('&#123;',u'{'), ('&#124;',u'|'), ('&#125;',u'}'), ('&#126;',u'~'),
              ('&#160;',u' '), ('&#161;',u'¡'), ('&#162;',u'¢'), ('&#163;',u'£'), ('&#164;',u'¤'), ('&#165;',u'¥'), ('&#166;',u'¦'), ('&#167;',u'§'),
              ('&#168;',u'¨'), ('&#169;',u'©'), ('&#170;',u'ª'), ('&#171;',u'«'), ('&#172;',u'¬'), ('&#173;',u'­'), ('&#174;',u'®'), ('&#175;',u'¯'),
              ('&#176;',u'°'), ('&#177;',u'±'), ('&#178;',u'²'), ('&#179;',u'³'), ('&#180;',u'´'), ('&#181;',u'µ'), ('&#182;',u'¶'), ('&#183;',u'·'),
              ('&#184;',u'¸'), ('&#185;',u'¹'), ('&#186;',u'º'), ('&#187;',u'»'), ('&#188;',u'¼'), ('&#189;',u'½'), ('&#190;',u'¾'), ('&#191;',u'¿'),
              ('&#192;',u'À'), ('&#193;',u'Á'), ('&#194;',u'Â'), ('&#195;',u'Ã'), ('&#196;',u'Ä'), ('&Aring;',u'Å'), ('&#197;',u'Å'), ('&#198;',u'Æ'),
              ('&#199;',u'Ç'), ('&#200;',u'È'), ('&#201;',u'É'), ('&#202;',u'Ê'), ('&#203;',u'Ë'), ('&#204;',u'Ì'), ('&#205;',u'Í'), ('&#206;',u'Î'),
              ('&#207;',u'Ï'), ('&#208;',u'Ð'), ('&#209;',u'Ñ'), ('&#210;',u'Ò'), ('&#211;',u'Ó'), ('&#212;',u'Ô'), ('&#213;',u'Õ'), ('&#214;',u'Ö'),
              ('&#215;',u'×'), ('&#216;',u'Ø'), ('&#217;',u'Ù'), ('&#218;',u'Ú'), ('&#219;',u'Û'), ('&#220;',u'Ü'), ('&#221;',u'Ý'), ('&#222;',u'Þ'),
              ('&#223;',u'ß'), ('&#224;',u'à'), ('&#225;',u'á'), ('&#226;',u'â'), ('&#227;',u'ã'), ('&#228;',u'ä'), ('&#229;',u'å'), ('&#230;',u'æ'),
              ('&#231;',u'ç'), ('&#232;',u'è'), ('&#233;',u'é'), ('&#234;',u'ê'), ('&#235;',u'ë'), ('&#236;',u'ì'), ('&iacute;',u'í'), ('&#237;',u'í'),
              ('&#238;',u'î'), ('&#239;',u'ï'), ('&#240;',u'ð'), ('&#241;',u'ñ'), ('&#242;',u'ò'), ('&#243;',u'ó'), ('&#244;',u'ô'), ('&#245;',u'õ'),
              ('&#246;',u'ö'), ('&#247;',u'÷'), ('&#248;',u'ø'), ('&#249;',u'ù'), ('&#250;',u'ú'), ('&#251;',u'û'), ('&#252;',u'ü'), ('&#253;',u'ý'),
              ('&#254;',u'þ'), ('&#255;',u'ÿ'), ('&#256;',u'Ā'), ('&#257;',u'ā'), ('&#258;',u'Ă'), ('&#259;',u'ă'), ('&#260;',u'Ą'), ('&#261;',u'ą'),
              ('&#262;',u'Ć'), ('&#263;',u'ć'), ('&#264;',u'Ĉ'), ('&#265;',u'ĉ'), ('&#266;',u'Ċ'), ('&#267;',u'ċ'), ('&#268;',u'Č'), ('&#269;',u'č'),
              ('&#270;',u'Ď'), ('&#271;',u'ď'), ('&#272;',u'Đ'), ('&#273;',u'đ'), ('&#274;',u'Ē'), ('&#275;',u'ē'), ('&#276;',u'Ĕ'), ('&#277;',u'ĕ'),
              ('&#278;',u'Ė'), ('&#279;',u'ė'), ('&#280;',u'Ę'), ('&#281;',u'ę'), ('&#282;',u'Ě'), ('&#283;',u'ě'), ('&#284;',u'Ĝ'), ('&#285;',u'ĝ'),
              ('&#286;',u'Ğ'), ('&#287;',u'ğ'), ('&#288;',u'Ġ'), ('&#289;',u'ġ'), ('&#290;',u'Ģ'), ('&#291;',u'ģ'), ('&#292;',u'Ĥ'), ('&#293;',u'ĥ'),
              ('&#294;',u'Ħ'), ('&#295;',u'ħ'), ('&#296;',u'Ĩ'), ('&#297;',u'ĩ'), ('&#298;',u'Ī'), ('&#299;',u'ī'), ('&#300;',u'Ĭ'), ('&#301;',u'ĭ'),
              ('&#302;',u'Į'), ('&#303;',u'į'), ('&#304;',u'İ'), ('&#305;',u'ı'), ('&#306;',u'Ĳ'), ('&#307;',u'ĳ'), ('&#308;',u'Ĵ'), ('&#309;',u'ĵ'),
              ('&#310;',u'Ķ'), ('&#311;',u'ķ'), ('&#312;',u'ĸ'), ('&#313;',u'Ĺ'), ('&#314;',u'ĺ'), ('&#315;',u'Ļ'), ('&#316;',u'ļ'), ('&#317;',u'Ľ'),
              ('&#318;',u'ľ'), ('&#319;',u'Ŀ'), ('&#320;',u'ŀ'), ('&#321;',u'Ł'), ('&#322;',u'ł'), ('&#323;',u'Ń'), ('&#324;',u'ń'), ('&#325;',u'Ņ'),
              ('&#326;',u'ņ'), ('&#327;',u'Ň'), ('&#328;',u'ň'), ('&#329;',u'ŉ'), ('&#330;',u'Ŋ'), ('&#331;',u'ŋ'), ('&#332;',u'Ō'), ('&#333;',u'ō'),
              ('&#334;',u'Ŏ'), ('&#335;',u'ŏ'), ('&#336;',u'Ő'), ('&#337;',u'ő'), ('&#338;',u'Œ'), ('&#339;',u'œ'), ('&#340;',u'Ŕ'), ('&#341;',u'ŕ'),
              ('&#342;',u'Ŗ'), ('&#343;',u'ŗ'), ('&#344;',u'Ř'), ('&#345;',u'ř'), ('&#346;',u'Ś'), ('&#347;',u'ś'), ('&#348;',u'Ŝ'), ('&#349;',u'ŝ'),
              ('&#350;',u'Ş'), ('&#351;',u'ş'), ('&#352;',u'Š'), ('&#353;',u'š'), ('&#354;',u'Ţ'), ('&#355;',u'ţ'), ('&#356;',u'Ť'), ('&#357;',u'ť'),
              ('&#358;',u'Ŧ'), ('&#359;',u'ŧ'), ('&#360;',u'Ũ'), ('&#361;',u'ũ'), ('&#362;',u'Ū'), ('&#363;',u'ū'), ('&#364;',u'Ŭ'), ('&#365;',u'ŭ'),
              ('&#366;',u'Ů'), ('&#367;',u'ů'), ('&#368;',u'Ű'), ('&#369;',u'ű'), ('&#370;',u'Ų'), ('&#371;',u'ų'), ('&#372;',u'Ŵ'), ('&#373;',u'ŵ'),
              ('&#374;',u'Ŷ'), ('&#375;',u'ŷ'), ('&#376;',u'Ÿ'), ('&#377;',u'Ź'), ('&#378;',u'ź'), ('&#379;',u'Ż'), ('&#380;',u'ż'), ('&#381;',u'Ž'),
              ('&#382;',u'ž'), ('&#383;',u'ſ'), ('&#340;',u'Ŕ'), ('&#341;',u'ŕ'), ('&#342;',u'Ŗ'), ('&#343;',u'ŗ'), ('&#344;',u'Ř'), ('&#345;',u'ř'),
              ('&#346;',u'Ś'), ('&#347;',u'ś'), ('&#348;',u'Ŝ'), ('&#349;',u'ŝ'), ('&#350;',u'Ş'), ('&#351;',u'ş'), ('&#352;',u'Š'), ('&#353;',u'š'),
              ('&#354;',u'Ţ'), ('&#355;',u'ţ'), ('&#356;',u'Ť'), ('&#577;',u'ť'), ('&#358;',u'Ŧ'), ('&#359;',u'ŧ'), ('&#360;',u'Ũ'), ('&#361;',u'ũ'),
              ('&#362;',u'Ū'), ('&#363;',u'ū'), ('&#364;',u'Ŭ'), ('&#365;',u'ŭ'), ('&#366;',u'Ů'), ('&#367;',u'ů'), ('&#368;',u'Ű'), ('&#369;',u'ű'),
              ('&#370;',u'Ų'), ('&#371;',u'ų'), ('&#372;',u'Ŵ'), ('&#373;',u'ŵ'), ('&#374;',u'Ŷ'), ('&#375;',u'ŷ'), ('&#376;',u'Ÿ'), ('&#377;',u'Ź'),
              ('&#378;',u'ź'), ('&#379;',u'Ż'), ('&#380;',u'ż'), ('&#381;',u'Ž'), ('&#382;',u'ž'), ('&#383;',u'ſ'),
          )


      #m = re.match()
      #s = s.replace(code[0], code[1])

## DEFAULT IMPORTER METHODS

    def foreignNotes(self):

        # Load file and parse it by minidom
        self.loadSource(self.file)

        # Migrating content / time consuming part
        # addItemToCards is called for each sm element
        self.logger(u'Parsing started.')
        self.parse()
        self.logger(u'Parsing done.')

        # Return imported cards
        self.total = len(self.notes)
        self.log.append(ngettext("%d card imported.", "%d cards imported.", self.total) % self.total)
        return self.notes

    def fields(self):
        return 2

## PARSER METHODS

    def addItemToCards(self,item):
        "This method actually do conversion"

        # new anki card
        note = ForeignNote()

        # clean Q and A
        note.fields.append(self._fudgeText(self._decode_htmlescapes(item.Question)))
        note.fields.append(self._fudgeText(self._decode_htmlescapes(item.Answer)))
        note.tags = []

        # pre-process scheduling data
        # convert learning data
        if (not self.META.resetLearningData
            and item.Interval >= 1
            and getattr(item, "LastRepetition", None)):
            # migration of LearningData algorithm
            tLastrep = time.mktime(time.strptime(item.LastRepetition, '%d.%m.%Y'))
            tToday = time.time()
            card = ForeignCard()
            card.ivl = int(item.Interval)
            card.lapses = int(item.Lapses)
            card.reps = int(item.Repetitions) + int(item.Lapses)
            nextDue = tLastrep + (float(item.Interval) * 86400.0)
            remDays = int((nextDue - time.time())/86400)
            card.due = self.col.sched.today+remDays
            card.factor = int(float(item.AFactor.replace(',','.'))*1000)
            note.cards[0] = card

        # categories & tags
        # it's worth to have every theme (tree structure of sm collection) stored in tags, but sometimes not
        # you can deceide if you are going to tag all toppics or just that containing some pattern
        tTaggTitle = False
        for pattern in self.META.pathsToBeTagged:
            if item.lTitle != None and pattern.lower() in u" ".join(item.lTitle).lower():
              tTaggTitle = True
              break
        if tTaggTitle or self.META.tagAllTopics:
          # normalize - remove diacritic punctuation from unicode chars to ascii
          item.lTitle = [ self._unicode2ascii(topic) for topic in item.lTitle]

          # Transfrom xyz / aaa / bbb / ccc on Title path to Tag  xyzAaaBbbCcc
          #  clean things like [999] or [111-2222] from title path, example: xyz / [1000-1200] zyx / xyz
          #  clean whitespaces
          #  set Capital letters for first char of the word
          tmp = list(set([ re.sub('(\[[0-9]+\])'   , ' ' , i ).replace('_',' ')  for i in item.lTitle ]))
          tmp = list(set([ re.sub('(\W)',' ', i )  for i in tmp ]))
          tmp = list(set([ re.sub( '^[0-9 ]+$','',i)  for i in tmp ]))
          tmp = list(set([ capwords(i).replace(' ','')  for i in tmp ]))
          tags = [ j[0].lower() + j[1:] for j in tmp if j.strip() <> '']

          note.tags += tags

          if self.META.tagMemorizedItems and item.Interval >0:
            note.tags.append("Memorized")

          self.logger(u'Element tags\t- ' + `note.tags`, level=3)

        self.notes.append(note)

    def logger(self,text,level=1):
        "Wrapper for Anki logger"

        dLevels={0:'',1:u'Info',2:u'Verbose',3:u'Debug'}
        if level<=self.META.loggerLevel:
          #self.deck.updateProgress(_(text))

          if self.META.logToStdOutput:
            print self.__class__.__name__+ u" - " + dLevels[level].ljust(9) +u' -\t'+ _(text)


    # OPEN AND LOAD
    def openAnything(self,source):
        "Open any source / actually only openig of files is used"

        if source == "-":
            return sys.stdin

        # try to open with urllib (if source is http, ftp, or file URL)
        import urllib
        try:
            return urllib.urlopen(source)
        except (IOError, OSError):
            pass

        # try to open with native open function (if source is pathname)
        try:
            return open(source)
        except (IOError, OSError):
            pass

        # treat source as string
        import StringIO
        return StringIO.StringIO(str(source))

    def loadSource(self, source):
        """Load source file and parse with xml.dom.minidom"""
        self.source = source
        self.logger(u'Load started...')
        sock = open(self.source)
        self.xmldoc = minidom.parse(sock).documentElement
        sock.close()
        self.logger(u'Load done.')


    # PARSE
    def parse(self, node=None):
        "Parse method - parses document elements"

        if node==None and self.xmldoc<>None:
          node = self.xmldoc

        _method = "parse_%s" % node.__class__.__name__
        if hasattr(self,_method):
          parseMethod = getattr(self, _method)
          parseMethod(node)
        else:
          self.logger(u'No handler for method %s' % _method, level=3)

    def parse_Document(self, node):
        "Parse XML document"

        self.parse(node.documentElement)

    def parse_Element(self, node):
        "Parse XML element"

        _method = "do_%s" % node.tagName
        if hasattr(self,_method):
          handlerMethod = getattr(self, _method)
          handlerMethod(node)
        else:
          self.logger(u'No handler for method %s' % _method, level=3)
          #print traceback.print_exc()

    def parse_Text(self, node):
        "Parse text inside elements. Text is stored into local buffer."

        text = node.data
        self.cntBuf.append(text)

    #def parse_Comment(self, node):
    #    """
    #    Source can contain XML comments, but we ignore them
    #    """
    #    pass


    # DO
    def do_SuperMemoCollection(self, node):
        "Process SM Collection"

        for child in node.childNodes: self.parse(child)

    def do_SuperMemoElement(self, node):
        "Process SM Element (Type - Title,Topics)"

        self.logger('='*45, level=3)

        self.cntElm.append(SuperMemoElement())
        self.cntElm[-1]['lTitle'] = self.cntMeta['title']

        #parse all child elements
        for child in node.childNodes: self.parse(child)

        #strip all saved strings, just for sure
        for key in self.cntElm[-1].keys():
          if hasattr(self.cntElm[-1][key], 'strip'):
            self.cntElm[-1][key]=self.cntElm[-1][key].strip()

        #pop current element
        smel = self.cntElm.pop()

        # Process cntElm if is valid Item (and not an Topic etc..)
        # if smel.Lapses != None and smel.Interval != None and smel.Question != None and smel.Answer != None:
        if smel.Title == None and smel.Question != None and smel.Answer != None:
          if smel.Answer.strip() !='' and smel.Question.strip() !='':

            # migrate only memorized otherway skip/continue
            if self.META.onlyMemorizedItems and not(int(smel.Interval) > 0):
              self.logger(u'Element skiped  \t- not memorized ...', level=3)
            else:
              #import sm element data to Anki
              self.addItemToCards(smel)
              self.logger(u"Import element \t- " + smel['Question'], level=3)

              #print element
              self.logger('-'*45, level=3)
              for key in smel.keys():
                self.logger('\t%s %s' % ((key+':').ljust(15),smel[key]), level=3 )
          else:
            self.logger(u'Element skiped  \t- no valid Q and A ...', level=3)


        else:
          # now we know that item was topic
          # parseing of whole node is now finished

          # test if it's really topic
          if smel.Title != None:
            # remove topic from title list
            t = self.cntMeta['title'].pop()
            self.logger(u'End of topic \t- %s' % (t), level=2)

    def do_Content(self, node):
        "Process SM element Content"

        for child in node.childNodes:
          if hasattr(child,'tagName') and child.firstChild != None:
            self.cntElm[-1][child.tagName]=child.firstChild.data

    def do_LearningData(self, node):
        "Process SM element LearningData"

        for child in node.childNodes:
          if hasattr(child,'tagName') and child.firstChild != None:
            self.cntElm[-1][child.tagName]=child.firstChild.data

    # It's being processed in do_Content now
    #def do_Question(self, node):
    #    for child in node.childNodes: self.parse(child)
    #    self.cntElm[-1][node.tagName]=self.cntBuf.pop()

    # It's being processed in do_Content now
    #def do_Answer(self, node):
    #    for child in node.childNodes: self.parse(child)
    #    self.cntElm[-1][node.tagName]=self.cntBuf.pop()

    def do_Title(self, node):
        "Process SM element Title"

        t = self._decode_htmlescapes(node.firstChild.data)
        self.cntElm[-1][node.tagName] = t
        self.cntMeta['title'].append(t)
        self.cntElm[-1]['lTitle'] = self.cntMeta['title']
        self.logger(u'Start of topic \t- ' + u" / ".join(self.cntMeta['title']), level=2)


    def do_Type(self, node):
        "Process SM element Type"

        if len(self.cntBuf) >=1 :
          self.cntElm[-1][node.tagName]=self.cntBuf.pop()


if __name__ == '__main__':

  # for testing you can start it standalone

  #file = u'/home/epcim/hg2g/dev/python/sm2anki/ADVENG2EXP.xxe.esc.zaloha_FINAL.xml'
  #file = u'/home/epcim/hg2g/dev/python/anki/libanki/tests/importing/supermemo/original_ENGLISHFORBEGGINERS_noOEM.xml'
  #file = u'/home/epcim/hg2g/dev/python/anki/libanki/tests/importing/supermemo/original_ENGLISHFORBEGGINERS_oem_1250.xml'
  file = str(sys.argv[1])
  impo = SupermemoXmlImporter(Deck(),file)
  impo.foreignCards()

  sys.exit(1)

# vim: ts=4 sts=2 ft=python
