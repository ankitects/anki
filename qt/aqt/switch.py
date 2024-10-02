# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from typing import cast

from aqt import colors, props
from aqt.qt import *
from aqt.theme import theme_manager


class Switch(QAbstractButton):
    """A horizontal slider to toggle between two states which can be denoted by strings and/or QIcons.

    The left state is the default and corresponds to isChecked()=False.
    The suppoorted slots are toggle(), for an animated transition, and setChecked().
    """

    def __init__(
        self,
        radius: int = 10,
        left_label: str = "",
        right_label: str = "",
        left_color: dict[str, str] = colors.ACCENT_CARD | {},
        right_color: dict[str, str] = colors.ACCENT_NOTE | {},
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent=parent)
        self.setCheckable(True)
        super().setChecked(False)
        self.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Fixed)
        self._left_label = left_label
        self._right_label = right_label
        self._left_color = left_color
        self._right_color = right_color
        self._path_radius = radius
        self._knob_radius = radius - 2
        self._label_padding = 4
        self._left_knob_position = self._position = radius
        self._right_knob_position = self.width() - self._path_radius
        self._left_label_position = self._label_padding / 2
        self._right_label_position = 2 * self._knob_radius
        self._hide_label: bool = False

    @pyqtProperty(int)  # type: ignore
    def position(self) -> int:
        return self._position

    @position.setter  # type: ignore
    def position(self, position: int) -> None:
        self._position = position
        self.update()

    @property
    def start_position(self) -> int:
        return (
            self._left_knob_position if self.isChecked() else self._right_knob_position
        )

    @property
    def end_position(self) -> int:
        return (
            self._right_knob_position if self.isChecked() else self._left_knob_position
        )

    @property
    def label(self) -> str:
        return self._right_label if self.isChecked() else self._left_label

    @property
    def path_color(self) -> QColor:
        color = self._right_color if self.isChecked() else self._left_color
        return theme_manager.qcolor(color)

    @property
    def label_width(self) -> int:
        font = QFont()
        font.setPixelSize(int(self._knob_radius))
        font.setWeight(QFont.Weight.Bold)
        fm = QFontMetrics(font)
        return (
            max(
                fm.horizontalAdvance(self._left_label),
                fm.horizontalAdvance(self._right_label),
            )
            + 2 * self._label_padding
        )

    def width(self) -> int:
        return self.label_width + 2 * self._path_radius

    def height(self) -> int:
        return 2 * self._path_radius

    def sizeHint(self) -> QSize:
        return QSize(
            self.width(),
            self.height(),
        )

    def setChecked(self, checked: bool) -> None:
        super().setChecked(checked)
        self._position = self.end_position
        self.update()

    def paintEvent(self, _event: QPaintEvent) -> None:
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        painter.setPen(Qt.PenStyle.NoPen)
        self._paint_path(painter)
        if not self._hide_label:
            self._paint_label(painter)
        self._paint_knob(painter)

    def _current_path_rectangle(self) -> QRectF:
        return QRectF(
            0,
            0,
            self.width(),
            self.height(),
        )

    def _current_label_rectangle(self) -> QRectF:
        return QRectF(
            (
                self._left_label_position
                if self.isChecked()
                else self._right_label_position
            ),
            0,
            self.label_width,
            self.height(),
        )

    def _current_knob_rectangle(self) -> QRectF:
        return QRectF(
            self.position - self._knob_radius,  # type: ignore
            2,
            2 * self._knob_radius,
            2 * self._knob_radius,
        )

    def _paint_path(self, painter: QPainter) -> None:
        painter.setBrush(QBrush(self.path_color))
        painter.drawRoundedRect(
            self._current_path_rectangle(), self._path_radius, self._path_radius
        )

    def _paint_knob(self, painter: QPainter) -> None:
        color = theme_manager.qcolor(colors.BUTTON_GRADIENT_START)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QBrush(color))
        painter.drawEllipse(self._current_knob_rectangle())

    def _paint_label(self, painter: QPainter) -> None:
        painter.setPen(theme_manager.qcolor(colors.CANVAS))
        font = painter.font()
        font.setPixelSize(int(self._knob_radius))
        font.setWeight(QFont.Weight.Bold)
        painter.setFont(font)
        painter.drawText(
            self._current_label_rectangle(), Qt.AlignmentFlag.AlignCenter, self.label
        )

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        super().mouseReleaseEvent(event)
        if event.button() == Qt.MouseButton.LeftButton:
            self._animate_toggle()

    def enterEvent(self, event: QEnterEvent) -> None:
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        super().enterEvent(event)

    def toggle(self) -> None:
        super().toggle()
        self._animate_toggle()

    def _animate_toggle(self) -> None:
        animation = QPropertyAnimation(self, cast(QByteArray, b"position"), self)
        animation.setDuration(int(theme_manager.var(props.TRANSITION)))
        animation.setStartValue(self.start_position)
        animation.setEndValue(self.end_position)
        # hide label during animation
        self._hide_label = True
        self.update()

        def on_animation_finished() -> None:
            self._hide_label = False
            self.update()

        qconnect(animation.finished, on_animation_finished)
        # make triggered events execute first so the animation runs smoothly afterwards
        QTimer.singleShot(50, animation.start)
