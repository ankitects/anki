# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import enum
import os
import re
import subprocess
from dataclasses import dataclass
from typing import Callable, List, Tuple

import anki.lang
import aqt
from anki.lang import is_rtl
from anki.utils import is_lin, is_mac, is_win
from aqt import QApplication, colors, gui_hooks
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
    color: dict[str, str]

    def current_color(self, night_mode: bool) -> str:
        if night_mode:
            return self.color.get("dark", "")
        else:
            return self.color.get("light", "")

    def with_color(self, color: dict[str, str]) -> ColoredIcon:
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

    def rtl(self) -> bool:
        return is_rtl(anki.lang.current_lang)

    def left(self) -> str:
        return "right" if self.rtl() else "left"

    def right(self) -> str:
        return "left" if self.rtl() else "right"

    # Qt applies a gradient to the buttons in dark mode
    # from about #505050 to #606060.
    DARK_MODE_BUTTON_BG_MIDPOINT = "#555555"

    def macos_dark_mode(self) -> bool:
        "True if the user has night mode on."
        if not is_mac:
            return False

        if not self._night_mode_preference:
            return False

        if self._dark_mode_available is None:
            self._dark_mode_available = set_macos_dark_mode(True)

        return self._dark_mode_available

    def get_night_mode(self) -> bool:
        return self._night_mode_preference

    def set_night_mode(self, val: bool) -> None:
        self._night_mode_preference = val
        self._update_stat_colors()

    night_mode = property(get_night_mode, set_night_mode)

    def themed_icon(self, path: str) -> str:
        "Fetch themed version of svg."
        from aqt.utils import aqt_data_folder

        if m := re.match(r"(?:mdi:)(.+)$", path):
            name = m.group(1)
        else:
            return path

        filename = f"{name}-{'dark' if self.night_mode else 'light'}.svg"

        return (
            os.path.join(aqt_data_folder(), "qt", "icons", filename)
            .replace("\\\\?\\", "")
            .replace("\\", "/")
        )

    def icon_from_resources(self, path: str | ColoredIcon) -> QIcon:
        "Fetch icon from Qt resources."
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
            if "mdi:" in path:
                icon = QIcon(self.themed_icon(path))
            else:
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
        "Returns space-separated class list for platform/theme/global settings."
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
        if aqt.mw.pm.reduced_motion():
            classes.append("reduced-motion")
        return " ".join(classes)

    def body_classes_for_card_ord(
        self, card_ord: int, night_mode: bool | None = None
    ) -> str:
        "Returns body classes used when showing a card."
        return f"card card{card_ord+1} {self.body_class(night_mode)}"

    def var(self, vars: dict[str, str]) -> str:
        """Given day/night colors/props, return the correct one for the current theme."""
        return vars["dark" if self.night_mode else "light"]

    def qcolor(self, colors: dict[str, str]) -> QColor:
        """Create QColor instance from CSS string for the current theme."""

        if m := re.match(
            r"rgba\((\d+),\s*(\d+),\s*(\d+),\s*(\d+\.*\d+?)\)", self.var(colors)
        ):
            return QColor(
                int(m.group(1)),
                int(m.group(2)),
                int(m.group(3)),
                int(255 * float(m.group(4))),
            )
        return QColor(self.var(colors))

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
        from aqt.stylesheets import splitter_styles

        buf = splitter_styles(self)

        if not is_mac:
            from aqt.stylesheets import (
                button_styles,
                checkbox_styles,
                combobox_styles,
                general_styles,
                menu_styles,
                scrollbar_styles,
                spinbox_styles,
                table_styles,
                tabwidget_styles,
            )

            buf += "".join(
                [
                    general_styles(self),
                    button_styles(self),
                    checkbox_styles(self),
                    menu_styles(self),
                    combobox_styles(self),
                    tabwidget_styles(self),
                    table_styles(self),
                    spinbox_styles(self),
                    scrollbar_styles(self),
                ]
            )

        # allow addons to modify the styling
        buf = gui_hooks.style_did_init(buf)

        app.setStyleSheet(buf)

    def _apply_palette(self, app: QApplication) -> None:
        set_macos_dark_mode(self.night_mode)

        if is_mac:
            app.setStyle(QStyleFactory.create(self._default_style))  # type: ignore
            self.default_palette.setColor(
                QPalette.ColorRole.Window, self.qcolor(colors.CANVAS)
            )
            app.setPalette(self.default_palette)
            return

        app.setStyle(QStyleFactory.create("fusion"))  # type: ignore

        palette = QPalette()
        text = self.qcolor(colors.FG)
        palette.setColor(QPalette.ColorRole.WindowText, text)
        palette.setColor(QPalette.ColorRole.ToolTipText, text)
        palette.setColor(QPalette.ColorRole.Text, text)
        palette.setColor(QPalette.ColorRole.ButtonText, text)

        hlbg = self.qcolor(colors.HIGHLIGHT_BG)
        palette.setColor(
            QPalette.ColorRole.HighlightedText, self.qcolor(colors.HIGHLIGHT_FG)
        )
        palette.setColor(QPalette.ColorRole.Highlight, hlbg)

        canvas = self.qcolor(colors.CANVAS)
        palette.setColor(QPalette.ColorRole.Window, canvas)
        palette.setColor(QPalette.ColorRole.AlternateBase, canvas)

        palette.setColor(QPalette.ColorRole.Button, self.qcolor(colors.BUTTON_BG))

        input_base = self.qcolor(colors.CANVAS_CODE)
        palette.setColor(QPalette.ColorRole.Base, input_base)
        palette.setColor(QPalette.ColorRole.ToolTipBase, input_base)

        palette.setColor(
            QPalette.ColorRole.PlaceholderText, self.qcolor(colors.FG_SUBTLE)
        )

        disabled_color = self.qcolor(colors.FG_DISABLED)
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

        palette.setColor(QPalette.ColorRole.Link, self.qcolor(colors.FG_LINK))

        palette.setColor(QPalette.ColorRole.BrightText, Qt.GlobalColor.red)

        app.setPalette(palette)

    def _update_stat_colors(self) -> None:
        import anki.stats as s

        s.colLearn = self.var(colors.STATE_NEW)
        s.colRelearn = self.var(colors.STATE_LEARN)
        s.colCram = self.var(colors.STATE_SUSPENDED)
        s.colSusp = self.var(colors.STATE_SUSPENDED)
        s.colMature = self.var(colors.STATE_REVIEW)
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

    try:
        key = OpenKey(
            HKEY_CURRENT_USER,
            r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize",
        )
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
