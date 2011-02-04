# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import ankiqt, simplejson, time, cStringIO, zipfile, tempfile, os, re, gzip
import traceback, urllib2, socket, cgi
from ankiqt.ui.utils import saveGeom, restoreGeom, showInfo
from anki.utils import fmtTimeSpan

URL = "http://ankiweb.net/file/"
#URL = "http://localhost:8001/file/"

R_ID = 0
R_USERNAME = 1
R_TITLE = 2
R_DESCRIPTION = 3
R_TAGS = 4
R_VERSION = 5
R_FACTS = 6
R_SIZE = 7
R_COUNT = 8
R_MODIFIED = 9
R_FNAME = 10

class GetShared(QDialog):

    def __init__(self, parent, type):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.form = ankiqt.forms.getshared.Ui_Dialog()
        self.form.setupUi(self)
        self.ok = True
        self.conErrMsg = _("""\
<b>Unable to connect to the server.<br><br>
Please check your network connection or try again in a few minutes.</b><br>
<br>
Error was:<pre>%s</pre>""")
        restoreGeom(self, "getshared")
        self.setupTable()
        self.onChangeType(type)
        if type == 0:
            self.setWindowTitle(_("Download Shared Deck"))
        else:
            self.setWindowTitle(_("Download Shared Plugin"))
        if self.ok:
            self.exec_()

    def setupTable(self):
        self.connect(
            self.form.table, SIGNAL("currentCellChanged(int,int,int,int)"),
            self.onCellChanged)
        self.form.table.verticalHeader().setDefaultSectionSize(
            self.parent.config['editLineSize'])
        self.connect(self.form.search, SIGNAL("textChanged(QString)"),
                     self.limit)

    def fetchData(self):
        self.parent.setProgressParent(None)
        self.parent.startProgress()
        self.parent.updateProgress()
        try:
            socket.setdefaulttimeout(30)
            try:
                sock = urllib2.urlopen(
                    URL + "search?t=%d&c=1" % self.type)
                data = sock.read()
                try:
                    data = gzip.GzipFile(fileobj=cStringIO.StringIO(data)).read()
                except:
                    # the server is sending gzipped data, but a transparent
                    # proxy or antivirus software may be decompressing it
                    # before we get it
                    pass
                self.allList = simplejson.loads(unicode(data))
            except:
                showInfo(self.conErrMsg % cgi.escape(unicode(
                    traceback.format_exc(), "utf-8", "replace")))
                self.close()
                self.ok = False
                return
        finally:
            self.parent.finishProgress()
            socket.setdefaulttimeout(None)
        self.form.search.setFocus()
        self.typeChanged()
        self.limit()

    def limit(self, txt=""):
        if not txt:
            self.curList = self.allList
        else:
            txt = unicode(txt).lower()
            self.curList = [
                l for l in self.allList
                if (txt in l[R_TITLE].lower() or
                    txt in l[R_DESCRIPTION].lower() or
                    txt in l[R_TAGS].lower())]
        self.redraw()

    def redraw(self):
        self.form.table.setSortingEnabled(False)
        self.form.table.setRowCount(len(self.curList))
        self.items = {}
        if self.type == 0:
            cols = (R_TITLE, R_FACTS, R_COUNT, R_MODIFIED)
        else:
            cols = (R_TITLE, R_COUNT, R_MODIFIED)
        for rc, r in enumerate(self.curList):
            for cc, c in enumerate(cols):
                if c == R_FACTS or c == R_COUNT:
                    txt = unicode("%15d" % r[c])
                elif c == R_MODIFIED:
                    days = int(((time.time() - r[c])/(24*60*60)))
                    txt = ngettext("%6d day ago", "%6d days ago", days) % days
                else:
                    txt = unicode(r[c])
                item = QTableWidgetItem(txt)
                item.setFlags(item.flags() & ~Qt.ItemIsEditable)
                self.items[item] = r
                self.form.table.setItem(rc, cc, item)
        self.form.table.setSortingEnabled(True)
        if self.type == 0:
            self.form.table.sortItems(2, Qt.DescendingOrder)
        else:
            self.form.table.sortItems(1, Qt.DescendingOrder)
        self.form.table.selectRow(0)
        self.onCellChanged(None, None, None, None)

    def onCellChanged(self, row, col, x, y):
        ci = self.form.table.currentItem()
        if not ci:
            self.form.bottomLabel.setText(_("Nothing selected."))
            return
        r = self.items[ci]
        self.curRow = r
        self.form.bottomLabel.setText(_("""\
<b>Title</b>: %(title)s<br>
<b>Tags</b>: %(tags)s<br>
<b>Size</b>: %(size)0.2fKB<br>
<b>Uploader</b>: %(author)s<br>
<b>Downloads</b>: %(count)s<br>
<b>Modified</b>: %(mod)s ago<br>
<br>%(description)s""") % {
            'title': r[R_TITLE],
            'tags': r[R_TAGS],
            'size': r[R_SIZE] / 1024.0,
            'author': r[R_USERNAME],
            'count': r[R_COUNT],
            'mod': fmtTimeSpan(time.time() - r[R_MODIFIED]),
            'description': r[R_DESCRIPTION].replace("\n", "<br>"),
            })
        self.form.scrollAreaWidgetContents.adjustSize()
        self.form.scrollArea.setWidget(self.form.scrollAreaWidgetContents)

    def onChangeType(self, type):
        self.type = type
        self.fetchData()

    def typeChanged(self):
        self.form.table.clear()
        if self.type == 0:
            self.form.table.setColumnCount(4)
            self.form.table.setHorizontalHeaderLabels([
                _("Title"), _("Facts"), _("Downloads"), _("Modified")])
        else:
            self.form.table.setColumnCount(3)
            self.form.table.setHorizontalHeaderLabels([
                _("Title"), _("Downloads"), _("Modified")])
        self.form.table.horizontalHeader().setResizeMode(
            0, QHeaderView.Stretch)
        self.form.table.verticalHeader().hide()

    def accept(self):
        if self.type == 0:
            if not self.parent.saveAndClose(hideWelcome=True, parent=self):
                return QDialog.accept(self)
        (fd, tmpname) = tempfile.mkstemp(prefix="anki")
        tmpfile = os.fdopen(fd, "w+b")
        cnt = 0
        try:
            socket.setdefaulttimeout(30)
            self.parent.setProgressParent(self)
            self.parent.startProgress()
            self.parent.updateProgress()
            try:
                sock = urllib2.urlopen(
                    URL + "get?id=%d" %
                    self.curRow[R_ID])
                while 1:
                    data = sock.read(32768)
                    if not data:
                        break
                    cnt += len(data)
                    tmpfile.write(data)
                    self.parent.updateProgress(
                        label=_("Downloaded %dKB") % (cnt/1024.0))
            except:
                showInfo(self.conErrMsg % cgi.escape(unicode(
                    traceback.format_exc(), "utf-8", "replace")))
                self.close()
                return
        finally:
            socket.setdefaulttimeout(None)
            self.parent.setProgressParent(None)
            self.parent.finishProgress()
            QDialog.accept(self)
        # file is fetched
        tmpfile.seek(0)
        self.handleFile(tmpfile)
        QDialog.accept(self)

    def handleFile(self, file):
        ext = os.path.splitext(self.curRow[R_FNAME])[1]
        if ext == ".zip":
            z = zipfile.ZipFile(file)
        else:
            z = None
        tit = self.curRow[R_TITLE]
        tit = re.sub("[^][A-Za-z0-9 ()\-]", "", tit)
        tit = tit[0:40]
        if self.type == 0:
            # deck
            dd = self.parent.documentDir
            p = os.path.join(dd, tit + ".anki")
            if os.path.exists(p):
                tit += "%d" % time.time()
            for l in z.namelist():
                if l == "shared.anki":
                    dpath = os.path.join(dd, tit + ".anki")
                    open(dpath, "wb").write(z.read(l))
                elif l.startswith("shared.media/"):
                    try:
                        os.mkdir(os.path.join(dd, tit + ".media"))
                    except OSError:
                        pass
                    open(os.path.join(dd, tit + ".media",
                                      os.path.basename(l)),"wb").write(z.read(l))
            self.parent.loadDeck(dpath)
        else:
            pd = self.parent.pluginsFolder()
            if z:
                for l in z.infolist():
                    try:
                        os.makedirs(os.path.join(
                            pd, os.path.dirname(l.filename)))
                    except OSError:
                        pass
                    if l.filename.endswith("/"):
                        # directory
                        continue
                    path = os.path.join(pd, l.filename)
                    open(path, "wb").write(z.read(l.filename))
            else:
                open(os.path.join(pd, tit + ext), "wb").write(file.read())
            showInfo(_("Plugin downloaded. Please restart Anki."),
                     parent=self)

