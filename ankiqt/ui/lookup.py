# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import urllib, re
import anki

# Tools - looking up words in the dictionary
##########################################################################

class Lookup(object):

    def __init__(self, main):
        self.main = main

    def selection(self, function):
        "Get the selected text and look it up with FUNCTION."
        text = unicode(self.main.mainWin.mainText.selectedText())
        text = text.strip()
        if "\n" in text:
            self.main.setStatus(_("Can't look up a selection with a newline."))
            return
        text = text.strip()
        if not text:
            self.main.setStatus(_("Empty selection."))
            return
        function(text)

    def edictKanji(self, text):
        self.edict(text, True)

    def edict(self, text, kanji=False):
        "Look up TEXT with edict."
        if kanji:
            x="M"
        else:
            x="U"
        baseUrl="http://www.csse.monash.edu.au/~jwb/cgi-bin/wwwjdic.cgi?1M" + x
        if isJapaneseText(text):
            baseUrl += "J"
        else:
            baseUrl += "E"
        url = baseUrl + urllib.quote(text.encode("utf-8"))
        self.main.setStatus(_("Looking %s up on edict...") % text)
        qurl = QUrl()
        qurl.setEncodedUrl(url)
        QDesktopServices.openUrl(qurl)

    def alc(self, text):
        "Look up TEXT with ALC."
        newText = urllib.quote(text.encode("utf-8"))
        url = (
            "http://eow.alc.co.jp/" +
            newText +
            "/UTF-8/?ref=sa")
        self.main.setStatus(_("Looking %s up on ALC...") % text)
        qurl = QUrl()
        qurl.setEncodedUrl(url)
        QDesktopServices.openUrl(qurl)

def isJapaneseText(text):
    "True if 70% of text is a Japanese character."
    total = len(text)
    if total == 0:
        return True
    jp = 0
    en = 0
    for c in text:
        if ord(c) >= 0x2E00 and ord(c) <= 0x9FFF:
            jp += 1
        if re.match("[A-Za-z]", c):
            en += 1
    if not jp:
        return False
    return ((jp + 1) / float(en + 1)) >= 1.0
