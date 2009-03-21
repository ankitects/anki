# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from PyQt4.QtNetwork import *
import ankiqt, simplejson, time, cStringIO, zipfile, tempfile, os, re
from ankiqt.ui.utils import saveGeom, restoreGeom, showInfo
from anki.utils import fmtTimeSpan

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
        restoreGeom(self, "getshared")
        self.setupTable()
        self.onChangeType(type)
        self.ok = False
        if type == 0:
            self.setWindowTitle(_("Download Shared Deck"))
        else:
            self.setWindowTitle(_("Download Shared Plugin"))
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
        h = QHttp(self)
        h.connect(h, SIGNAL("requestFinished(int,bool)"), self.onReqFin)
        h.setHost("anki.ichi2.net")
        #h.setHost("localhost", 8001)
        self.conId = h.get("/file/search?t=%d" % self.type)
        self.http = h
        self.parent.setProgressParent(self)
        self.parent.startProgress()

    def onReqFin(self, id, err):
        "List fetched."
        if id != self.conId:
            return
        self.parent.finishProgress()
        self.parent.setProgressParent(None)
        if err:
            showInfo(_("Unable to connect to server."), parent=self)
            self.close()
            return
        data = self.http.readAll()
        self.allList = simplejson.loads(unicode(data))
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
            cols = (R_TITLE, R_FACTS, R_COUNT)
        else:
            cols = (R_TITLE, R_COUNT)
        for rc, r in enumerate(self.curList):
            for cc, c in enumerate(cols):
                if c == R_FACTS or c == R_COUNT:
                    txt = unicode("%15d" % r[c])
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
<b>Description</b>:<br>%(description)s""") % {
            'title': r[R_TITLE],
            'tags': r[R_TAGS],
            'size': r[R_SIZE] / 1024.0,
            'author': r[R_USERNAME],
            'count': r[R_COUNT],
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
            self.form.table.setColumnCount(3)
            self.form.table.setHorizontalHeaderLabels([
                _("Title"), _("Facts"), _("Downloads")])
        else:
            self.form.table.setColumnCount(2)
            self.form.table.setHorizontalHeaderLabels([
                _("Title"), _("Downloads")])
        self.form.table.horizontalHeader().setResizeMode(
            0, QHeaderView.Stretch)
        self.form.table.verticalHeader().hide()
        self.form.table.setSelectionBehavior(QAbstractItemView.SelectRows)

    def accept(self):
        h = QHttp(self)
        h.connect(h, SIGNAL("requestFinished(int,bool)"), self.onReqFin2)
        h.setHost("anki.ichi2.net")
        #h.setHost("localhost", 8001)
        self.conId = h.get("/file/get?id=%d" % self.curRow[R_ID])
        self.http = h
        self.parent.setProgressParent(self)
        self.parent.startProgress()

    def onReqFin2(self, id, err):
        "File fetched."
        if id != self.conId:
            return
        try:
            self.parent.finishProgress()
            self.parent.setProgressParent(None)
            if err:
                showInfo(_("Unable to connect to server."), parent=self)
                self.close()
                return
            data = self.http.readAll()
            ext = os.path.splitext(self.curRow[R_FNAME])[1]
            if ext == ".zip":
                f = cStringIO.StringIO()
                f.write(data)
                z = zipfile.ZipFile(f)
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
                self.ok = True
            else:
                pd = self.parent.pluginsFolder()
                if z:
                    for l in z.infolist():
                        if not l.file_size:
                            continue
                        try:
                            os.makedirs(os.path.join(
                                pd, os.path.dirname(l.filename)))
                        except OSError:
                            pass
                        open(os.path.join(pd, l.filename), "wb").\
                                              write(z.read(l.filename))
                else:
                    open(os.path.join(pd, tit + ext), "wb").write(data)
                self.ok = True
                showInfo(_("Plugin downloaded. Please restart Anki."),
                         parent=self)
                return
        finally:
            QDialog.accept(self)

