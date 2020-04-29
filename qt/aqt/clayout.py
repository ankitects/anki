# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import collections
import json
import re
from typing import Optional

import aqt
from anki.cards import Card
from anki.consts import *
from anki.lang import _, ngettext
from anki.utils import isMac, isWin, joinFields
from aqt import gui_hooks
from aqt.qt import *
from aqt.sound import av_player, play_clicked_audio
from aqt.theme import theme_manager
from aqt.utils import (
    askUser,
    downArrow,
    getOnlyText,
    openHelp,
    restoreGeom,
    saveGeom,
    showInfo,
    showWarning,
)
from aqt.webview import AnkiWebView


class CardLayout(QDialog):
    card: Optional[Card]

    def __init__(self, mw, note, ord=0, parent=None, addMode=False):
        QDialog.__init__(self, parent or mw, Qt.Window)
        mw.setupDialogGC(self)
        self.mw = aqt.mw
        self.parent = parent or mw
        self.note = note
        self.ord = ord
        self.col = self.mw.col
        self.mm = self.mw.col.models
        self.model = note.model()
        self.mw.checkpoint(_("Card Types"))
        self.addMode = addMode
        if addMode:
            # save it to DB temporarily
            self.emptyFields = []
            for name, val in list(note.items()):
                if val.strip():
                    continue
                self.emptyFields.append(name)
                note[name] = "(%s)" % name
            note.flush()
        self.removeColons()
        self.setupTopArea()
        self.setupMainArea()
        self.setupButtons()
        self.setupShortcuts()
        self.setWindowTitle(_("Card Types for %s") % self.model["name"])
        v1 = QVBoxLayout()
        v1.addWidget(self.topArea)
        v1.addWidget(self.mainArea)
        v1.addLayout(self.buttons)
        v1.setContentsMargins(12, 12, 12, 12)
        self.setLayout(v1)
        gui_hooks.card_layout_will_show(self)
        self.redraw()
        restoreGeom(self, "CardLayout")
        self.setWindowModality(Qt.ApplicationModal)
        self.show()
        # take the focus away from the first input area when starting up,
        # as users tend to accidentally type into the template
        self.setFocus()

    def redraw(self):
        did = None
        if hasattr(self.parent, "deckChooser"):
            did = self.parent.deckChooser.selectedId()
        self.cards = self.col.previewCards(self.note, 2, did=did)
        idx = self.ord
        if idx >= len(self.cards):
            self.ord = len(self.cards) - 1

        self.redrawing = True
        self.updateTopArea()
        self.updateMainArea()
        self.redrawing = False
        self.onCardSelected(self.ord)

    def setupShortcuts(self):
        for i in range(1, 9):
            QShortcut(
                QKeySequence("Ctrl+%d" % i),
                self,
                activated=lambda i=i: self.selectCard(i),
            )

    def selectCard(self, n):
        self.ord = n - 1
        self.redraw()

    def setupTopArea(self):
        self.topArea = QWidget()
        self.topAreaForm = aqt.forms.clayout_top.Ui_Form()
        self.topAreaForm.setupUi(self.topArea)
        self.topAreaForm.templateOptions.setText(_("Options") + " " + downArrow())
        self.topAreaForm.templateOptions.clicked.connect(self.onMore)
        self.topAreaForm.templatesBox.currentIndexChanged.connect(self.onCardSelected)

    def updateTopArea(self):
        cnt = self.mw.col.models.useCount(self.model)
        self.topAreaForm.changesLabel.setText(
            ngettext(
                "Changes below will affect the %(cnt)d note that uses this card type.",
                "Changes below will affect the %(cnt)d notes that use this card type.",
                cnt,
            )
            % dict(cnt=cnt)
        )
        self.updateCardNames()

    def updateCardNames(self):
        self.redrawing = True
        combo = self.topAreaForm.templatesBox
        combo.clear()
        combo.addItems(self._summarizedName(t) for t in self.model["tmpls"])
        combo.setCurrentIndex(self.ord)
        combo.setEnabled(not self._isCloze())
        self.redrawing = False

    def _summarizedName(self, tmpl):
        return "{}: {}: {} -> {}".format(
            tmpl["ord"] + 1,
            tmpl["name"],
            self._fieldsOnTemplate(tmpl["qfmt"]),
            self._fieldsOnTemplate(tmpl["afmt"]),
        )

    def _fieldsOnTemplate(self, fmt):
        matches = re.findall("{{[^#/}]+?}}", fmt)
        charsAllowed = 30
        result = collections.OrderedDict()
        for m in matches:
            # strip off mustache
            m = re.sub(r"[{}]", "", m)
            # strip off modifiers
            m = m.split(":")[-1]
            # don't show 'FrontSide'
            if m == "FrontSide":
                continue

            if m not in result:
                result[m] = True
                charsAllowed -= len(m)
                if charsAllowed <= 0:
                    break

        str = "+".join(result.keys())
        if charsAllowed <= 0:
            str += "+..."
        return str

    def _isCloze(self):
        return self.model["type"] == MODEL_CLOZE

    def setupMainArea(self):
        w = self.mainArea = QWidget()
        l = QHBoxLayout()
        l.setContentsMargins(0, 0, 0, 0)
        l.setSpacing(3)
        left = QWidget()
        # template area
        tform = self.tform = aqt.forms.template.Ui_Form()
        tform.setupUi(left)
        tform.label1.setText(" →")
        tform.label2.setText(" →")
        tform.labelc1.setText(" ↗")
        tform.labelc2.setText(" ↘")
        if self.style().objectName() == "gtk+":
            # gtk+ requires margins in inner layout
            tform.tlayout1.setContentsMargins(0, 11, 0, 0)
            tform.tlayout2.setContentsMargins(0, 11, 0, 0)
            tform.tlayout3.setContentsMargins(0, 11, 0, 0)
        tform.groupBox_3.setTitle(_("Styling (shared between cards)"))
        tform.front.textChanged.connect(self.saveCard)
        tform.css.textChanged.connect(self.saveCard)
        tform.back.textChanged.connect(self.saveCard)
        l.addWidget(left, 5)
        # preview area
        right = QWidget()
        pform = self.pform = aqt.forms.preview.Ui_Form()
        pform.setupUi(right)
        if self.style().objectName() == "gtk+":
            # gtk+ requires margins in inner layout
            pform.frontPrevBox.setContentsMargins(0, 11, 0, 0)
            pform.backPrevBox.setContentsMargins(0, 11, 0, 0)

        self.setupWebviews()

        l.addWidget(right, 5)
        w.setLayout(l)

    def setupWebviews(self):
        pform = self.pform
        pform.frontWeb = AnkiWebView(title="card layout front")
        pform.frontPrevBox.addWidget(pform.frontWeb)
        pform.backWeb = AnkiWebView(title="card layout back")
        pform.backPrevBox.addWidget(pform.backWeb)
        jsinc = [
            "jquery.js",
            "browsersel.js",
            "mathjax/conf.js",
            "mathjax/MathJax.js",
            "reviewer.js",
        ]
        pform.frontWeb.stdHtml(
            self.mw.reviewer.revHtml(), css=["reviewer.css"], js=jsinc, context=self,
        )
        pform.backWeb.stdHtml(
            self.mw.reviewer.revHtml(), css=["reviewer.css"], js=jsinc, context=self,
        )
        pform.frontWeb.set_bridge_command(self._on_bridge_cmd, self)
        pform.backWeb.set_bridge_command(self._on_bridge_cmd, self)

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("play:"):
            play_clicked_audio(cmd, self.card)

    def updateMainArea(self):
        if self._isCloze():
            cnt = len(self.mm.availOrds(self.model, joinFields(self.note.fields)))
            for g in self.pform.groupBox, self.pform.groupBox_2:
                g.setTitle(g.title() + _(" (1 of %d)") % max(cnt, 1))

    def onRemove(self):
        if len(self.model["tmpls"]) < 2:
            return showInfo(_("At least one card type is required."))
        idx = self.ord
        cards = self.mm.tmplUseCount(self.model, idx)
        cards = ngettext("%d card", "%d cards", cards) % cards
        msg = _("Delete the '%(a)s' card type, and its %(b)s?") % dict(
            a=self.model["tmpls"][idx]["name"], b=cards
        )
        if not askUser(msg):
            return
        if not self.mm.remTemplate(self.model, self.cards[idx].template()):
            return showWarning(
                _(
                    """\
Removing this card type would cause one or more notes to be deleted. \
Please create a new card type first."""
                )
            )
        self.redraw()

    def removeColons(self):
        # colons in field names conflict with the template language
        for fld in self.model["flds"]:
            if ":" in fld["name"]:
                self.mm.renameField(self.model, fld, fld["name"])

    # Buttons
    ##########################################################################

    def setupButtons(self):
        l = self.buttons = QHBoxLayout()
        help = QPushButton(_("Help"))
        help.setAutoDefault(False)
        l.addWidget(help)
        help.clicked.connect(self.onHelp)
        l.addStretch()
        addField = QPushButton(_("Add Field"))
        addField.setAutoDefault(False)
        l.addWidget(addField)
        addField.clicked.connect(self.onAddField)
        if not self._isCloze():
            flip = QPushButton(_("Flip"))
            flip.setAutoDefault(False)
            l.addWidget(flip)
            flip.clicked.connect(self.onFlip)
        l.addStretch()
        close = QPushButton(_("Close"))
        close.setAutoDefault(False)
        l.addWidget(close)
        close.clicked.connect(self.accept)

    # Cards
    ##########################################################################

    def onCardSelected(self, idx):
        if self.redrawing:
            return
        self.card = self.cards[idx]
        self.ord = idx
        self.playedAudio = {}
        self.readCard()
        self.renderPreview()

    def readCard(self):
        t = self.card.template()
        self.redrawing = True
        self.tform.front.setPlainText(t["qfmt"])
        self.tform.css.setPlainText(self.model["css"])
        self.tform.back.setPlainText(t["afmt"])
        self.tform.front.setAcceptRichText(False)
        self.tform.css.setAcceptRichText(False)
        self.tform.back.setAcceptRichText(False)
        tab_width = self.fontMetrics().width(" " * 4)
        self.tform.front.setTabStopDistance(tab_width)
        self.tform.css.setTabStopDistance(tab_width)
        self.tform.back.setTabStopDistance(tab_width)
        self.redrawing = False

    def saveCard(self):
        if self.redrawing:
            return
        text = self.tform.front.toPlainText()
        self.card.template()["qfmt"] = text
        text = self.tform.css.toPlainText()
        self.card.model()["css"] = text
        text = self.tform.back.toPlainText()
        self.card.template()["afmt"] = text
        self.renderPreview()

    # Preview
    ##########################################################################

    _previewTimer = None

    def renderPreview(self):
        # schedule a preview when timing stops
        self.cancelPreviewTimer()
        self._previewTimer = self.mw.progress.timer(500, self._renderPreview, False)

    def cancelPreviewTimer(self):
        if self._previewTimer:
            self._previewTimer.stop()
            self._previewTimer = None

    def _renderPreview(self) -> None:
        self.cancelPreviewTimer()

        c = self.card
        ti = self.maybeTextInput

        bodyclass = theme_manager.body_classes_for_card_ord(c.ord)

        q = ti(self.mw.prepare_card_text_for_display(c.q(reload=True)))
        q = gui_hooks.card_will_show(q, c, "clayoutQuestion")

        a = ti(self.mw.prepare_card_text_for_display(c.a()), type="a")
        a = gui_hooks.card_will_show(a, c, "clayoutAnswer")

        # use _showAnswer to avoid the longer delay
        self.pform.frontWeb.eval("_showAnswer(%s,'%s');" % (json.dumps(q), bodyclass))
        self.pform.backWeb.eval("_showAnswer(%s, '%s');" % (json.dumps(a), bodyclass))

        if c.id not in self.playedAudio:
            av_player.play_tags(c.question_av_tags() + c.answer_av_tags())
            self.playedAudio[c.id] = True

        self.updateCardNames()

    def maybeTextInput(self, txt, type="q"):
        if "[[type:" not in txt:
            return txt
        origLen = len(txt)
        txt = txt.replace("<hr id=answer>", "")
        hadHR = origLen != len(txt)

        def answerRepl(match):
            res = self.mw.reviewer.correct("exomple", "an example")
            if hadHR:
                res = "<hr id=answer>" + res
            return res

        if type == "q":
            repl = "<input id='typeans' type=text value='exomple' readonly='readonly'>"
            repl = "<center>%s</center>" % repl
        else:
            repl = answerRepl
        return re.sub(r"\[\[type:.+?\]\]", repl, txt)

    # Card operations
    ######################################################################

    def onRename(self):
        name = getOnlyText(_("New name:"), default=self.card.template()["name"])
        if not name:
            return
        if name in [
            c.template()["name"] for c in self.cards if c.template()["ord"] != self.ord
        ]:
            return showWarning(_("That name is already used."))
        self.card.template()["name"] = name
        self.redraw()

    def onReorder(self):
        n = len(self.cards)
        cur = self.card.template()["ord"] + 1
        pos = getOnlyText(_("Enter new card position (1...%s):") % n, default=str(cur))
        if not pos:
            return
        try:
            pos = int(pos)
        except ValueError:
            return
        if pos < 1 or pos > n:
            return
        if pos == cur:
            return
        pos -= 1
        self.mm.moveTemplate(self.model, self.card.template(), pos)
        self.ord = pos
        self.redraw()

    def _newCardName(self):
        n = len(self.cards) + 1
        while 1:
            name = _("Card %d") % n
            if name not in [c.template()["name"] for c in self.cards]:
                break
            n += 1
        return name

    def onAddCard(self):
        cnt = self.mw.col.models.useCount(self.model)
        txt = (
            ngettext(
                "This will create %d card. Proceed?",
                "This will create %d cards. Proceed?",
                cnt,
            )
            % cnt
        )
        if not askUser(txt):
            return
        name = self._newCardName()
        t = self.mm.newTemplate(name)
        old = self.card.template()
        t["qfmt"] = old["qfmt"]
        t["afmt"] = old["afmt"]
        self.mm.addTemplate(self.model, t)
        self.ord = len(self.cards)
        self.redraw()

    def onFlip(self):
        old = self.card.template()
        self._flipQA(old, old)
        self.redraw()

    def _flipQA(self, src, dst):
        m = re.match("(?s)(.+)<hr id=answer>(.+)", src["afmt"])
        if not m:
            showInfo(
                _(
                    """\
Anki couldn't find the line between the question and answer. Please \
adjust the template manually to switch the question and answer."""
                )
            )
            return
        dst["afmt"] = "{{FrontSide}}\n\n<hr id=answer>\n\n%s" % src["qfmt"]
        dst["qfmt"] = m.group(2).strip()
        return True

    def onMore(self):
        m = QMenu(self)

        if not self._isCloze():
            a = m.addAction(_("Add Card Type..."))
            a.triggered.connect(self.onAddCard)

            a = m.addAction(_("Remove Card Type..."))
            a.triggered.connect(self.onRemove)

            a = m.addAction(_("Rename Card Type..."))
            a.triggered.connect(self.onRename)

            a = m.addAction(_("Reposition Card Type..."))
            a.triggered.connect(self.onReorder)

            m.addSeparator()

            t = self.card.template()
            if t["did"]:
                s = _(" (on)")
            else:
                s = _(" (off)")
            a = m.addAction(_("Deck Override...") + s)
            a.triggered.connect(self.onTargetDeck)

        a = m.addAction(_("Browser Appearance..."))
        a.triggered.connect(self.onBrowserDisplay)

        m.exec_(self.topAreaForm.templateOptions.mapToGlobal(QPoint(0, 0)))

    def onBrowserDisplay(self):
        d = QDialog()
        f = aqt.forms.browserdisp.Ui_Dialog()
        f.setupUi(d)
        t = self.card.template()
        f.qfmt.setText(t.get("bqfmt", ""))
        f.afmt.setText(t.get("bafmt", ""))
        if t.get("bfont"):
            f.overrideFont.setChecked(True)
        f.font.setCurrentFont(QFont(t.get("bfont", "Arial")))
        f.fontSize.setValue(t.get("bsize", 12))
        f.buttonBox.accepted.connect(lambda: self.onBrowserDisplayOk(f))
        d.exec_()

    def onBrowserDisplayOk(self, f):
        t = self.card.template()
        t["bqfmt"] = f.qfmt.text().strip()
        t["bafmt"] = f.afmt.text().strip()
        if f.overrideFont.isChecked():
            t["bfont"] = f.font.currentFont().family()
            t["bsize"] = f.fontSize.value()
        else:
            for key in ("bfont", "bsize"):
                if key in t:
                    del t[key]

    def onTargetDeck(self):
        from aqt.tagedit import TagEdit

        t = self.card.template()
        d = QDialog(self)
        d.setWindowTitle("Anki")
        d.setMinimumWidth(400)
        l = QVBoxLayout()
        lab = QLabel(
            _(
                """\
Enter deck to place new %s cards in, or leave blank:"""
            )
            % self.card.template()["name"]
        )
        lab.setWordWrap(True)
        l.addWidget(lab)
        te = TagEdit(d, type=1)
        te.setCol(self.col)
        l.addWidget(te)
        if t["did"]:
            te.setText(self.col.decks.get(t["did"])["name"])
            te.selectAll()
        bb = QDialogButtonBox(QDialogButtonBox.Close)
        bb.rejected.connect(d.close)
        l.addWidget(bb)
        d.setLayout(l)
        d.exec_()
        if not te.text().strip():
            t["did"] = None
        else:
            t["did"] = self.col.decks.id(te.text())

    def onAddField(self):
        diag = QDialog(self)
        form = aqt.forms.addfield.Ui_Dialog()
        form.setupUi(diag)
        fields = [f["name"] for f in self.model["flds"]]
        form.fields.addItems(fields)
        form.font.setCurrentFont(QFont("Arial"))
        form.size.setValue(20)
        diag.show()
        # Work around a Qt bug,
        # https://bugreports.qt-project.org/browse/QTBUG-1894
        if isMac or isWin:
            # No problems on Macs or Windows.
            form.fields.showPopup()
        else:
            # Delay showing the pop-up.
            self.mw.progress.timer(200, form.fields.showPopup, False)
        if not diag.exec_():
            return
        if form.radioQ.isChecked():
            obj = self.tform.front
        else:
            obj = self.tform.back
        self._addField(
            obj,
            fields[form.fields.currentIndex()],
            form.font.currentFont().family(),
            form.size.value(),
        )

    def _addField(self, widg, field, font, size):
        t = widg.toPlainText()
        t += "\n<div style='font-family: %s; font-size: %spx;'>{{%s}}</div>\n" % (
            font,
            size,
            field,
        )
        widg.setPlainText(t)
        self.saveCard()

    # Closing & Help
    ######################################################################

    def accept(self):
        self.reject()

    def reject(self):
        self.cancelPreviewTimer()
        av_player.stop_and_clear_queue()
        if self.addMode:
            # remove the filler fields we added
            for name in self.emptyFields:
                self.note[name] = ""
            self.mw.col.db.execute("delete from notes where id = ?", self.note.id)
        self.mm.save(self.model, templates=True)
        self.mw.reset()
        saveGeom(self, "CardLayout")
        self.pform.frontWeb = None
        self.pform.backWeb = None
        return QDialog.reject(self)

    def onHelp(self):
        openHelp("templates")
