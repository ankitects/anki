# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys
import anki, anki.graphs, anki.utils
from ankiqt import ui
from ankiqt.ui.utils import saveGeom, restoreGeom

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
                                   QSizePolicy.Expanding)
        FigureCanvas.updateGeometry(self)

    def sizeHint(self):
        w, h = self.get_width_height()
        return QSize(w+30, h+30)

    def minimumSizeHint(self):
        return QSize(10, 10)

    # bug in matplotlib
    def keyReleaseEvent(self, evt):
        evt.ignore()

    def keyPressEvent(self, evt):
        evt.ignore()

class AdjustableFigure(QWidget):

    def __init__(self, figureFunc, range=None):
        QWidget.__init__(self)
        self.vbox = QVBoxLayout()
        self.vbox.setSpacing(2)
        self.range = range
        self.figureFunc = figureFunc
        self.setLayout(self.vbox)
        self.updateTimer = None
        self.hbox = QHBoxLayout()
        self.hbox.addSpacing(10)
        self.hbox.addStretch(1)

    def addWidget(self, widget):
        self.vbox.addWidget(widget)

    def addFigure(self):
        if self.range is None:
            self.figureCanvas = AnkiFigureCanvas(self.figureFunc())
        else:
            self.figureCanvas = AnkiFigureCanvas(self.figureFunc(self.range))
        self.addWidget(self.figureCanvas)

    def updateFigure(self):
        self.updateTimer = None
        self.setUpdatesEnabled(False)
        idx = self.vbox.indexOf(self.figureCanvas)
        self.vbox.removeWidget(self.figureCanvas)
        self.figureCanvas.deleteLater()
        self.figureCanvas = AnkiFigureCanvas(self.figureFunc(self.range))
        self.vbox.insertWidget(idx, self.figureCanvas)
        self.setUpdatesEnabled(True)

    def addSlider(self, label, choices):
        self.choices = choices
        self.labelText = label
        self.label = QLabel()
        self.label.setFixedWidth(110)
        self.updateLabel()
        self.slider = QScrollBar(Qt.Horizontal)
        self.slider.setFixedWidth(150)
        self.slider.setRange(0, len(choices) - 1)
        self.slider.setValue(choices.index(self.range))
        self.slider.setFocusPolicy(Qt.TabFocus)
        self.hbox.addWidget(self.label)
        self.hbox.addWidget(self.slider)
        self.connect(self.slider, SIGNAL("valueChanged(int)"),
                     self.sliderChanged)

    def updateLabel(self):
        self.label.setText("%s: %s" % (self.labelText,
                                       anki.utils.fmtTimeSpan(self.range*86400)))

    def sliderChanged(self, index):
        self.range = self.choices[index]
        self.updateLabel()
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
        self.vbox.addLayout(self.hbox)

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
            d.setMinimumSize(670, 715)
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
    nextDue = AdjustableFigure(dg.nextDue, 30)
    nextDue.addWidget(QLabel(_("<h1>Due cards</h1>")))
    nextDue.addFigure()
    nextDue.addSlider(_("Period"), range)
    nextDue.addExplanation(_("The number of cards due each day over the "
                             "period.\n"
                             "Today is 0; cards less than zero are overdue."))

    vbox.addWidget(nextDue)

    cumDue = AdjustableFigure(dg.cumulativeDue, 30)
    cumDue.addWidget(QLabel(_("<h1>Cumulative view of due cards</h1>")))
    cumDue.addFigure()
    cumDue.addSlider(_("Period"), range)
    cumDue.addExplanation(_("The number of cards due each day, assuming "
                             "no study."))

    vbox.addWidget(cumDue)

    interval = AdjustableFigure(dg.intervalPeriod, 30)
    interval.addWidget(QLabel(_("<h1>Card intervals</h1>")))
    interval.addFigure()
    interval.addSlider(_("Period"), range)
    interval.addExplanation(_("The number of cards scheduled for a given "
                             "number of days."))
    vbox.addWidget(interval)

    added = AdjustableFigure(dg.addedRecently, 30)
    added.addWidget(QLabel(_("<h1>Added cards</h1>")))
    added.addFigure()
    added.addSlider(_("Period"), range)
    added.addExplanation(_("The number of cards added on a given day."))
    vbox.addWidget(added)

    answered = AdjustableFigure(lambda *args: apply(
        dg.addedRecently, args + ('firstAnswered',)), 30)
    answered.addWidget(QLabel(_("<h1>First answered</h1>")))
    answered.addFigure()
    answered.addSlider(_("Period"), range)
    answered.addExplanation(_("The number of cards first answered on a "
                              "given day.\nThis will be different to "
                              "'added cards' if you are\nusing a "
                              "pre-made deck."))
    vbox.addWidget(answered)

    eases = AdjustableFigure(dg.easeBars)
    eases.addWidget(QLabel(_("<h1>Card ease</h1>")))
    eases.addFigure()
    eases.addExplanation(_("The amount of times you answered a card at "
                           "each ease level."))
    vbox.addWidget(eases)

    scroll.setWidget(frame)
    buttonBox = QDialogButtonBox(d)
    buttonBox.setOrientation(Qt.Horizontal)
    close = buttonBox.addButton(QDialogButtonBox.Close)
    close.setDefault(True)

    d.connect(buttonBox, SIGNAL("rejected()"), d.close)

    topBox.addWidget(buttonBox)

    d.show()

