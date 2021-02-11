# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# mypy: check-untyped-defs
import json
import re
import time
from typing import Any, Callable, Optional, Tuple, Union

from anki.cards import Card
from anki.collection import Config
from aqt import AnkiQt, gui_hooks
from aqt.qt import (
    QAbstractItemView,
    QCheckBox,
    QDialog,
    QDialogButtonBox,
    QIcon,
    QKeySequence,
    QPixmap,
    QShortcut,
    Qt,
    QTimer,
    QVBoxLayout,
    QWidget,
    qconnect,
)
from aqt.reviewer import replay_audio
from aqt.sound import av_player, play_clicked_audio
from aqt.theme import theme_manager
from aqt.utils import TR, disable_help_button, restoreGeom, saveGeom, tr
from aqt.webview import AnkiWebView

LastStateAndMod = Tuple[str, int, int]


class Previewer(QDialog):
    _last_state: Optional[LastStateAndMod] = None
    _card_changed = False
    _last_render: Union[int, float] = 0
    _timer: Optional[QTimer] = None
    _show_both_sides = False

    def __init__(
        self, parent: QWidget, mw: AnkiQt, on_close: Callable[[], None]
    ) -> None:
        super().__init__(None, Qt.Window)
        self._open = True
        self._parent = parent
        self._close_callback = on_close
        self.mw = mw
        icon = QIcon()
        icon.addPixmap(QPixmap(":/icons/anki.png"), QIcon.Normal, QIcon.Off)
        disable_help_button(self)
        self.setWindowIcon(icon)

    def card(self) -> Optional[Card]:
        raise NotImplementedError

    def card_changed(self) -> bool:
        raise NotImplementedError

    def open(self) -> None:
        self._state = "question"
        self._last_state = None
        self._create_gui()
        self._setup_web_view()
        self.render_card()
        self.show()

    def _create_gui(self) -> None:
        self.setWindowTitle(tr(TR.ACTIONS_PREVIEW))

        self.close_shortcut = QShortcut(QKeySequence("Ctrl+Shift+P"), self)
        qconnect(self.close_shortcut.activated, self.close)

        qconnect(self.finished, self._on_finished)
        self.silentlyClose = True
        self.vbox = QVBoxLayout()
        self.vbox.setContentsMargins(0, 0, 0, 0)
        self._web = AnkiWebView(title="previewer")
        self.vbox.addWidget(self._web)
        self.bbox = QDialogButtonBox()

        self._replay = self.bbox.addButton(
            tr(TR.ACTIONS_REPLAY_AUDIO), QDialogButtonBox.ActionRole
        )
        self._replay.setAutoDefault(False)
        self._replay.setShortcut(QKeySequence("R"))
        self._replay.setToolTip(tr(TR.ACTIONS_SHORTCUT_KEY, val="R"))
        qconnect(self._replay.clicked, self._on_replay_audio)

        both_sides_button = QCheckBox(tr(TR.QT_MISC_BACK_SIDE_ONLY))
        both_sides_button.setShortcut(QKeySequence("B"))
        both_sides_button.setToolTip(tr(TR.ACTIONS_SHORTCUT_KEY, val="B"))
        self.bbox.addButton(both_sides_button, QDialogButtonBox.ActionRole)
        self._show_both_sides = self.mw.col.get_config_bool(
            Config.Bool.PREVIEW_BOTH_SIDES
        )
        both_sides_button.setChecked(self._show_both_sides)
        qconnect(both_sides_button.toggled, self._on_show_both_sides)

        self.vbox.addWidget(self.bbox)
        self.setLayout(self.vbox)
        restoreGeom(self, "preview")

    def _on_finished(self, ok: int) -> None:
        saveGeom(self, "preview")
        self.mw.progress.timer(100, self._on_close, False)

    def _on_replay_audio(self) -> None:
        if self._state == "question":
            replay_audio(self.card(), True)
        elif self._state == "answer":
            replay_audio(self.card(), False)

    def close(self) -> None:
        self._on_close()
        super().close()

    def _on_close(self) -> None:
        self._open = False
        self._close_callback()

    def _setup_web_view(self) -> None:
        jsinc = [
            "js/vendor/jquery.min.js",
            "js/vendor/css_browser_selector.min.js",
            "js/mathjax.js",
            "js/vendor/mathjax/tex-chtml.js",
            "js/reviewer.js",
        ]
        self._web.stdHtml(
            self.mw.reviewer.revHtml(),
            css=["css/reviewer.css"],
            js=jsinc,
            context=self,
        )
        self._web.set_bridge_command(self._on_bridge_cmd, self)

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("play:"):
            play_clicked_audio(cmd, self.card())

    def render_card(self) -> None:
        self.cancel_timer()
        # Keep track of whether render() has ever been called
        # with cardChanged=True since the last successful render
        self._card_changed |= self.card_changed()
        # avoid rendering in quick succession
        elap_ms = int((time.time() - self._last_render) * 1000)
        delay = 300
        if elap_ms < delay:
            self._timer = self.mw.progress.timer(
                delay - elap_ms, self._render_scheduled, False
            )
        else:
            self._render_scheduled()

    def cancel_timer(self) -> None:
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
            txt = tr(TR.QT_MISC_PLEASE_SELECT_1_CARD)
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
                AnkiWebView.setPlaybackRequiresGesture(False)
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
                AnkiWebView.setPlaybackRequiresGesture(True)
                av_player.clear_queue_and_maybe_interrupt()

            txt = self.mw.prepare_card_text_for_display(txt)
            txt = gui_hooks.card_will_show(txt, c, f"preview{self._state.capitalize()}")
            self._last_state = self._state_and_mod()
        self._web.eval(f"{func}({json.dumps(txt)},'{bodyclass}');")
        self._card_changed = False

    def _on_show_both_sides(self, toggle: bool) -> None:
        self._show_both_sides = toggle
        self.mw.col.set_config_bool(Config.Bool.PREVIEW_BOTH_SIDES, toggle)
        self.mw.col.setMod()
        if self._state == "answer" and not toggle:
            self._state = "question"
        self.render_card()

    def _state_and_mod(self) -> Tuple[str, int, int]:
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

    def card_changed(self) -> bool:
        # need to state explicitly it's not implement to avoid W0223
        raise NotImplementedError

    def _create_gui(self) -> None:
        super()._create_gui()
        self._prev = self.bbox.addButton("<", QDialogButtonBox.ActionRole)
        self._prev.setAutoDefault(False)
        self._prev.setShortcut(QKeySequence("Left"))
        self._prev.setToolTip(tr(TR.QT_MISC_SHORTCUT_KEY_LEFT_ARROW))

        self._next = self.bbox.addButton(">", QDialogButtonBox.ActionRole)
        self._next.setAutoDefault(True)
        self._next.setShortcut(QKeySequence("Right"))
        self._next.setToolTip(tr(TR.QT_MISC_SHORTCUT_KEY_RIGHT_ARROW_OR_ENTER))

        qconnect(self._prev.clicked, self._on_prev)
        qconnect(self._next.clicked, self._on_next)

    def _on_prev(self) -> None:
        if self._state == "answer" and not self._show_both_sides:
            self._state = "question"
            self.render_card()
        else:
            self._on_prev_card()

    def _on_prev_card(self) -> None:
        pass

    def _on_next(self) -> None:
        if self._state == "question":
            self._state = "answer"
            self.render_card()
        else:
            self._on_next_card()

    def _on_next_card(self) -> None:
        pass

    def _updateButtons(self) -> None:
        if not self._open:
            return
        self._prev.setEnabled(self._should_enable_prev())
        self._next.setEnabled(self._should_enable_next())

    def _should_enable_prev(self) -> bool:
        return self._state == "answer" and not self._show_both_sides

    def _should_enable_next(self) -> bool:
        return self._state == "question"

    def _on_close(self) -> None:
        super()._on_close()
        self._prev = None
        self._next = None


class BrowserPreviewer(MultiCardPreviewer):
    _last_card_id = 0

    def card(self) -> Optional[Card]:
        if self._parent.singleCard:
            return self._parent.card
        else:
            return None

    def card_changed(self) -> bool:
        c = self.card()
        if not c:
            return True
        else:
            changed = c.id != self._last_card_id
            self._last_card_id = c.id
            return changed

    def _on_prev_card(self) -> None:
        self._parent.editor.saveNow(
            lambda: self._parent._moveCur(QAbstractItemView.MoveUp)
        )

    def _on_next_card(self) -> None:
        self._parent.editor.saveNow(
            lambda: self._parent._moveCur(QAbstractItemView.MoveDown)
        )

    def _should_enable_prev(self) -> bool:
        return super()._should_enable_prev() or self._parent.currentRow() > 0

    def _should_enable_next(self) -> bool:
        return (
            super()._should_enable_next()
            or self._parent.currentRow() < self._parent.model.rowCount(None) - 1
        )

    def _render_scheduled(self) -> None:
        super()._render_scheduled()
        self._updateButtons()
