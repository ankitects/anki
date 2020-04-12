# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# mypy: check-untyped-defs

import json
import re
import time
from typing import Any, Callable, List, Optional, Union

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
    qconnect,
)
from aqt.reviewer import replay_audio
from aqt.sound import av_player, play_clicked_audio
from aqt.theme import theme_manager
from aqt.utils import restoreGeom, saveGeom
from aqt.webview import AnkiWebView


class Previewer(QDialog):
    _last_state = None
    _card_changed = False
    _last_render: Union[int, float] = 0
    _timer = None
    _show_both_sides = False

    def __init__(self, parent: QWidget, mw: AnkiQt, on_close: Callable[[], None]):
        super().__init__(None, Qt.Window)
        self._open = True
        self._parent = parent
        self._close_callback = on_close
        self.mw = mw

    def card(self) -> Optional[Card]:
        raise NotImplementedError

    def open(self):
        self._state = "question"
        self._last_state = None
        self._create_gui()
        self._setup_web_view()
        self.render_card(True)
        self.show()

    def _create_gui(self):
        self.setWindowTitle(_("Preview"))

        qconnect(self.finished, self._on_finished)
        self.silentlyClose = True
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
        qconnect(self._replay.clicked, self._on_replay_audio)

        both_sides_button = QCheckBox(_("Show Both Sides"))
        both_sides_button.setShortcut(QKeySequence("B"))
        both_sides_button.setToolTip(_("Shortcut key: %s" % "B"))
        self.bbox.addButton(both_sides_button, QDialogButtonBox.ActionRole)
        self._show_both_sides = self.mw.col.conf.get("previewBothSides", False)
        both_sides_button.setChecked(self._show_both_sides)
        qconnect(both_sides_button.toggled, self._on_show_both_sides)

        self.vbox.addWidget(self.bbox)
        self.setLayout(self.vbox)
        restoreGeom(self, "preview")

    def _on_finished(self, ok):
        saveGeom(self, "preview")
        self.mw.progress.timer(100, self._on_close, False)

    def _on_replay_audio(self):
        if self._state == "question":
            replay_audio(self.card(), True)
        elif self._state == "answer":
            replay_audio(self.card(), False)

    def close(self):
        self._on_close()
        super().close()
        self._close_callback()

    def _on_close(self):
        self._open = False

    def _setup_web_view(self):
        jsinc = [
            "jquery.js",
            "browsersel.js",
            "mathjax/conf.js",
            "mathjax/MathJax.js",
            "reviewer.js",
        ]
        self._web.stdHtml(
            self.mw.reviewer.revHtml(), css=["reviewer.css"], js=jsinc, context=self,
        )
        self._web.set_bridge_command(self._on_bridge_cmd, self)

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("play:"):
            play_clicked_audio(cmd, self.card())

    def render_card(self, cardChanged=False):
        self.cancel_timer()
        # Keep track of whether render() has ever been called
        # with cardChanged=True since the last successful render
        self._card_changed |= cardChanged
        # avoid rendering in quick succession
        elap_ms = int((time.time() - self._last_render) * 1000)
        delay = 300
        if elap_ms < delay:
            self._timer = self.mw.progress.timer(
                delay - elap_ms, self._render_scheduled, False
            )
        else:
            self._render_scheduled()

    def cancel_timer(self):
        if self._timer:
            self._timer.stop()
            self._timer = None

    def _render_scheduled(self) -> None:
        self.cancel_timer()
        self._last_render = time.time()

        if not self._open:
            return
        c = self.card()
        func = "_showQuestion"
        if not c:
            txt = _("(please select 1 card)")
            bodyclass = ""
            self._last_state = None
        else:
            if self._show_both_sides:
                self._state = "answer"
            elif self._card_changed:
                self._state = "question"

            currentState = self._state_and_mod()
            if currentState == self._last_state:
                # nothing has changed, avoid refreshing
                return

            # need to force reload even if answer
            txt = c.q(reload=True)

            if self._state == "answer":
                func = "_showAnswer"
                txt = c.a()
            txt = re.sub(r"\[\[type:[^]]+\]\]", "", txt)

            bodyclass = theme_manager.body_classes_for_card_ord(c.ord)

            if c.autoplay():
                if self._show_both_sides:
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
            self._last_state = self._state_and_mod()
        self._web.eval("{}({},'{}');".format(func, json.dumps(txt), bodyclass))
        self._card_changed = False

    def _on_show_both_sides(self, toggle):
        self._show_both_sides = toggle
        self.mw.col.conf["previewBothSides"] = toggle
        self.mw.col.setMod()
        if self._state == "answer" and not toggle:
            self._state = "question"
        self.render_card()

    def _state_and_mod(self):
        c = self.card()
        n = c.note()
        n.load()
        return (self._state, c.id, n.mod)

    def state(self) -> str:
        return self._state


class MultiCardPreviewer(Previewer):
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

        qconnect(self._prev.clicked, self._on_prev)
        qconnect(self._next.clicked, self._on_next)

    def _on_prev(self):
        if self._state == "answer" and not self._show_both_sides:
            self._state = "question"
            self.render_card()
        else:
            self._on_prev_card()

    def _on_prev_card(self):
        pass

    def _on_next(self):
        if self._state == "question":
            self._state = "answer"
            self.render_card()
        else:
            self._on_next_card()

    def _on_next_card(self):
        pass

    def _updateButtons(self):
        if not self._open:
            return
        self._prev.setEnabled(self._should_enable_prev())
        self._next.setEnabled(self._should_enable_next())

    def _should_enable_prev(self):
        return self._state == "answer" and not self._show_both_sides

    def _should_enable_next(self):
        return self._state == "question"

    def _on_close(self):
        super()._on_close()
        self._prev = None
        self._next = None


class BrowserPreviewer(MultiCardPreviewer):
    def card(self) -> Optional[Card]:
        if self._parent.singleCard:
            return self._parent.card
        else:
            return None

    def _on_finished(self, ok):
        super()._on_finished(ok)
        self._parent.form.previewButton.setChecked(False)

    def _on_prev_card(self):
        self._parent.editor.saveNow(
            lambda: self._parent._moveCur(QAbstractItemView.MoveUp)
        )

    def _on_next_card(self):
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

    def _on_close(self):
        super()._on_close()
        self._parent.previewer = None

    def _render_scheduled(self) -> None:
        super()._render_scheduled()
        self._updateButtons()


class CardListPreviewer(MultiCardPreviewer):
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
        entry = self.cards[self.index]
        if isinstance(entry, int):
            card = self.mw.col.getCard(entry)
            self.cards[self.index] = card
            return card
        else:
            return entry

    def open(self):
        if not self.cards:
            return
        super().open()

    def _on_prev_card(self):
        self.index -= 1
        self.render_card()

    def _on_next_card(self):
        self.index += 1
        self.render_card()

    def _should_enable_prev(self):
        return super()._should_enable_prev() or self.index > 0

    def _should_enable_next(self):
        return super()._should_enable_next() or self.index < len(self.cards) - 1

    def _on_other_side(self):
        if self._state == "question":
            self._state = "answer"
        else:
            self._state = "question"
        self.render_card()


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
        qconnect(self._other_side.clicked, self._on_other_side)

    def _on_other_side(self):
        if self._state == "question":
            self._state = "answer"
        else:
            self._state = "question"
        self.render_card()
