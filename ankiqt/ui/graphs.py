# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys
import anki, anki.graphs, anki.utils
from ankiqt import ui
from ankiqt.ui.utils import saveGeom, restoreGeom
import ankiqt

from matplotlib.backends.backend_qt4agg import FigureCanvasQTAgg as FigureCanvas
from matplotlib import rc
rc('font', **{'sans-serif': 'Arial',
              'serif': 'Arial',
              'size': 14.0})
rc('legend', fontsize=14.0)

class AnkiFigureCanvas (FigureCanvas):
    def __init__(self, fig, parent=None):
        self.fig = fig
        FigureCanvas.__init__(self, self.fig)
        self.setParent(parent)

        self.fig.subplots_adjust(left=0.08, right=0.96, bottom=0.15, top=0.95)

        FigureCanvas.setSizePolicy(self,
                                   QSizePolicy.Expanding,
                                   QSizePolicy.Fixed)
        FigureCanvas.updateGeometry(self)

    def sizeHint(self):
        w, h = self.get_width_height()
        return QSize(w+30, h+30)

    def keyReleaseEvent(self, evt):
        evt.ignore()

    def keyPressEvent(self, evt):
        evt.ignore()

    def wheelEvent(self, evt):
        evt.ignore()

class AdjustableFigure(QWidget):

    def __init__(self, parent, name, figureFunc, choices=None):
        QWidget.__init__(self)
        self.parent = parent
        self.config = parent.config
        self.name = name
        self.vbox = QVBoxLayout()
        self.vbox.setSpacing(2)
        self.range = None
        self.choices = choices
        self.figureFunc = figureFunc
        self.setLayout(self.vbox)
        self.updateTimer = None
        self.hbox = QHBoxLayout()
        self.hbox.addSpacing(10)
        self.hbox.addStretch(1)
        self.figureCanvas = None
        if self.choices:
            self.addCombo()

    def addWidget(self, widget):
        self.vbox.addWidget(widget)

    def addFigure(self):
        if self.range is None:
            self.figureCanvas = AnkiFigureCanvas(self.figureFunc())
        else:
            if self.range:
                self.figureCanvas = AnkiFigureCanvas(self.figureFunc(self.range))
            else:
                self.figureCanvas = AnkiFigureCanvas(self.figureFunc())
        self.addWidget(self.figureCanvas)
        self.vbox.addLayout(self.hbox)

    def updateFigure(self):
        if self.parent.inDbHandler:
            return
        self.updateTimer = None
        self.setUpdatesEnabled(False)
        idx = self.vbox.indexOf(self.figureCanvas)
        self.vbox.removeWidget(self.figureCanvas)
        if not self.figureCanvas:
            self.addFigure()
        else:
            self.figureCanvas.deleteLater()
            if self.range:
                self.figureCanvas = AnkiFigureCanvas(self.figureFunc(self.range))
            else:
                self.figureCanvas = AnkiFigureCanvas(self.figureFunc())
        self.vbox.insertWidget(idx, self.figureCanvas)
        self.setUpdatesEnabled(True)

    def addCombo(self):
        self.periodCombo = QComboBox()
        self.periodCombo.addItems(QStringList(
            [anki.utils.fmtTimeSpan(x*86400, point = -1) for x in self.choices]))
        self.hbox.addWidget(self.periodCombo)
        idx = self.config.get('graphs.period.' + self.name, 1)
        self.periodCombo.setCurrentIndex(idx)
        self.connect(self.periodCombo, SIGNAL("currentIndexChanged(int)"),
                     self.onPeriodChange)
        self.onPeriodChange(idx, initialSkip=True)

    def onPeriodChange(self, index, initialSkip=False):
        self.config['graphs.period.' + self.name] = index
        self.range = self.choices[index]
        if not initialSkip:
            self.scheduleUpdate()

    def scheduleUpdate(self):
        if not self.updateTimer:
            self.updateTimer = QTimer(self)
            self.updateTimer.setSingleShot(True)
            self.updateTimer.start(200)
            self.connect(self.updateTimer, SIGNAL("timeout()"),
                         self.updateFigure)
        else:
            self.updateTimer.setInterval(200)

    def addExplanation(self, text):
        self.explanation = QLabel(text)
        self.hbox.insertWidget(1, self.explanation)

    def showHide(self):
        shown = self.config.get('graphs.shown.' + self.name, True)
        self.setVisible(shown)
        if shown and not self.figureCanvas:
            self.addFigure()

class IntervalGraph(QDialog):

    def __init__(self, parent, *args):
        QDialog.__init__(self, parent, Qt.Window)
        ui.dialogs.open("Graphs", self)
        self.setAttribute(Qt.WA_DeleteOnClose)

    def reject(self):
        saveGeom(self, "graphs")
        ui.dialogs.close("Graphs")
        QDialog.reject(self)

class GraphWindow(object):

    nameMap = {
        'due': _("Due"),
        'cum': _("Cumulative"),
        'interval': _("Interval"),
        'added': _("Added"),
        'answered': _("First Answered"),
        'eases': _("Eases"),
        'reps': _("Repetitions"),
        'times': _("Review Time"),
        }

    def __init__(self, parent, deck):
        self.parent = parent
        self.deck = deck
        self.widgets = []
        self.dg = anki.graphs.DeckGraphs(deck)
        self.diag = IntervalGraph(parent)
        self.diag.setWindowTitle(_("Deck Graphs"))
        if parent.config.get('graphsGeom'):
            restoreGeom(self.diag, "graphs")
        else:
            if sys.platform.startswith("darwin"):
                self.diag.setMinimumSize(740, 680)
            else:
                self.diag.setMinimumSize(690, 715)
        scroll = QScrollArea(self.diag)
        topBox = QVBoxLayout(self.diag)
        topBox.addWidget(scroll)
        self.frame = QWidget(scroll)
        self.vbox = QVBoxLayout(self.frame)
        self.vbox.setMargin(0)
        self.vbox.setSpacing(0)
        self.frame.setLayout(self.vbox)
        self.range = [7, 30, 90, 180, 365, 730, 1095, 1460, 1825]
        scroll.setWidget(self.frame)
        self.hbox = QHBoxLayout()
        topBox.addLayout(self.hbox)
        self.setupGraphs()
        self.setupButtons()
        self.showHideAll()
        self.diag.show()

    def setupGraphs(self):
        nextDue = AdjustableFigure(self.parent, 'due', self.dg.nextDue, self.range)
        nextDue.addWidget(QLabel(_("<h1>Due</h1>")))
        self.vbox.addWidget(nextDue)
        self.widgets.append(nextDue)

        workload = AdjustableFigure(self.parent, 'reps', self.dg.workDone, self.range)
        workload.addWidget(QLabel(_("<h1>Reps</h1>")))
        self.vbox.addWidget(workload)
        self.widgets.append(workload)

        times = AdjustableFigure(self.parent, 'times', self.dg.timeSpent, self.range)
        times.addWidget(QLabel(_("<h1>Review Time</h1>")))
        self.vbox.addWidget(times)
        self.widgets.append(times)

        added = AdjustableFigure(self.parent, 'added', self.dg.addedRecently, self.range)
        added.addWidget(QLabel(_("<h1>Added</h1>")))
        self.vbox.addWidget(added)
        self.widgets.append(added)

        answered = AdjustableFigure(self.parent, 'answered', lambda *args: apply(
            self.dg.addedRecently, args + ('firstAnswered',)), self.range)
        answered.addWidget(QLabel(_("<h1>First Answered</h1>")))
        self.vbox.addWidget(answered)
        self.widgets.append(answered)

        cumDue = AdjustableFigure(self.parent, 'cum', self.dg.cumulativeDue, self.range)
        cumDue.addWidget(QLabel(_("<h1>Cumulative Due</h1>")))
        self.vbox.addWidget(cumDue)
        self.widgets.append(cumDue)

        interval = AdjustableFigure(self.parent, 'interval', self.dg.intervalPeriod, self.range)
        interval.addWidget(QLabel(_("<h1>Intervals</h1>")))
        self.vbox.addWidget(interval)
        self.widgets.append(interval)

        eases = AdjustableFigure(self.parent, 'eases', self.dg.easeBars)
        eases.addWidget(QLabel(_("<h1>Eases</h1>")))
        self.vbox.addWidget(eases)
        self.widgets.append(eases)

    def setupButtons(self):
        self.showhide = QPushButton(_("Show/Hide"))
        self.hbox.addWidget(self.showhide)
        self.showhide.connect(self.showhide, SIGNAL("clicked()"),
                              self.onShowHide)
        refresh = QPushButton(_("Refresh"))
        self.hbox.addWidget(refresh)
        self.showhide.connect(refresh, SIGNAL("clicked()"),
                              self.onRefresh)
        buttonBox = QDialogButtonBox(self.diag)
        buttonBox.setOrientation(Qt.Horizontal)
        close = buttonBox.addButton(QDialogButtonBox.Close)
        close.setDefault(True)
        self.diag.connect(buttonBox, SIGNAL("rejected()"), self.diag.close)
        help = buttonBox.addButton(QDialogButtonBox.Help)
        self.diag.connect(buttonBox, SIGNAL("helpRequested()"),
                  self.onHelp)
        self.hbox.addWidget(buttonBox)

    def showHideAll(self):
        self.deck.startProgress(len(self.widgets))
        for w in self.widgets:
            self.deck.updateProgress(_("Processing..."))
            w.showHide()
        self.frame.adjustSize()
        self.deck.finishProgress()

    def onShowHideToggle(self, b, w):
        key = 'graphs.shown.' + w.name
        self.parent.config[key] = not self.parent.config.get(key, True)
        self.showHideAll()

    def onShowHide(self):
        m = QMenu(self.parent)
        for graph in self.widgets:
            name = graph.name
            shown = self.parent.config.get('graphs.shown.' + name, True)
            action = QAction(self.parent)
            action.setCheckable(True)
            action.setChecked(shown)
            action.setText(self.nameMap[name])
            action.connect(action, SIGNAL("toggled(bool)"),
                           lambda b, g=graph: self.onShowHideToggle(b, g))
            m.addAction(action)
        m.exec_(self.showhide.mapToGlobal(QPoint(0,0)))

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + "Graphs"))

    def onRefresh(self):
        self.deck.startProgress(len(self.widgets))
        self.dg.stats = None
        for w in self.widgets:
            self.deck.updateProgress(_("Processing..."))
            w.updateFigure()
        self.deck.finishProgress()

def intervalGraph(*args):
    return GraphWindow(*args)
