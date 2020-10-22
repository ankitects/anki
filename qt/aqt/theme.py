# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import platform
from typing import Dict, Optional

from anki.utils import isMac
from aqt import QApplication, gui_hooks, isWin
from aqt.colors import colors
from aqt.qt import QColor, QIcon, QPalette, QPixmap, QStyleFactory, Qt


class ThemeManager:
    _night_mode_preference = False
    _icon_cache_light: Dict[str, QIcon] = {}
    _icon_cache_dark: Dict[str, QIcon] = {}
    _icon_size = 128

    def macos_dark_mode(self) -> bool:
        "True if the user has night mode on, and has forced native widgets."
        if not isMac:
            return False

        from aqt import mw

        return self._night_mode_preference and mw.pm.dark_mode_widgets()

    def get_night_mode(self) -> bool:
        return self._night_mode_preference

    def set_night_mode(self, val: bool) -> None:
        self._night_mode_preference = val
        self._update_stat_colors()

    night_mode = property(get_night_mode, set_night_mode)

    def icon_from_resources(self, path: str) -> QIcon:
        "Fetch icon from Qt resources, and invert if in night mode."
        if self.night_mode:
            cache = self._icon_cache_light
        else:
            cache = self._icon_cache_dark
        icon = cache.get(path)
        if icon:
            return icon

        icon = QIcon(path)

        if self.night_mode:
            img = icon.pixmap(self._icon_size, self._icon_size).toImage()
            img.invertPixels()
            icon = QIcon(QPixmap(img))

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

    def str_color(self, key: str) -> str:
        """Get a color defined in _vars.scss

        If the colour is called '--frame-bg', key should be
        'frame-bg'.

        Returns the color as a string hex code or color name."""
        idx = 1 if self.night_mode else 0
        c = colors.get(key)
        if c is None:
            raise Exception("no such color:", key)
        return c[idx]

    def qcolor(self, key: str) -> QColor:
        """Get a color defined in _vars.scss as a QColor."""
        return QColor(self.str_color(key))

    def apply_style(self, app: QApplication) -> None:
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
                    self.str_color("window-bg"),
                    # fushion-button-hover-bg
                    "#656565",
                    self.str_color("window-bg"),
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

        text_fg = self.qcolor("text-fg")
        palette.setColor(QPalette.WindowText, text_fg)
        palette.setColor(QPalette.ToolTipText, text_fg)
        palette.setColor(QPalette.Text, text_fg)
        palette.setColor(QPalette.ButtonText, text_fg)

        hlbg = self.qcolor("highlight-bg")
        hlbg.setAlpha(64)
        palette.setColor(QPalette.HighlightedText, self.qcolor("highlight-fg"))
        palette.setColor(QPalette.Highlight, hlbg)

        window_bg = self.qcolor("window-bg")
        palette.setColor(QPalette.Window, window_bg)
        palette.setColor(QPalette.AlternateBase, window_bg)

        palette.setColor(QPalette.Button, QColor("#454545"))

        frame_bg = self.qcolor("frame-bg")
        palette.setColor(QPalette.Base, frame_bg)
        palette.setColor(QPalette.ToolTipBase, frame_bg)

        disabled_color = self.qcolor("disabled")
        palette.setColor(QPalette.Disabled, QPalette.Text, disabled_color)
        palette.setColor(QPalette.Disabled, QPalette.ButtonText, disabled_color)
        palette.setColor(QPalette.Disabled, QPalette.HighlightedText, disabled_color)

        palette.setColor(QPalette.Link, self.qcolor("link"))

        palette.setColor(QPalette.BrightText, Qt.red)

        app.setPalette(palette)

    def _update_stat_colors(self) -> None:
        import anki.stats as s

        s.colLearn = self.str_color("new-count")
        s.colRelearn = self.str_color("learn-count")
        s.colCram = self.str_color("suspended-bg")
        s.colSusp = self.str_color("suspended-bg")
        s.colMature = self.str_color("review-count")


theme_manager = ThemeManager()
