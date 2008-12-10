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

    # bug in matplotlib
    def keyReleaseEvent(self, evt):
        evt.ignore()

    def keyPressEvent(self, evt):
        evt.ignore()

class AdjustableFigure(QWidget):

    def __init__(self, config, name, figureFunc, choices=None):
        QWidget.__init__(self)
        self.config = config
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
        if self.choices:
            self.addCombo()

    def addWidget(self, widget):
        self.vbox.addWidget(widget)

    def addFigure(self):
        if self.range is None:
            self.figureCanvas = AnkiFigureCanvas(self.figureFunc())
        else:
            self.figureCanvas = AnkiFigureCanvas(self.figureFunc(self.range))
        self.addWidget(self.figureCanvas)
        self.vbox.addLayout(self.hbox)

    def updateFigure(self):
        self.updateTimer = None
        self.setUpdatesEnabled(False)
        idx = self.vbox.indexOf(self.figureCanvas)
        self.vbox.removeWidget(self.figureCanvas)
        self.figureCanvas.deleteLater()
        self.figureCanvas = AnkiFigureCanvas(self.figureFunc(self.range))
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
        self.onPeriodChange(idx)

    def onPeriodChange(self, index):
        self.config['graphs.period.' + self.name] = index
        self.range = self.choices[index]
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

class IntervalGraph(QDialog):

    def __init__(self, parent, *args):
        QDialog.__init__(self, parent, Qt.Window)
        ui.dialogs.open("Graphs", self)
        self.setAttribute(Qt.WA_DeleteOnClose)

    def reject(self):
        saveGeom(self, "graphs")
        ui.dialogs.close("Graphs")
        QDialog.reject(self)

def intervalGraph(parent, deck):
    widgets = []
    dg = anki.graphs.DeckGraphs(deck)
    # dialog setup
    d = IntervalGraph(parent)
    d.setWindowTitle(_("Deck Graphs"))
    if parent.config.get('graphsGeom'):
        restoreGeom(d, "graphs")
    else:
        if sys.platform.startswith("darwin"):
            d.setMinimumSize(740, 680)
        else:
            d.setMinimumSize(690, 715)
    scroll = QScrollArea(d)
    topBox = QVBoxLayout(d)
    topBox.addWidget(scroll)
    frame = QWidget(scroll)
    vbox = QVBoxLayout(frame)
    vbox.setMargin(0)
    vbox.setSpacing(0)
    frame.setLayout(vbox)

    range = [7, 30, 90, 180, 365, 730, 1095, 1460, 1825]

    # views
    nextDue = AdjustableFigure(parent.config, 'due', dg.nextDue, range)
    nextDue.addWidget(QLabel(_("<h1>Due</h1>")))
    nextDue.addFigure()
    vbox.addWidget(nextDue)
    widgets.append(nextDue)

    cumDue = AdjustableFigure(parent.config, 'cum', dg.cumulativeDue, range)
    cumDue.addWidget(QLabel(_("<h1>Cumulative Due</h1>")))
    cumDue.addFigure()
    vbox.addWidget(cumDue)
    widgets.append(cumDue)

    interval = AdjustableFigure(parent.config, 'interval', dg.intervalPeriod, range)
    interval.addWidget(QLabel(_("<h1>Intervals</h1>")))
    interval.addFigure()
    vbox.addWidget(interval)
    widgets.append(interval)

    added = AdjustableFigure(parent.config, 'added', dg.addedRecently, range)
    added.addWidget(QLabel(_("<h1>Added</h1>")))
    added.addFigure()
    vbox.addWidget(added)
    widgets.append(added)

    answered = AdjustableFigure(parent.config, 'answered', lambda *args: apply(
        dg.addedRecently, args + ('firstAnswered',)), range)
    answered.addWidget(QLabel(_("<h1>First Answered</h1>")))
    answered.addFigure()
    vbox.addWidget(answered)
    widgets.append(answered)

    eases = AdjustableFigure(parent.config, 'eases', dg.easeBars)
    eases.addWidget(QLabel(_("<h1>Eases</h1>")))
    eases.addFigure()
    vbox.addWidget(eases)
    widgets.append(eases)

    scroll.setWidget(frame)

    hbox = QHBoxLayout()

    def showHideAll():
        for w in widgets:
            w.showHide()
        frame.adjustSize()

    def onShowHideToggle(b, w):
        key = 'graphs.shown.' + w.name
        parent.config[key] = not parent.config.get(key, True)
        showHideAll()

    def onShowHide():
        nameMap = {
            'due': _("Due"),
            'cum': _("Cumulative"),
            'interval': _("Interval"),
            'added': _("Added"),
            'answered': _("First Answered"),
            'eases': _("Eases"),
            }
        m = QMenu(parent)
        for graph in widgets:
            name = graph.name
            shown = parent.config.get('graphs.shown.' + name, True)
            action = QAction(parent)
            action.setCheckable(True)
            action.setChecked(shown)
            action.setText(nameMap[name])
            action.connect(action, SIGNAL("toggled(bool)"),
                           lambda b, g=graph: onShowHideToggle(b, g))
            m.addAction(action)
        m.exec_(showhide.mapToGlobal(QPoint(0,0)))

    def onHelp():
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + "Graphs"))

    showhide = QPushButton(_("Show/Hide"))
    hbox.addWidget(showhide)
    showhide.connect(showhide, SIGNAL("clicked()"),
                     onShowHide)

    buttonBox = QDialogButtonBox(d)
    buttonBox.setOrientation(Qt.Horizontal)
    close = buttonBox.addButton(QDialogButtonBox.Close)
    close.setDefault(True)
    d.connect(buttonBox, SIGNAL("rejected()"), d.close)
    help = buttonBox.addButton(QDialogButtonBox.Help)
    d.connect(buttonBox, SIGNAL("helpRequested()"),
              onHelp)

    hbox.addWidget(buttonBox)

    topBox.addLayout(hbox)

    showHideAll()

    d.show()
