import json
import re
import time
from typing import Any, List, Optional, Union

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


class Previewer:
    _lastState = None
    _cardChanged = False
    _lastRender: Union[int, float] = 0
    _timer = None

    def __init__(self, parent: QWidget, mw: AnkiQt):
        self._parent = parent
        self.mw = mw

    def card(self) -> Optional[Card]:
        raise NotImplementedError

    def open(self):
        self._state = "question"
        self._lastState = None
        self._create_gui()
        self._setupWebview()
        self.render(True)
        self._window.show()

    def _create_gui(self):
        self._window = QDialog(None, Qt.Window)
        self._window.setWindowTitle(_("Preview"))

        self._window.finished.connect(self._onFinished)
        self._window.silentlyClose = True
        self.vbox = QVBoxLayout()
        self.vbox.setContentsMargins(0, 0, 0, 0)
        self._web = AnkiWebView(title="previewer")
        self.vbox.addWidget(self._web)
        self.bbox = QDialogButtonBox()

        self._replay = self.bbox.addButton(
            _("Replay Audio"), QDialogButtonBox.ActionRole
        )
        self._replay.setAutoDefault(False)
        self._replay.setShortcut(QKeySequence("R"))
        self._replay.setToolTip(_("Shortcut key: %s" % "R"))
        self._replay.clicked.connect(self._onReplayAudio)

        self.showBothSides = QCheckBox(_("Show Both Sides"))
        self.showBothSides.setShortcut(QKeySequence("B"))
        self.showBothSides.setToolTip(_("Shortcut key: %s" % "B"))
        self.bbox.addButton(self.showBothSides, QDialogButtonBox.ActionRole)
        self._bothSides = self.mw.col.conf.get("previewBothSides", False)
        self.showBothSides.setChecked(self._bothSides)
        self.showBothSides.toggled.connect(self._onShowBothSides)

        self.vbox.addWidget(self.bbox)
        self._window.setLayout(self.vbox)
        restoreGeom(self._window, "preview")

    def _onFinished(self, ok):
        saveGeom(self._window, "preview")
        self.mw.progress.timer(100, self._onClose, False)

    def _onReplayAudio(self):
        self.mw.reviewer.replayAudio(self)

    def close(self):
        if self._window:
            self._window.close()
            self._onClose()

    def _onClose(self):
        self._window = None

    def _setupWebview(self):
        jsinc = [
            "jquery.js",
            "browsersel.js",
            "mathjax/conf.js",
            "mathjax/MathJax.js",
            "reviewer.js",
        ]
        self._previewWeb.stdHtml(
            self.mw.reviewer.revHtml(), css=["reviewer.css"], js=jsinc, context=self,
        )
        self._web.set_bridge_command(self._on_bridge_cmd, self)

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("play:"):
            play_clicked_audio(cmd, self.card())

    def render(self, cardChanged=False):
        self.cancelTimer()
        # Keep track of whether render() has ever been called
        # with cardChanged=True since the last successful render
        self._cardChanged |= cardChanged
        # avoid rendering in quick succession
        elapMS = int((time.time() - self._lastRender) * 1000)
        delay = 300
        if elapMS < delay:
            self._timer = self.mw.progress.timer(
                delay - elapMS, self._renderScheduled, False
            )
        else:
            self._renderScheduled()

    def cancelTimer(self):
        if self._timer:
            self._timer.stop()
            self._timer = None

    def _renderScheduled(self) -> None:
        self.cancelTimer()
        self._lastRender = time.time()

        if not self._window:
            return
        c = self.card()
        func = "_showQuestion"
        if not c:
            txt = _("(please select 1 card)")
            bodyclass = ""
            self._lastState = None
        else:
            if self._bothSides:
                self._state = "answer"
            elif self._cardChanged:
                self._state = "question"

            currentState = self._stateAndMod()
            if currentState == self._lastState:
                # nothing has changed, avoid refreshing
                return

            # need to force reload even if answer
            txt = c.q(reload=True)

            if self._state == "answer":
                func = "_showAnswer"
                txt = c.a()
            txt = re.sub(r"\[\[type:[^]]+\]\]", "", txt)

            bodyclass = theme_manager.body_classes_for_card_ord(c.ord)

            if self.mw.reviewer.autoplay(c):
                if self._bothSides:
                    # if we're showing both sides at once, remove any audio
                    # from the answer that's appeared on the question already
                    question_audio = c.question_av_tags()
                    only_on_answer_audio = [
                        x for x in c.answer_av_tags() if x not in question_audio
                    ]
                    audio = question_audio + only_on_answer_audio
                elif self._state == "question":
                    audio = c.question_av_tags()
                else:
                    audio = c.answer_av_tags()
                av_player.play_tags(audio)
            else:
                av_player.clear_queue_and_maybe_interrupt()

            txt = self.mw.prepare_card_text_for_display(txt)
            txt = gui_hooks.card_will_show(txt, c, "preview" + self._state.capitalize())
            self._lastState = self._stateAndMod()
        self._web.eval("{}({},'{}');".format(func, json.dumps(txt), bodyclass))
        self._cardChanged = False

    def _onShowBothSides(self, toggle):
        self._bothSides = toggle
        self.mw.col.conf["previewBothSides"] = toggle
        self.mw.col.setMod()
        if self._state == "answer" and not toggle:
            self._state = "question"
        self.render()

    def _stateAndMod(self):
        c = self.card()
        n = c.note()
        n.load()
        return (self._state, c.id, n.mod)


class MultipleCardsPreviewer(Previewer):
    def card(self) -> Optional[Card]:
        # need to state explicitly it's not implement to avoid W0223
        raise NotImplementedError

    def _create_gui(self):
        super()._create_gui()
        self._prev = self.bbox.addButton("<", QDialogButtonBox.ActionRole)
        self._prev.setAutoDefault(False)
        self._prev.setShortcut(QKeySequence("Left"))
        self._prev.setToolTip(_("Shortcut key: Left arrow"))

        self._next = self.bbox.addButton(">", QDialogButtonBox.ActionRole)
        self._next.setAutoDefault(True)
        self._next.setShortcut(QKeySequence("Right"))
        self._next.setToolTip(_("Shortcut key: Right arrow or Enter"))

        self._prev.clicked.connect(self._onPrev)
        self._next.clicked.connect(self._onNext)

    def _onPrev(self):
        if self._state == "answer" and not self._bothSides:
            self._state = "question"
            self.render()
        else:
            self._onPrevCard()

    def _onPrevCard(self):
        ...

    def _onNext(self):
        if self._state == "question":
            self._state = "answer"
            self.render()
        else:
            self._onNextCard()

    def _onNextCard(self):
        ...

    def _updateButtons(self):
        if not self._window:
            return
        self._prev.setEnabled(self._should_enable_prev())
        self._next.setEnabled(self._should_enable_next())

    def _should_enable_prev(self):
        return self._state == "answer" and not self._bothSides

    def _should_enable_next(self):
        return self._state == "question"

    def _onClose(self):
        super()._onClose()
        self._prev = None
        self._next = None


class BrowserPreviewer(MultipleCardsPreviewer):
    def card(self) -> Optional[Card]:
        if self._parent.singleCard:
            return self._parent.card
        else:
            return None

    def _onFinished(self, ok):
        super()._onFinished(ok)
        self._parent.form.previewButton.setChecked(False)

    def _onPrevCard(self):
        self._parent.editor.saveNow(
            lambda: self._parent._moveCur(QAbstractItemView.MoveUp)
        )

    def _onNextCard(self):
        self._parent.editor.saveNow(
            lambda: self._parent._moveCur(QAbstractItemView.MoveDown)
        )

    def _should_enable_prev(self):
        return super()._should_enable_prev() or self._parent.currentRow() > 0

    def _should_enable_next(self):
        return (
            super()._should_enable_next()
            or self._parent.currentRow() < self._parent.model.rowCount(None) - 1
        )

    def _onClose(self):
        super()._onClose()
        self._parent.previewer = None

    def _renderScheduled(self) -> None:
        super()._renderScheduled()
        self._updateButtons()


class ListCardsPreviewer(MultipleCardsPreviewer):
    def __init__(self, cards: List[Union[Card, int]], *args, **kwargs):
        """A previewer displaying a list of card.

        List can be changed by setting self.cards to a new value.

        self.cards contains both cid and card. So that card is loaded
        only when required and is not loaded twice.

        """
        self.index = 0
        self.cards = cards
        super().__init__(*args, **kwargs)

    def card(self):
        if not self.cards:
            return None
        if isinstance(self.cards[self.index], int):
            self.cards[self.index] = self.mw.col.getCard(self.cards[self.index])
        return self.cards[self.index]

    def open(self):
        if not self.cards:
            return
        super().open()

    def _onPrevCard(self):
        self.index -= 1
        self.render()

    def _onNextCard(self):
        self.index += 1
        self.render()

    def _should_enable_prev(self):
        return super()._should_enable_prev() or self.index > 0

    def _should_enable_next(self):
        return super()._should_enable_next() or self.index < len(self.cards) - 1

    def _on_other_side(self):
        if self._state == "question":
            self._state = "answer"
        else:
            self._state = "question"
        self.render()


class SingleCardPreviewer(Previewer):
    def __init__(self, card: Card, *args, **kwargs):
        self._card = card
        super().__init__(*args, **kwargs)

    def card(self) -> Card:
        return self._card

    def _create_gui(self):
        super()._create_gui()
        self._other_side = self.bbox.addButton(
            "Other side", QDialogButtonBox.ActionRole
        )
        self._other_side.setAutoDefault(False)
        self._other_side.setShortcut(QKeySequence("Right"))
        self._other_side.setShortcut(QKeySequence("Left"))
        self._other_side.setToolTip(_("Shortcut key: Left or Right arrow"))
        self._other_side.clicked.connect(self._on_other_side)

    def _on_other_side(self):
        if self._state == "question":
            self._state = "answer"
        else:
            self._state = "question"
        self.render()
