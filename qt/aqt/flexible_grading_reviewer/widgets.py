# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import aqt
from aqt import qconnect
from aqt.flexible_grading_reviewer.utils import clear_layout
from aqt.qt import *


class FlexiblePushButton(QPushButton):
    _height: int = 16
    _font_size: int = _height - 4

    def __init__(
        self,
        text="",
        text_color: str = "#111111",
        text_underline: bool = False,
        parent=None,
    ) -> None:
        super().__init__(text, parent)
        # Fixed height 16px, let width be flexible
        self.setFixedHeight(self._height)
        # Remove extra spacing from focus/contents margins
        self.setContentsMargins(0, 0, 0, 0)
        self.set_text_style(text_color, text_underline)
        # Optional: ensure compact size hint
        self.setSizePolicy(
            self.sizePolicy().horizontalPolicy(),
            self.sizePolicy().Policy.Fixed,
        )

    def set_text_style(
        self, text_color: str = "#111111", text_underline: bool = False
    ) -> None:
        stylesheet = (
            """
        FlexiblePushButton {
            border: none;
            background: transparent;
            color: TEXT_COLOR;
            margin: 0;
            padding: 0;
            font-size: FONT_SIZEpx;
            min-width: 0;
            qproperty-flat: true;
            font-family: "Noto Sans Mono", "Liberation Mono", "DejaVu Sans Mono", "Courier New", "Lucida Console", 
                         Courier, Consolas, "Noto Sans Mono CJK JP", monospace;
            TEXT_UNDERLINE
        }
        FlexiblePushButton:hover {
            background: #d0d0d0;
            color: #000;
        }
        FlexiblePushButton:pressed {
            background: #b8b8b8;
        }
        """.replace("FONT_SIZE", f"{self._font_size}")
            .replace("TEXT_COLOR", text_color)
            .replace(
                "TEXT_UNDERLINE",
                "text-decoration: underline;" if text_underline else "",
            )
        )
        self.setStyleSheet(stylesheet)

    def sizeHint(self) -> QSize:
        """
        Ensure sizeHint respects fixed height and minimal width
        """
        hint = super().sizeHint()
        return QSize(max(hint.width(), 0), self._height)


class FlexibleHorizontalBar(QWidget):
    """
    A simple bucket-like widget that holds other widgets and places them in a horizontal line.
    """

    _height: int = 16
    _spacing: int = 0

    mw: aqt.AnkiQt

    def __init__(self, mw: aqt.AnkiQt) -> None:
        super().__init__(mw)
        self.mw = mw
        # Setup Layout
        self._layout = QHBoxLayout()
        self.setLayout(self._layout)
        self._layout.setContentsMargins(0, 0, 0, 0)
        self._layout.setSpacing(self._spacing)
        self.setMaximumHeight(self._height)

    def add_stretch(self, stretch_value: int = 1) -> None:
        return self._layout.addStretch(stretch_value)

    def add_widget(self, widget: QWidget) -> QWidget:
        self._layout.addWidget(widget)
        return widget

    def add_button(self, button: QPushButton, *, on_clicked: Callable) -> QPushButton:
        self.add_widget(button)
        qconnect(button.clicked, lambda button_checked=False: on_clicked())
        return button

    def clear_layout(self) -> None:
        return clear_layout(self._layout)

    def reset(self, is_visible: bool) -> None:
        """
        Prepare to show a new set of buttons.
        """
        self.setHidden(not is_visible)
        self.clear_layout()


class FlexibleButtonsList(FlexibleHorizontalBar):
    _spacing: int = 8


class FlexibleBottomBar(FlexibleHorizontalBar):
    """
    Bottom bar. Shows answer buttons, answer timer, reps done today.
    """

    def __init__(self, mw: aqt.AnkiQt) -> None:
        super().__init__(mw)
        # Setup Buttons
        self.left_bucket = FlexibleButtonsList(self.mw)
        self.middle_bucket = FlexibleButtonsList(self.mw)
        self.right_bucket = FlexibleButtonsList(self.mw)
        # Setup UI
        self._setup_ui()

    def _setup_ui(self) -> None:
        self.add_widget(self.left_bucket)
        self.add_stretch()
        self.add_widget(self.middle_bucket)
        self.add_stretch()
        self.add_widget(self.right_bucket)


class FlexibleTimerLabel(QLabel):
    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._time = 0  # current time (seconds)
        self._max_time = 0  # maximum time (seconds); 0 means hidden
        self._qtimer = QTimer(self)
        self._qtimer.setInterval(1000)
        qconnect(self._qtimer.timeout, self._on_tick)
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setHidden(True)

    def start(self, max_time: int) -> None:
        self._time = 0
        self._max_time = max_time
        self._update_display()
        if self._qtimer.isActive():
            self._qtimer.stop()
        self._qtimer.start()

    def stop(self) -> None:
        if self._qtimer.isActive():
            self._qtimer.stop()

    # Internal tick handler
    def _on_tick(self) -> None:
        self._time += 1
        # clamp to max_time if set (mirrors TS: time = Math.min(maxTime, time))
        if self._time > self._max_time > 0:
            self._time = self._max_time
        self._update_display()
        # if reached max, keep ticking but display in red (TS continues interval)

    def _update_display(self) -> None:
        if self._max_time <= 0:
            super().setText("")  # hide when max_time == 0
            self.setHidden(True)
            return

        self.setHidden(False)
        t = min(self._max_time, self._time) if self._max_time > 0 else self._time
        m = t // 60
        s = t % 60
        s_str = f"{s:02d}"
        time_string = f"{m}:{s_str}"

        if t >= self._max_time > 0:
            # display red when time == maxTime (using simple HTML)
            super().setText(f"<font color='red'>{time_string}</font>")
        else:
            super().setText(time_string)
