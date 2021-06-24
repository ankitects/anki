# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import platform
from dataclasses import dataclass
from typing import Dict, Optional, Tuple, Union

from anki.utils import isMac
from aqt import QApplication, colors, gui_hooks, isWin
from aqt.platform import set_dark_mode
from aqt.qt import QColor, QIcon, QPainter, QPalette, QPixmap, QStyleFactory, Qt


@dataclass
class ColoredIcon:
    path: str
    # (day, night)
    color: Tuple[str, str]

    def current_color(self, night_mode: bool) -> str:
        if night_mode:
            return self.color[1]
        else:
            return self.color[0]

    def with_color(self, color: Tuple[str, str]) -> ColoredIcon:
        return ColoredIcon(path=self.path, color=color)


class ThemeManager:
    _night_mode_preference = False
    _icon_cache_light: Dict[str, QIcon] = {}
    _icon_cache_dark: Dict[str, QIcon] = {}
    _icon_size = 128
    _dark_mode_available: Optional[bool] = None
    default_palette: Optional[QPalette] = None

    # Qt applies a gradient to the buttons in dark mode
    # from about #505050 to #606060.
    DARK_MODE_BUTTON_BG_MIDPOINT = "#555555"

    def macos_dark_mode(self) -> bool:
        "True if the user has night mode on, and has forced native widgets."
        if not isMac:
            return False

        if not self._night_mode_preference:
            return False

        if self._dark_mode_available is None:
            self._dark_mode_available = set_dark_mode(True)

        from aqt import mw

        return self._dark_mode_available and mw.pm.dark_mode_widgets()

    def get_night_mode(self) -> bool:
        return self._night_mode_preference

    def set_night_mode(self, val: bool) -> None:
        self._night_mode_preference = val
        self._update_stat_colors()

    night_mode = property(get_night_mode, set_night_mode)

    def icon_from_resources(self, path: Union[str, ColoredIcon]) -> QIcon:
        "Fetch icon from Qt resources, and invert if in night mode."
        if self.night_mode:
            cache = self._icon_cache_light
        else:
            cache = self._icon_cache_dark

        if isinstance(path, str):
            key = path
        else:
            key = f"{path.path}-{path.color}"

        icon = cache.get(key)
        if icon:
            return icon

        if isinstance(path, str):
            # default black/white
            icon = QIcon(path)
            if self.night_mode:
                img = icon.pixmap(self._icon_size, self._icon_size).toImage()
                img.invertPixels()
                icon = QIcon(QPixmap(img))
        else:
            # specified colours
            icon = QIcon(path.path)
            pixmap = icon.pixmap(16)
            painter = QPainter(pixmap)
            painter.setCompositionMode(QPainter.CompositionMode_SourceIn)
            painter.fillRect(pixmap.rect(), QColor(path.current_color(self.night_mode)))
            painter.end()
            icon = QIcon(pixmap)
            return icon

        return cache.setdefault(path, icon)

    def body_class(self, night_mode: Optional[bool] = None) -> str:
        "Returns space-separated class list for platform/theme."
        classes = []
        if isWin:
            classes.append("isWin")
        elif isMac:
            classes.append("isMac")
        else:
            classes.append("isLin")

        if night_mode is None:
            night_mode = self.night_mode
        if night_mode:
            classes.extend(["nightMode", "night_mode"])
            if self.macos_dark_mode():
                classes.append("macos-dark-mode")
        return " ".join(classes)

    def body_classes_for_card_ord(
        self, card_ord: int, night_mode: Optional[bool] = None
    ) -> str:
        "Returns body classes used when showing a card."
        return f"card card{card_ord+1} {self.body_class(night_mode)}"

    def color(self, colors: Tuple[str, str]) -> str:
        """Given day/night colors, return the correct one for the current theme."""
        idx = 1 if self.night_mode else 0
        return colors[idx]

    def qcolor(self, colors: Tuple[str, str]) -> QColor:
        return QColor(self.color(colors))

    def apply_style(self, app: QApplication) -> None:
        self.default_palette = app.style().standardPalette()
        self._apply_palette(app)
        self._apply_style(app)

    def _apply_style(self, app: QApplication) -> None:
        buf = ""

        if isWin and platform.release() == "10" and not self.night_mode:
            # add missing bottom border to menubar
            buf += """
QMenuBar {
  border-bottom: 1px solid #aaa;
  background: white;
}
"""
            # qt bug? setting the above changes the browser sidebar
            # to white as well, so set it back
            buf += """
QTreeWidget {
  background: #eee;
}
            """

        if self.night_mode:
            buf += """
QToolTip {
  border: 0;
}
            """

            if not self.macos_dark_mode():
                buf += """
QScrollBar { background-color: %s; }
QScrollBar::handle { background-color: %s; border-radius: 5px; } 

QScrollBar:horizontal { height: 12px; }
QScrollBar::handle:horizontal { min-width: 50px; } 

QScrollBar:vertical { width: 12px; }
QScrollBar::handle:vertical { min-height: 50px; } 
    
QScrollBar::add-line {
      border: none;
      background: none;
}

QScrollBar::sub-line {
      border: none;
      background: none;
}

QTabWidget { background-color: %s; }
""" % (
                    self.color(colors.WINDOW_BG),
                    # fushion-button-hover-bg
                    "#656565",
                    self.color(colors.WINDOW_BG),
                )

        # allow addons to modify the styling
        buf = gui_hooks.style_did_init(buf)

        app.setStyleSheet(buf)

    def _apply_palette(self, app: QApplication) -> None:
        if not self.night_mode:
            return

        if not self.macos_dark_mode():
            app.setStyle(QStyleFactory.create("fusion"))  # type: ignore

        palette = QPalette()

        text_fg = self.qcolor(colors.TEXT_FG)
        palette.setColor(QPalette.WindowText, text_fg)
        palette.setColor(QPalette.ToolTipText, text_fg)
        palette.setColor(QPalette.Text, text_fg)
        palette.setColor(QPalette.ButtonText, text_fg)

        hlbg = self.qcolor(colors.HIGHLIGHT_BG)
        hlbg.setAlpha(64)
        palette.setColor(QPalette.HighlightedText, self.qcolor(colors.HIGHLIGHT_FG))
        palette.setColor(QPalette.Highlight, hlbg)

        window_bg = self.qcolor(colors.WINDOW_BG)
        palette.setColor(QPalette.Window, window_bg)
        palette.setColor(QPalette.AlternateBase, window_bg)

        palette.setColor(QPalette.Button, QColor("#454545"))

        frame_bg = self.qcolor(colors.FRAME_BG)
        palette.setColor(QPalette.Base, frame_bg)
        palette.setColor(QPalette.ToolTipBase, frame_bg)

        disabled_color = self.qcolor(colors.DISABLED)
        palette.setColor(QPalette.Disabled, QPalette.Text, disabled_color)
        palette.setColor(QPalette.Disabled, QPalette.ButtonText, disabled_color)
        palette.setColor(QPalette.Disabled, QPalette.HighlightedText, disabled_color)

        palette.setColor(QPalette.Link, self.qcolor(colors.LINK))

        palette.setColor(QPalette.BrightText, Qt.red)

        app.setPalette(palette)

    def _update_stat_colors(self) -> None:
        import anki.stats as s

        s.colLearn = self.color(colors.NEW_COUNT)
        s.colRelearn = self.color(colors.LEARN_COUNT)
        s.colCram = self.color(colors.SUSPENDED_BG)
        s.colSusp = self.color(colors.SUSPENDED_BG)
        s.colMature = self.color(colors.REVIEW_COUNT)


theme_manager = ThemeManager()
