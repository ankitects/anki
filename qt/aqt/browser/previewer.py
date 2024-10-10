# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import json
import re
import time
from collections.abc import Callable
from typing import Any

import aqt.browser
from anki.cards import Card
from anki.collection import Config
from anki.tags import MARKED_TAG
from aqt import AnkiQt, gui_hooks
from aqt.qt import (
    QCheckBox,
    QDialog,
    QDialogButtonBox,
    QKeySequence,
    QShortcut,
    Qt,
    QTimer,
    QVBoxLayout,
    qconnect,
)
from aqt.reviewer import replay_audio
from aqt.sound import av_player, play_clicked_audio
from aqt.theme import theme_manager
from aqt.utils import disable_help_button, restoreGeom, saveGeom, setWindowIcon, tr
from aqt.webview import AnkiWebView, AnkiWebViewKind

LastStateAndMod = tuple[str, int, int]


class Previewer(QDialog):
    _last_state: LastStateAndMod | None = None
    _card_changed = False
    _last_render: int | float = 0
    _timer: QTimer | None = None
    _show_both_sides = False

    def __init__(
        self,
        parent: aqt.browser.Browser | None,
        mw: AnkiQt,
        on_close: Callable[[], None],
    ) -> None:
        super().__init__(None, Qt.WindowType.Window)
        mw.garbage_collect_on_dialog_finish(self)
        self._open = True
        self._parent = parent
        self._close_callback = on_close
        self.mw = mw
        disable_help_button(self)
        setWindowIcon(self)
        gui_hooks.previewer_did_init(self)

    def card(self) -> Card | None:
        raise NotImplementedError

    def card_changed(self) -> bool:
        raise NotImplementedError

    def open(self) -> None:
        self._state = "question"
        self._last_state = None
        self._create_gui()
        self._setup_web_view()
        self.render_card()
        restoreGeom(self, "preview")
        self.show()

    def _create_gui(self) -> None:
        self.setWindowTitle(tr.actions_preview())

        self.close_shortcut = QShortcut(QKeySequence("Ctrl+Shift+P"), self)
        qconnect(self.close_shortcut.activated, self.close)

        qconnect(self.finished, self._on_finished)
        self.silentlyClose = True
        self.vbox = QVBoxLayout()
        self.vbox.setContentsMargins(0, 0, 0, 0)
        self._web: AnkiWebView | None = AnkiWebView(kind=AnkiWebViewKind.PREVIEWER)
        self.vbox.addWidget(self._web)
        self.bbox = QDialogButtonBox()
        self.bbox.setLayoutDirection(Qt.LayoutDirection.LeftToRight)

        gui_hooks.card_review_webview_did_init(self._web, AnkiWebViewKind.PREVIEWER)

        self._replay = self.bbox.addButton(
            tr.actions_replay_audio(), QDialogButtonBox.ButtonRole.ActionRole
        )
        assert self._replay is not None
        self._replay.setAutoDefault(False)
        self._replay.setShortcut(QKeySequence("R"))
        self._replay.setToolTip(tr.actions_shortcut_key(val="R"))
        qconnect(self._replay.clicked, self._on_replay_audio)

        both_sides_button = QCheckBox(tr.qt_misc_back_side_only())
        both_sides_button.setShortcut(QKeySequence("B"))
        both_sides_button.setToolTip(tr.actions_shortcut_key(val="B"))
        self.bbox.addButton(both_sides_button, QDialogButtonBox.ButtonRole.ActionRole)
        self._show_both_sides = self.mw.col.get_config_bool(
            Config.Bool.PREVIEW_BOTH_SIDES
        )
        both_sides_button.setChecked(self._show_both_sides)
        qconnect(both_sides_button.toggled, self._on_show_both_sides)

        self.vbox.addWidget(self.bbox)
        self.setLayout(self.vbox)

    def _on_finished(self, ok: int) -> None:
        saveGeom(self, "preview")
        self._on_close()

    def _on_replay_audio(self) -> None:
        assert self._web is not None
        card = self.card()
        assert card is not None

        gui_hooks.audio_will_replay(self._web, card, self._state == "question")

        if self._state == "question":
            replay_audio(card, True)
        elif self._state == "answer":
            replay_audio(card, False)

    def _on_close(self) -> None:
        self._open = False
        self._close_callback()

        assert self._web is not None

        self._web.cleanup()
        self._web = None

    def _setup_web_view(self) -> None:
        assert self._web is not None

        self._web.stdHtml(
            self.mw.reviewer.revHtml(),
            css=["css/reviewer.css"],
            js=[
                "js/mathjax.js",
                "js/vendor/mathjax/tex-chtml-full.js",
                "js/reviewer.js",
            ],
            context=self,
        )
        self._web.allow_drops = True
        self._web.eval("_blockDefaultDragDropBehavior();")
        self._web.set_bridge_command(self._on_bridge_cmd, self)

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("play:"):
            card = self.card()
            assert card is not None

            play_clicked_audio(cmd, card)

    def _update_flag_and_mark_icons(self, card: Card | None) -> None:
        if card:
            flag = card.user_flag()
            marked = card.note(reload=True).has_tag(MARKED_TAG)
        else:
            flag = 0
            marked = False

        assert self._web is not None

        self._web.eval(f"_drawFlag({flag}); _drawMark({json.dumps(marked)});")

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
                delay - elap_ms, self._render_scheduled, False, parent=self
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
        self._update_flag_and_mark_icons(c)
        func = "_showQuestion"
        ans_txt = ""
        if not c:
            txt = tr.qt_misc_please_select_1_card()
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
            txt = c.question(reload=True)
            ans_txt = c.answer()

            if self._state == "answer":
                func = "_showAnswer"
                txt = ans_txt
            txt = re.sub(r"\[\[type:[^]]+\]\]", "", txt)

            bodyclass = theme_manager.body_classes_for_card_ord(c.ord)

            assert self._web is not None

            if c.autoplay():
                self._web.setPlaybackRequiresGesture(False)
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
            else:
                audio = []
                self._web.setPlaybackRequiresGesture(True)
            gui_hooks.av_player_will_play_tags(audio, self._state, self)
            av_player.play_tags(audio)
            txt = self.mw.prepare_card_text_for_display(txt)
            txt = gui_hooks.card_will_show(txt, c, f"preview{self._state.capitalize()}")
            self._last_state = self._state_and_mod()

        js: str
        if self._state == "question":
            ans_txt = self.mw.col.media.escape_media_filenames(ans_txt)
            js = f"{func}({json.dumps(txt)}, {json.dumps(ans_txt)}, '{bodyclass}');"
        else:
            js = f"{func}({json.dumps(txt)}, '{bodyclass}');"

        assert self._web is not None
        self._web.eval(js)
        self._card_changed = False

    def _on_show_both_sides(self, toggle: bool) -> None:
        assert self._web is not None

        self._show_both_sides = toggle
        self.mw.col.set_config_bool(Config.Bool.PREVIEW_BOTH_SIDES, toggle)

        card = self.card()
        assert card is not None

        gui_hooks.previewer_will_redraw_after_show_both_sides_toggled(
            self._web, card, self._state == "question", toggle
        )

        if self._state == "answer" and not toggle:
            self._state = "question"
        self.render_card()

    def _state_and_mod(self) -> tuple[str, int, int]:
        c = self.card()

        assert c is not None

        n = c.note()
        n.load()
        return (self._state, c.id, n.mod)

    def state(self) -> str:
        return self._state


class MultiCardPreviewer(Previewer):
    def card(self) -> Card | None:
        # need to state explicitly it's not implement to avoid W0223
        raise NotImplementedError

    def card_changed(self) -> bool:
        # need to state explicitly it's not implement to avoid W0223
        raise NotImplementedError

    def _create_gui(self) -> None:
        super()._create_gui()
        self._prev = self.bbox.addButton(
            ">" if self.layoutDirection() == Qt.LayoutDirection.RightToLeft else "<",
            QDialogButtonBox.ButtonRole.ActionRole,
        )

        assert self._prev is not None

        self._prev.setAutoDefault(False)
        self._prev.setShortcut(QKeySequence("Left"))
        self._prev.setToolTip(tr.qt_misc_shortcut_key_left_arrow())

        self._next = self.bbox.addButton(
            "<" if self.layoutDirection() == Qt.LayoutDirection.RightToLeft else ">",
            QDialogButtonBox.ButtonRole.ActionRole,
        )

        assert self._next is not None

        self._next.setAutoDefault(True)
        self._next.setShortcut(QKeySequence("Right"))
        self._next.setToolTip(tr.qt_misc_shortcut_key_right_arrow_or_enter())

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

        assert self._prev is not None
        assert self._next is not None

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
    _parent: aqt.browser.Browser | None

    def __init__(
        self, parent: aqt.browser.Browser, mw: AnkiQt, on_close: Callable[[], None]
    ) -> None:
        super().__init__(parent=parent, mw=mw, on_close=on_close)

    def card(self) -> Card | None:
        assert self._parent is not None

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
        assert self._parent is not None

        self._parent.onPreviousCard()

    def _on_next_card(self) -> None:
        assert self._parent is not None

        self._parent.onNextCard()

    def _should_enable_prev(self) -> bool:
        assert self._parent is not None

        return super()._should_enable_prev() or self._parent.has_previous_card()

    def _should_enable_next(self) -> bool:
        assert self._parent is not None

        return super()._should_enable_next() or self._parent.has_next_card()

    def _render_scheduled(self) -> None:
        super()._render_scheduled()
        self._updateButtons()
