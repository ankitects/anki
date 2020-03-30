import json
import re
import time
from dataclasses import dataclass
from typing import Any, Optional, Union

from anki.cards import Card
from anki.lang import _
from aqt import AnkiQt, gui_hooks
from aqt.qt import (
    QAbstractItemView,
    QCheckBox,
    QDialog,
    QDialogButtonBox,
    QKeySequence,
    Qt,
    QVBoxLayout,
    QWidget,
)
from aqt.sound import av_player, play_clicked_audio
from aqt.theme import theme_manager
from aqt.utils import restoreGeom, saveGeom
from aqt.webview import AnkiWebView


@dataclass
class PreviewDialog:
    dialog: QDialog
    parent: QWidget


class Previewer:
    _lastPreviewState = None
    _previewCardChanged = False
    _lastPreviewRender: Union[int, float] = 0
    _previewTimer = None

    def __init__(self, parent: QWidget, mw: AnkiQt):
        self.parent = parent
        self.mw = mw

    def card(self) -> Optional[Card]:
        return self.parent.card

    def _openPreview(self):
        self._previewState = "question"
        self._lastPreviewState = None
        self._create_gui()
        self._setupPreviewWebview()
        self._renderPreview(True)
        self._previewWindow.show()

    def _create_gui(self):
        self._previewWindow = QDialog(None, Qt.Window)
        self._previewWindow.setWindowTitle(_("Preview"))

        self._previewWindow.finished.connect(self._onPreviewFinished)
        self._previewWindow.silentlyClose = True
        self.vbox = QVBoxLayout()
        self.vbox.setContentsMargins(0, 0, 0, 0)
        self._previewWeb = AnkiWebView(title="previewer")
        self.vbox.addWidget(self._previewWeb)
        self.bbox = QDialogButtonBox()

        self._previewReplay = self.bbox.addButton(
            _("Replay Audio"), QDialogButtonBox.ActionRole
        )
        self._previewReplay.setAutoDefault(False)
        self._previewReplay.setShortcut(QKeySequence("R"))
        self._previewReplay.setToolTip(_("Shortcut key: %s" % "R"))

        self._previewPrev = self.bbox.addButton("<", QDialogButtonBox.ActionRole)
        self._previewPrev.setAutoDefault(False)
        self._previewPrev.setShortcut(QKeySequence("Left"))
        self._previewPrev.setToolTip(_("Shortcut key: Left arrow"))

        self._previewNext = self.bbox.addButton(">", QDialogButtonBox.ActionRole)
        self._previewNext.setAutoDefault(True)
        self._previewNext.setShortcut(QKeySequence("Right"))
        self._previewNext.setToolTip(_("Shortcut key: Right arrow or Enter"))

        self._previewPrev.clicked.connect(self._onPreviewPrev)
        self._previewNext.clicked.connect(self._onPreviewNext)
        self._previewReplay.clicked.connect(self._onReplayAudio)

        self.previewShowBothSides = QCheckBox(_("Show Both Sides"))
        self.previewShowBothSides.setShortcut(QKeySequence("B"))
        self.previewShowBothSides.setToolTip(_("Shortcut key: %s" % "B"))
        self.bbox.addButton(self.previewShowBothSides, QDialogButtonBox.ActionRole)
        self._previewBothSides = self.mw.col.conf.get("previewBothSides", False)
        self.previewShowBothSides.setChecked(self._previewBothSides)
        self.previewShowBothSides.toggled.connect(self._onPreviewShowBothSides)

        self.vbox.addWidget(self.bbox)
        self._previewWindow.setLayout(self.vbox)
        restoreGeom(self._previewWindow, "preview")

    def _onPreviewFinished(self, ok):
        saveGeom(self._previewWindow, "preview")
        self.mw.progress.timer(100, self._onClosePreview, False)
        self.parent.form.previewButton.setChecked(False)

    def _onPreviewPrev(self):
        if self._previewState == "answer" and not self._previewBothSides:
            self._previewState = "question"
            self._renderPreview()
        else:
            self.parent.editor.saveNow(
                lambda: self.parent._moveCur(QAbstractItemView.MoveUp)
            )

    def _onPreviewNext(self):
        if self._previewState == "question":
            self._previewState = "answer"
            self._renderPreview()
        else:
            self.parent.editor.saveNow(
                lambda: self.parent._moveCur(QAbstractItemView.MoveDown)
            )

    def _onReplayAudio(self):
        self.mw.reviewer.replayAudio(self)

    def _updatePreviewButtons(self):
        if not self._previewWindow:
            return
        current = self.parent.currentRow()
        canBack = current > 0 or (
            current == 0
            and self._previewState == "answer"
            and not self._previewBothSides
        )
        self._previewPrev.setEnabled(bool(self.parent.singleCard and canBack))
        canForward = (
            self.parent.currentRow() < self.parent.model.rowCount(None) - 1
            or self._previewState == "question"
        )
        self._previewNext.setEnabled(bool(self.parent.singleCard and canForward))

    def _closePreview(self):
        if self._previewWindow:
            self._previewWindow.close()
            self._onClosePreview()

    def _onClosePreview(self):
        self.parent.previewer = None
        self._previewWindow = None
        self._previewPrev = None
        self._previewNext = None

    def _setupPreviewWebview(self):
        jsinc = [
            "jquery.js",
            "browsersel.js",
            "mathjax/conf.js",
            "mathjax/MathJax.js",
            "reviewer.js",
        ]
        web_context = PreviewDialog(dialog=self._previewWindow, parent=self.parent)
        self._previewWeb.stdHtml(
            self.mw.reviewer.revHtml(),
            css=["reviewer.css"],
            js=jsinc,
            context=web_context,
        )
        self._previewWeb.set_bridge_command(
            self._on_preview_bridge_cmd, web_context,
        )

    def _on_preview_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("play:"):
            play_clicked_audio(cmd, self.card())

    def _renderPreview(self, cardChanged=False):
        self._cancelPreviewTimer()
        # Keep track of whether _renderPreview() has ever been called
        # with cardChanged=True since the last successful render
        self._previewCardChanged |= cardChanged
        # avoid rendering in quick succession
        elapMS = int((time.time() - self._lastPreviewRender) * 1000)
        delay = 300
        if elapMS < delay:
            self._previewTimer = self.mw.progress.timer(
                delay - elapMS, self._renderScheduledPreview, False
            )
        else:
            self._renderScheduledPreview()

    def _cancelPreviewTimer(self):
        if self._previewTimer:
            self._previewTimer.stop()
            self._previewTimer = None

    def _renderScheduledPreview(self) -> None:
        self._cancelPreviewTimer()
        self._lastPreviewRender = time.time()

        if not self._previewWindow:
            return
        c = self.card()
        func = "_showQuestion"
        if not c or not self.parent.singleCard:
            txt = _("(please select 1 card)")
            bodyclass = ""
            self._lastPreviewState = None
        else:
            if self._previewBothSides:
                self._previewState = "answer"
            elif self._previewCardChanged:
                self._previewState = "question"

            currentState = self._previewStateAndMod()
            if currentState == self._lastPreviewState:
                # nothing has changed, avoid refreshing
                return

            # need to force reload even if answer
            txt = c.q(reload=True)

            if self._previewState == "answer":
                func = "_showAnswer"
                txt = c.a()
            txt = re.sub(r"\[\[type:[^]]+\]\]", "", txt)

            bodyclass = theme_manager.body_classes_for_card_ord(c.ord)

            if self.mw.reviewer.autoplay(c):
                if self._previewBothSides:
                    # if we're showing both sides at once, remove any audio
                    # from the answer that's appeared on the question already
                    question_audio = c.question_av_tags()
                    only_on_answer_audio = [
                        x for x in c.answer_av_tags() if x not in question_audio
                    ]
                    audio = question_audio + only_on_answer_audio
                elif self._previewState == "question":
                    audio = c.question_av_tags()
                else:
                    audio = c.answer_av_tags()
                av_player.play_tags(audio)
            else:
                av_player.clear_queue_and_maybe_interrupt()

            txt = self.mw.prepare_card_text_for_display(txt)
            txt = gui_hooks.card_will_show(
                txt, c, "preview" + self._previewState.capitalize()
            )
            self._lastPreviewState = self._previewStateAndMod()
        self._updatePreviewButtons()
        self._previewWeb.eval("{}({},'{}');".format(func, json.dumps(txt), bodyclass))
        self._previewCardChanged = False

    def _onPreviewShowBothSides(self, toggle):
        self._previewBothSides = toggle
        self.mw.col.conf["previewBothSides"] = toggle
        self.mw.col.setMod()
        if self._previewState == "answer" and not toggle:
            self._previewState = "question"
        self._renderPreview()

    def _previewStateAndMod(self):
        c = self.card()
        n = c.note()
        n.load()
        return (self._previewState, c.id, n.mod)
