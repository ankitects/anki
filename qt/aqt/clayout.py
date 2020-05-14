# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy
import json
import re
from typing import List, Optional

import aqt
from anki.cards import Card
from anki.consts import *
from anki.lang import _, ngettext
from anki.notes import Note
from anki.rsbackend import TemplateError
from anki.template import TemplateRenderContext
from anki.utils import isMac, isWin
from aqt import AnkiQt, gui_hooks
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
    tooltip,
    TR, tr, shortcut
)
from aqt.webview import AnkiWebView

# fixme: previewing with empty fields
# fixme: deck name on new cards
# fixme: card count when removing
# fixme: change tracking and tooltip in fields
# fixme: replay suppression


class CardLayout(QDialog):
    def __init__(
        self, mw: AnkiQt, note: Note, ord=0, parent: Optional[QWidget] = None,
    ):
        QDialog.__init__(self, parent or mw, Qt.Window)
        mw.setupDialogGC(self)
        self.mw = aqt.mw
        self.note = note
        self.ord = ord
        self.col = self.mw.col.weakref()
        self.mm = self.mw.col.models
        self.model = note.model()
        self.templates = self.model["tmpls"]
        self.mm._remove_from_cache(self.model["id"])
        self.mw.checkpoint(_("Card Types"))
        self.changed = False
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
        self.redraw_everything()
        restoreGeom(self, "CardLayout")
        self.setWindowModality(Qt.ApplicationModal)
        self.show()
        # take the focus away from the first input area when starting up,
        # as users tend to accidentally type into the template
        self.setFocus()

    def redraw_everything(self):
        self.ignore_change_signals = True
        self.updateTopArea()
        self.ignore_change_signals = False
        self.update_current_ordinal_and_redraw(self.ord)

    def update_current_ordinal_and_redraw(self, idx):
        if self.ignore_change_signals:
            return
        self.ord = idx
        self.playedAudio = {}
        self.fill_fields_from_template()
        self.renderPreview()

    def _isCloze(self):
        return self.model["type"] == MODEL_CLOZE

    # Top area
    ##########################################################################

    def setupTopArea(self):
        self.topArea = QWidget()
        self.topAreaForm = aqt.forms.clayout_top.Ui_Form()
        self.topAreaForm.setupUi(self.topArea)
        self.topAreaForm.templateOptions.setText(_("Options") + " " + downArrow())
        qconnect(self.topAreaForm.templateOptions.clicked, self.onMore)
        qconnect(
            self.topAreaForm.templatesBox.currentIndexChanged,
            self.update_current_ordinal_and_redraw,
        )
        self.topAreaForm.card_type_label.setText(tr(TR.CARD_TEMPLATES_CARD_TYPE))

    def updateTopArea(self):
        self.updateCardNames()

    def updateCardNames(self):
        self.ignore_change_signals = True
        combo = self.topAreaForm.templatesBox
        combo.clear()
        combo.addItems(
            self._summarizedName(idx, tmpl) for (idx, tmpl) in enumerate(self.templates)
        )
        combo.setCurrentIndex(self.ord)
        combo.setEnabled(not self._isCloze())
        self.ignore_change_signals = False

    def _summarizedName(self, idx: int, tmpl: Dict):
        return "{}: {}: {} -> {}".format(
            idx + 1,
            tmpl["name"],
            self._fieldsOnTemplate(tmpl["qfmt"]),
            self._fieldsOnTemplate(tmpl["afmt"]),
        )

    def _fieldsOnTemplate(self, fmt):
        matches = re.findall("{{[^#/}]+?}}", fmt)
        chars_allowed = 30
        field_names: List[str] = []
        for m in matches:
            # strip off mustache
            m = re.sub(r"[{}]", "", m)
            # strip off modifiers
            m = m.split(":")[-1]
            # don't show 'FrontSide'
            if m == "FrontSide":
                continue

            field_names.append(m)
            chars_allowed -= len(m)
            if chars_allowed <= 0:
                break

        s = "+".join(field_names)
        if chars_allowed <= 0:
            s += "+..."
        return s

    def setupShortcuts(self):
        self.tform.front_button.setToolTip(shortcut("Ctrl+1"))
        self.tform.back_button.setToolTip(shortcut("Ctrl+2"))
        self.tform.style_button.setToolTip(shortcut("Ctrl+3"))
        QShortcut(
            QKeySequence("Ctrl+1"),
            self,
            activated=lambda: self.tform.front_button.setChecked(True))
        QShortcut(
            QKeySequence("Ctrl+2"),
            self,
            activated=lambda: self.tform.back_button.setChecked(True))
        QShortcut(
            QKeySequence("Ctrl+3"),
            self,
            activated=lambda: self.tform.style_button.setChecked(True))

    # Main area
    ##########################################################################

    def setupMainArea(self):
        w = self.mainArea = QWidget()
        l = QHBoxLayout()
        l.setContentsMargins(0, 0, 0, 0)
        l.setSpacing(3)
        left = QWidget()
        # template area
        tform = self.tform = aqt.forms.template.Ui_Form()
        tform.setupUi(left)
        qconnect(tform.edit_area.textChanged, self.write_edits_to_template_and_redraw)
        tform.front_button.setText(tr(TR.CARD_TEMPLATES_FRONT_TEMPLATE))
        qconnect(tform.front_button.toggled, self.on_editor_toggled)
        tform.back_button.setText(tr(TR.CARD_TEMPLATES_BACK_TEMPLATE))
        qconnect(tform.back_button.toggled, self.on_editor_toggled)
        tform.style_button.setText(tr(TR.CARD_TEMPLATES_TEMPLATE_STYLING))
        qconnect(tform.style_button.toggled, self.on_editor_toggled)
        tform.groupBox.setTitle(tr(TR.CARD_TEMPLATES_TEMPLATE_BOX))

        cnt = self.mw.col.models.useCount(self.model)
        self.tform.changes_affect_label.setText(self.col.tr(
        TR.CARD_TEMPLATES_CHANGES_WILL_AFFECT_NOTES, count=cnt))

        l.addWidget(left, 5)

        self.setup_edit_area()

        widg = tform.search_edit
        widg.setPlaceholderText("Search")
        qconnect(widg.textChanged, self.on_search_changed)
        qconnect(widg.returnPressed, self.on_search_next)
        # preview area
        right = QWidget()
        self.pform: Any = aqt.forms.preview.Ui_Form()
        pform = self.pform
        pform.setupUi(right)
        pform.preview_front.setText(tr(TR.CARD_TEMPLATES_FRONT_PREVIEW))
        pform.preview_back.setText(tr(TR.CARD_TEMPLATES_BACK_PREVIEW))
        pform.preview_box.setTitle(tr(TR.CARD_TEMPLATES_PREVIEW_BOX))

        if self._isCloze():
            nums = self.note.cloze_numbers_in_fields()
            if self.ord + 1 not in nums:
                # current card is empty
                nums.append(self.ord + 1)
            self.cloze_numbers = sorted(nums)
            self.setup_cloze_number_box()
        else:
            self.cloze_numbers = []
            self.pform.cloze_number_combo.setHidden(True)

        self.setupWebviews()

        l.addWidget(right, 5)
        w.setLayout(l)

    def setup_edit_area(self):
        self.current_editor_index = 0
        self.tform.edit_area.setAcceptRichText(False)
        if qtminor < 10:
            self.tform.edit_area.setTabStopWidth(30)
        else:
            tab_width = self.fontMetrics().width(" " * 4)
            self.tform.edit_area.setTabStopDistance(tab_width)

    def setup_cloze_number_box(self):
        names = (_("Cloze %d") % n for n in self.cloze_numbers)
        self.pform.cloze_number_combo.addItems(names)
        try:
            idx = self.cloze_numbers.index(self.ord + 1)
            self.pform.cloze_number_combo.setCurrentIndex(idx)
        except ValueError:
            # invalid cloze
            pass
        qconnect(
            self.pform.cloze_number_combo.currentIndexChanged, self.on_change_cloze
        )

    def on_change_cloze(self, idx: int) -> None:
        self.ord = self.cloze_numbers[idx] - 1
        self._renderPreview()

    def on_editor_toggled(self):
        if self.tform.front_button.isChecked():
            self.current_editor_index = 0
            self.pform.preview_front.setChecked(True)
            self.add_field_button.setHidden(False)
        elif self.tform.back_button.isChecked():
            self.current_editor_index = 1
            self.pform.preview_back.setChecked(True)
            self.add_field_button.setHidden(False)
        else:
            self.current_editor_index = 2
            self.add_field_button.setHidden(True)

        self.fill_fields_from_template()

    def on_search_changed(self, text: str):
        editor = self.tform.edit_area
        if not editor.find(text):
            # try again from top
            cursor = editor.textCursor()
            cursor.movePosition(QTextCursor.Start)
            editor.setTextCursor(cursor)
            editor.find(text)

    def on_search_next(self):
        text = self.tform.search_edit.text()
        self.on_search_changed(text)

    def setupWebviews(self):
        pform = self.pform
        pform.frontWeb = AnkiWebView(title="card layout")
        pform.verticalLayout.addWidget(pform.frontWeb)
        pform.verticalLayout.setStretch(1, 99)
        pform.preview_front.isChecked()
        qconnect(pform.preview_front.toggled, self.on_preview_toggled)
        qconnect(pform.preview_back.toggled, self.on_preview_toggled)
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
        pform.frontWeb.set_bridge_command(self._on_bridge_cmd, self)

    def on_preview_toggled(self):
        self._renderPreview()

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("play:"):
            play_clicked_audio(cmd, self.rendered_card)

    def ephemeral_card_for_rendering(self) -> Card:
        card = Card(self.col)
        card.ord = self.ord
        template = copy.copy(self.current_template())
        # may differ in cloze case
        template["ord"] = card.ord
        # this fetches notetype, we should pass it in
        output = TemplateRenderContext.from_card_layout(
            self.note, card, notetype=self.model, template=template
        ).render()
        card.set_render_output(output)
        return card

    # Buttons
    ##########################################################################

    def setupButtons(self):
        l = self.buttons = QHBoxLayout()
        help = QPushButton(_("Help"))
        help.setAutoDefault(False)
        l.addWidget(help)
        qconnect(help.clicked, self.onHelp)
        l.addStretch()
        self.add_field_button = QPushButton(_("Add Field"))
        self.add_field_button.setAutoDefault(False)
        l.addWidget(self.add_field_button)
        qconnect(self.add_field_button.clicked, self.onAddField)
        if not self._isCloze():
            flip = QPushButton(_("Flip"))
            flip.setAutoDefault(False)
            l.addWidget(flip)
            qconnect(flip.clicked, self.onFlip)
        l.addStretch()
        save = QPushButton(_("Save"))
        save.setAutoDefault(False)
        l.addWidget(save)
        qconnect(save.clicked, self.accept)

        close = QPushButton(_("Cancel"))
        close.setAutoDefault(False)
        l.addWidget(close)
        qconnect(close.clicked, self.reject)

    # Reading/writing question/answer/css
    ##########################################################################

    def current_template(self) -> Dict:
        if self._isCloze():
            return self.templates[0]
        return self.templates[self.ord]

    def fill_fields_from_template(self):
        t = self.current_template()
        self.ignore_change_signals = True

        if self.current_editor_index == 0:
            text = t["qfmt"]
        elif self.current_editor_index == 1:
            text = t["afmt"]
        else:
            text = self.model["css"]

        self.tform.edit_area.setPlainText(text)
        self.ignore_change_signals = False

    def write_edits_to_template_and_redraw(self):
        if self.ignore_change_signals:
            return

        self.changed = True

        text = self.tform.edit_area.toPlainText()

        if self.current_editor_index == 0:
            self.current_template()['qfmt'] = text
        elif self.current_editor_index == 1:
            self.current_template()['afmt'] = text
        else:
            self.model["css"] = text

        self.renderPreview()

    # Preview
    ##########################################################################

    _previewTimer = None

    def renderPreview(self):
        # schedule a preview when timing stops
        self.cancelPreviewTimer()
        self._previewTimer = self.mw.progress.timer(200, self._renderPreview, False)

    def cancelPreviewTimer(self):
        if self._previewTimer:
            self._previewTimer.stop()
            self._previewTimer = None

    def _renderPreview(self) -> None:
        self.cancelPreviewTimer()

        c = self.rendered_card = self.ephemeral_card_for_rendering()

        ti = self.maybeTextInput

        bodyclass = theme_manager.body_classes_for_card_ord(c.ord)

        if self.pform.preview_front.isChecked():
            q = ti(self.mw.prepare_card_text_for_display(c.q()))
            q = gui_hooks.card_will_show(q, c, "clayoutQuestion")
            text = q
            audio = c.question_av_tags()
        else:
            a = ti(self.mw.prepare_card_text_for_display(c.a()), type="a")
            a = gui_hooks.card_will_show(a, c, "clayoutAnswer")
            text = a
            audio = c.answer_av_tags()

        # use _showAnswer to avoid the longer delay
        self.pform.frontWeb.eval(
            "_showAnswer(%s,'%s');" % (json.dumps(text), bodyclass)
        )

        if c.id not in self.playedAudio:
            av_player.play_tags(audio)
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

        repl: Union[str, Callable]

        if type == "q":
            repl = "<input id='typeans' type=text value='exomple' readonly='readonly'>"
            repl = "<center>%s</center>" % repl
        else:
            repl = answerRepl
        return re.sub(r"\[\[type:.+?\]\]", repl, txt)

    # Card operations
    ######################################################################

    def onRemove(self):
        if len(self.templates) < 2:
            return showInfo(_("At least one card type is required."))
        template = self.current_template()
        msg = _("Delete the '%(a)s' card type, and its %(b)s?") % dict(
            a=template["name"], b=_("cards")
        )
        if not askUser(msg):
            return

        self.changed = True
        self.mm.remTemplate(self.model, template, save=False)

        # ensure current ordinal is within bounds
        idx = self.ord
        if idx >= len(self.templates):
            self.ord = len(self.templates) - 1

        self.redraw_everything()

    def onRename(self):
        template = self.current_template()
        name = getOnlyText(_("New name:"), default=template["name"])
        if not name.strip():
            return

        self.changed = True
        template["name"] = name
        self.redraw_everything()

    def onReorder(self):
        n = len(self.templates)
        template = self.current_template()
        current_pos = self.templates.index(template) + 1
        pos = getOnlyText(
            _("Enter new card position (1...%s):") % n, default=str(current_pos)
        )
        if not pos:
            return
        try:
            pos = int(pos)
        except ValueError:
            return
        if pos < 1 or pos > n:
            return
        if pos == current_pos:
            return
        new_idx = pos - 1
        self.changed = True
        self.mm.moveTemplate(self.model, template, new_idx, save=False)
        self.ord = new_idx
        self.redraw_everything()

    def _newCardName(self):
        n = len(self.templates) + 1
        while 1:
            name = _("Card %d") % n
            if name not in [t["name"] for t in self.templates]:
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
        self.changed = True
        name = self._newCardName()
        t = self.mm.newTemplate(name)
        old = self.current_template()
        t["qfmt"] = old["qfmt"]
        t["afmt"] = old["afmt"]
        self.mm.addTemplate(self.model, t, save=False)
        self.ord = len(self.templates) - 1
        self.redraw_everything()

    def onFlip(self):
        old = self.current_template()
        self._flipQA(old, old)
        self.redraw_everything()

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
        self.changed = True
        dst["afmt"] = "{{FrontSide}}\n\n<hr id=answer>\n\n%s" % src["qfmt"]
        dst["qfmt"] = m.group(2).strip()
        return True

    def onMore(self):
        m = QMenu(self)

        if not self._isCloze():
            a = m.addAction(_("Add Card Type..."))
            qconnect(a.triggered, self.onAddCard)

            a = m.addAction(_("Remove Card Type..."))
            qconnect(a.triggered, self.onRemove)

            a = m.addAction(_("Rename Card Type..."))
            qconnect(a.triggered, self.onRename)

            a = m.addAction(_("Reposition Card Type..."))
            qconnect(a.triggered, self.onReorder)

            m.addSeparator()

            t = self.current_template()
            if t["did"]:
                s = _(" (on)")
            else:
                s = _(" (off)")
            a = m.addAction(_("Deck Override...") + s)
            qconnect(a.triggered, self.onTargetDeck)

        a = m.addAction(_("Browser Appearance..."))
        qconnect(a.triggered, self.onBrowserDisplay)

        m.exec_(self.topAreaForm.templateOptions.mapToGlobal(QPoint(0, 0)))

    def onBrowserDisplay(self):
        d = QDialog()
        f = aqt.forms.browserdisp.Ui_Dialog()
        f.setupUi(d)
        t = self.current_template()
        f.qfmt.setText(t.get("bqfmt", ""))
        f.afmt.setText(t.get("bafmt", ""))
        if t.get("bfont"):
            f.overrideFont.setChecked(True)
        f.font.setCurrentFont(QFont(t.get("bfont", "Arial")))
        f.fontSize.setValue(t.get("bsize", 12))
        qconnect(f.buttonBox.accepted, lambda: self.onBrowserDisplayOk(f))
        d.exec_()

    def onBrowserDisplayOk(self, f):
        t = self.current_template()
        self.changed = True
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

        t = self.current_template()
        d = QDialog(self)
        d.setWindowTitle("Anki")
        d.setMinimumWidth(400)
        l = QVBoxLayout()
        lab = QLabel(
            _(
                """\
Enter deck to place new %s cards in, or leave blank:"""
            )
            % self.current_template()["name"]
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
        qconnect(bb.rejected, d.close)
        l.addWidget(bb)
        d.setLayout(l)
        d.exec_()
        self.changed = True
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
        form.fields.setCurrentRow(0)
        form.font.setCurrentFont(QFont("Arial"))
        form.size.setValue(20)
        if not diag.exec_():
            return
        row = form.fields.currentIndex().row()
        if row >= 0:
            self._addField(
                fields[row],
                form.font.currentFont().family(),
                form.size.value(),
            )

    def _addField(self, field, font, size):
        text = self.tform.edit_area.toPlainText()
        text += "\n<div style='font-family: %s; font-size: %spx;'>{{%s}}</div>\n" % (
            font,
            size,
            field,
        )
        self.tform.edit_area.setPlainText(text)
        self.changed = True
        self.write_edits_to_template_and_redraw()

    # Closing & Help
    ######################################################################

    def accept(self) -> None:
        def save():
            self.mm.save(self.model)

        def on_done(fut):
            try:
                fut.result()
            except TemplateError as e:
                showWarning("Unable to save changes: " + str(e))
                return
            self.mw.reset()
            tooltip("Changes saved.", parent=self.mw)
            self.cleanup()
            return QDialog.accept(self)

        self.mw.taskman.with_progress(save, on_done)

    def reject(self) -> None:
        if self.changed:
            if not askUser("Discard changes?"):
                return
        self.cleanup()
        return QDialog.reject(self)

    def cleanup(self) -> None:
        self.cancelPreviewTimer()
        av_player.stop_and_clear_queue()
        saveGeom(self, "CardLayout")
        self.pform.frontWeb = None
        self.model = None
        self.rendered_card = None
        self.mw = None

    def onHelp(self):
        openHelp("templates")
