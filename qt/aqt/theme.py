# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import enum
import platform
import subprocess
from dataclasses import dataclass
from typing import Callable, List, Tuple

import aqt
from anki.utils import is_lin, is_mac, is_win
from aqt import QApplication, colors, gui_hooks, props
from aqt.qt import (
    QColor,
    QGuiApplication,
    QIcon,
    QPainter,
    QPalette,
    QPixmap,
    QStyleFactory,
    Qt,
)


@dataclass
class ColoredIcon:
    path: str
    # (day, night)
    color: tuple[str, str]

    def current_color(self, night_mode: bool) -> str:
        if night_mode:
            return self.color[1]
        else:
            return self.color[0]

    def with_color(self, color: tuple[str, str]) -> ColoredIcon:
        return ColoredIcon(path=self.path, color=color)


class Theme(enum.IntEnum):
    FOLLOW_SYSTEM = 0
    LIGHT = 1
    DARK = 2


class ThemeManager:
    _night_mode_preference = False
    _icon_cache_light: dict[str, QIcon] = {}
    _icon_cache_dark: dict[str, QIcon] = {}
    _icon_size = 128
    _dark_mode_available: bool | None = None
    default_palette: QPalette | None = None
    _default_style: str | None = None

    # Qt applies a gradient to the buttons in dark mode
    # from about #505050 to #606060.
    DARK_MODE_BUTTON_BG_MIDPOINT = "#555555"

    def macos_dark_mode(self) -> bool:
        "True if the user has night mode on, and has forced native widgets."
        if not is_mac:
            return False

        if not self._night_mode_preference:
            return False

        if self._dark_mode_available is None:
            self._dark_mode_available = set_macos_dark_mode(True)

        from aqt import mw

        return self._dark_mode_available and mw.pm.dark_mode_widgets()

    def get_night_mode(self) -> bool:
        return self._night_mode_preference

    def set_night_mode(self, val: bool) -> None:
        self._night_mode_preference = val
        self._update_stat_colors()

    night_mode = property(get_night_mode, set_night_mode)

    def icon_from_resources(self, path: str | ColoredIcon) -> QIcon:
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
            painter.setCompositionMode(
                QPainter.CompositionMode.CompositionMode_SourceIn
            )
            painter.fillRect(pixmap.rect(), QColor(path.current_color(self.night_mode)))
            painter.end()
            icon = QIcon(pixmap)
            return icon

        return cache.setdefault(path, icon)

    def body_class(self, night_mode: bool | None = None) -> str:
        "Returns space-separated class list for platform/theme."
        classes = []
        if is_win:
            classes.append("isWin")
        elif is_mac:
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
        self, card_ord: int, night_mode: bool | None = None
    ) -> str:
        "Returns body classes used when showing a card."
        return f"card card{card_ord+1} {self.body_class(night_mode)}"

    def value(self, values: tuple[str, str]) -> str:
        """Given day/night values, return the correct one for the current theme."""
        idx = 1 if self.night_mode else 0
        return values[idx]

    def qcolor(self, colors: tuple[str, str]) -> QColor:
        return QColor(self.value(colors))

    def _determine_night_mode(self) -> bool:
        theme = aqt.mw.pm.theme()
        if theme == Theme.LIGHT:
            return False
        elif theme == Theme.DARK:
            return True
        else:
            if is_win:
                return get_windows_dark_mode()
            elif is_mac:
                return get_macos_dark_mode()
            else:
                return get_linux_dark_mode()

    def apply_style_if_system_style_changed(self) -> None:
        theme = aqt.mw.pm.theme()
        if theme != Theme.FOLLOW_SYSTEM:
            return
        if self._determine_night_mode() != self.night_mode:
            self.apply_style()

    def apply_style(self) -> None:
        "Apply currently configured style."
        app = aqt.mw.app
        self.night_mode = self._determine_night_mode()
        if not self.default_palette:
            self.default_palette = QGuiApplication.palette()
            self._default_style = app.style().objectName()
        self._apply_palette(app)
        self._apply_style(app)
        gui_hooks.theme_did_change()

    def _apply_style(self, app: QApplication) -> None:
        buf = ""

        if is_win and platform.release() == "10":
            # day mode is missing a bottom border; background must be
            # also set for border to apply
            buf += f"""
QMenuBar {{
  border-bottom: 1px solid {self.value(colors.BORDER)};
  background: {self.value(colors.WINDOW_BG) if self.night_mode else "white"};
}}
"""

        buf += """
QToolTip {
  border: 0;
}
        """

        buf += f"""
QScrollBar {{
  background-color: {self.value(colors.FRAME_BG)};
}}
QScrollBar::handle {{
  background-color: {self.value(colors.CONTROL_IDLE)};
  border-radius: {self.value(props.BORDER_RADIUS_DEFAULT)};
}} 
QScrollBar::handle:hover {{
  background-color: {self.value(colors.CONTROL_HOVER)};
}}
QScrollBar::handle:pressed {{
  background-color: {self.value(colors.CONTROL_ACTIVE)};
}}
QScrollBar:horizontal {{
  height: {self.value(props.SCROLLBAR_HANDLE_WIDTH)};
}}
QScrollBar:vertical {{
  width: {self.value(props.SCROLLBAR_HANDLE_WIDTH)};
}}
QScrollBar::handle:vertical {{
  min-height: {self.value(props.SCROLLBAR_HANDLE_LENGTH)};
}} 
QScrollBar::groove,
QScrollBar::add-line,
QScrollBar::sub-line {{
  border: none;
  background: none;
}}

QTabWidget {{
  background-color: {self.value(colors.WINDOW_BG)};
}}
        """
        # allow addons to modify the styling
        buf = gui_hooks.style_did_init(buf)

        app.setStyleSheet(buf)

    def _apply_palette(self, app: QApplication) -> None:
        set_macos_dark_mode(self.night_mode)

        if not self.night_mode:
            app.setStyle(QStyleFactory.create(self._default_style))  # type: ignore
            app.setPalette(self.default_palette)
            return

        if not self.macos_dark_mode():
            app.setStyle(QStyleFactory.create("fusion"))  # type: ignore

        palette = QPalette()

        text_fg = self.qcolor(colors.TEXT_FG)
        palette.setColor(QPalette.ColorRole.WindowText, text_fg)
        palette.setColor(QPalette.ColorRole.ToolTipText, text_fg)
        palette.setColor(QPalette.ColorRole.Text, text_fg)
        palette.setColor(QPalette.ColorRole.ButtonText, text_fg)

        hlbg = self.qcolor(colors.HIGHLIGHT_BG)
        hlbg.setAlpha(64)
        palette.setColor(
            QPalette.ColorRole.HighlightedText, self.qcolor(colors.HIGHLIGHT_FG)
        )
        palette.setColor(QPalette.ColorRole.Highlight, hlbg)

        window_bg = self.qcolor(colors.WINDOW_BG)
        palette.setColor(QPalette.ColorRole.Window, window_bg)
        palette.setColor(QPalette.ColorRole.AlternateBase, window_bg)

        palette.setColor(QPalette.ColorRole.Button, QColor("#454545"))

        frame_bg = self.qcolor(colors.FRAME_BG)
        palette.setColor(QPalette.ColorRole.Base, frame_bg)
        palette.setColor(QPalette.ColorRole.ToolTipBase, frame_bg)

        disabled_color = self.qcolor(colors.DISABLED)
        palette.setColor(QPalette.ColorRole.PlaceholderText, disabled_color)
        palette.setColor(
            QPalette.ColorGroup.Disabled, QPalette.ColorRole.Text, disabled_color
        )
        palette.setColor(
            QPalette.ColorGroup.Disabled, QPalette.ColorRole.ButtonText, disabled_color
        )
        palette.setColor(
            QPalette.ColorGroup.Disabled,
            QPalette.ColorRole.HighlightedText,
            disabled_color,
        )

        palette.setColor(QPalette.ColorRole.Link, self.qcolor(colors.LINK))

        palette.setColor(QPalette.ColorRole.BrightText, Qt.GlobalColor.red)

        app.setPalette(palette)

    def _update_stat_colors(self) -> None:
        import anki.stats as s

        s.colLearn = self.value(colors.NEW_COUNT)
        s.colRelearn = self.value(colors.LEARN_COUNT)
        s.colCram = self.value(colors.SUSPENDED_BG)
        s.colSusp = self.value(colors.SUSPENDED_BG)
        s.colMature = self.value(colors.REVIEW_COUNT)
        s._legacy_nightmode = self._night_mode_preference


def get_windows_dark_mode() -> bool:
    "True if Windows system is currently in dark mode."
    if not is_win:
        return False

    from winreg import (  # type: ignore[attr-defined] # pylint: disable=import-error
        HKEY_CURRENT_USER,
        OpenKey,
        QueryValueEx,
    )

    key = OpenKey(
        HKEY_CURRENT_USER,
        r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize",
    )
    try:
        return not QueryValueEx(key, "AppsUseLightTheme")[0]
    except Exception as err:
        # key reportedly missing or set to wrong type on some systems
        return False


def set_macos_dark_mode(enabled: bool) -> bool:
    "True if setting successful."
    from aqt._macos_helper import macos_helper

    if not macos_helper:
        return False
    return macos_helper.set_darkmode_enabled(enabled)


def get_macos_dark_mode() -> bool:
    "True if macOS system is currently in dark mode."
    from aqt._macos_helper import macos_helper

    if not macos_helper:
        return False
    return macos_helper.system_is_dark()


def get_linux_dark_mode() -> bool:
    """True if Linux system is in dark mode.
    Only works if D-Bus is installed and system uses org.freedesktop.appearance
    color-scheme to indicate dark mode preference OR if GNOME theme has
    '-dark' in the name."""
    if not is_lin:
        return False

    def parse_stdout_dbus_send(stdout: str) -> bool:
        dbus_response = stdout.split()
        if len(dbus_response) != 4:
            return False

        # https://github.com/flatpak/xdg-desktop-portal/blob/main/data/org.freedesktop.impl.portal.Settings.xml#L40
        PREFER_DARK = "1"

        return dbus_response[-1] == PREFER_DARK

    dark_mode_detection_strategies: List[Tuple[str, Callable[[str], bool]]] = [
        (
            "dbus-send --session --print-reply=literal --reply-timeout=1000 "
            "--dest=org.freedesktop.portal.Desktop /org/freedesktop/portal/desktop "
            "org.freedesktop.portal.Settings.Read string:'org.freedesktop.appearance' "
            "string:'color-scheme'",
            parse_stdout_dbus_send,
        ),
        (
            "gsettings get org.gnome.desktop.interface gtk-theme",
            lambda stdout: "-dark" in stdout.lower(),
        ),
    ]

    for cmd, parse_stdout in dark_mode_detection_strategies:
        try:
            process = subprocess.run(
                cmd,
                shell=True,
                check=True,
                capture_output=True,
                encoding="utf8",
            )
        except FileNotFoundError as e:
            # detection strategy failed, missing program
            print(e)
            continue

        except subprocess.CalledProcessError as e:
            # detection strategy failed, command returned error
            print(e)
            continue

        return parse_stdout(process.stdout)

    return False  # all dark mode detection strategies failed


theme_manager = ThemeManager()
