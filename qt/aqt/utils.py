# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from functools import wraps
from pathlib import Path
from typing import TYPE_CHECKING, Any, Literal, Sequence, no_type_check

from send2trash import send2trash

import aqt
from anki._legacy import DeprecatedNamesMixinForModule
from anki.collection import Collection, HelpPage
from anki.lang import TR, tr_legacyglobal  # pylint: disable=unused-import
from anki.utils import (
    invalid_filename,
    is_mac,
    is_win,
    no_bundled_libs,
    version_with_build,
)
from aqt.qt import *
from aqt.theme import theme_manager

if TYPE_CHECKING:
    TextFormat = Literal["plain", "rich"]


def aqt_data_folder() -> str:
    # running in Bazel on macOS?
    if path := os.getenv("AQT_DATA_FOLDER"):
        return path
    # packaged?
    elif getattr(sys, "frozen", False):
        path = os.path.join(sys.prefix, "lib/aqt/data")
        if os.path.exists(path):
            return path
        else:
            return os.path.join(sys.prefix, "../Resources/aqt/data")
    elif os.path.exists(dir := os.path.join(os.path.dirname(__file__), "data")):
        return os.path.abspath(dir)
    else:
        # should only happen when running unit tests
        print("warning, data folder not found")
        return "."


# shortcut to access Fluent translations; set as
tr = tr_legacyglobal

HelpPageArgument = Union["HelpPage.V", str]


def openHelp(section: HelpPageArgument) -> None:
    if isinstance(section, str):
        link = tr.backend().help_page_link(page=HelpPage.INDEX) + section
    else:
        link = tr.backend().help_page_link(page=section)
    openLink(link)


def openLink(link: str | QUrl) -> None:
    tooltip(tr.qt_misc_loading(), period=1000)
    with no_bundled_libs():
        QDesktopServices.openUrl(QUrl(link))


def showWarning(
    text: str,
    parent: QWidget | None = None,
    help: HelpPageArgument | None = None,
    title: str = "Anki",
    textFormat: TextFormat | None = None,
) -> int:
    "Show a small warning with an OK button."
    return showInfo(text, parent, help, "warning", title=title, textFormat=textFormat)


def showCritical(
    text: str,
    parent: QDialog | None = None,
    help: str = "",
    title: str = "Anki",
    textFormat: TextFormat | None = None,
) -> int:
    "Show a small critical error with an OK button."
    return showInfo(text, parent, help, "critical", title=title, textFormat=textFormat)


def showInfo(
    text: str,
    parent: QWidget | None = None,
    help: HelpPageArgument | None = None,
    type: str = "info",
    title: str = "Anki",
    textFormat: TextFormat | None = None,
    customBtns: list[QMessageBox.StandardButton] | None = None,
) -> int:
    "Show a small info window with an OK button."
    parent_widget: QWidget
    if parent is None:
        parent_widget = aqt.mw.app.activeWindow() or aqt.mw
    else:
        parent_widget = parent
    if type == "warning":
        icon = QMessageBox.Icon.Warning
    elif type == "critical":
        icon = QMessageBox.Icon.Critical
    else:
        icon = QMessageBox.Icon.Information
    mb = QMessageBox(parent_widget)  #
    if textFormat == "plain":
        mb.setTextFormat(Qt.TextFormat.PlainText)
    elif textFormat == "rich":
        mb.setTextFormat(Qt.TextFormat.RichText)
    elif textFormat is not None:
        raise Exception("unexpected textFormat type")
    mb.setText(text)
    mb.setIcon(icon)
    mb.setWindowTitle(title)
    if customBtns:
        default = None
        for btn in customBtns:
            b = mb.addButton(btn)
            if not default:
                default = b
        mb.setDefaultButton(default)
    else:
        b = mb.addButton(QMessageBox.StandardButton.Ok)
        b.setDefault(True)
    if help is not None:
        b = mb.addButton(QMessageBox.StandardButton.Help)
        qconnect(b.clicked, lambda: openHelp(help))
        b.setAutoDefault(False)
    return mb.exec()


def showText(
    txt: str,
    parent: QWidget | None = None,
    type: str = "text",
    run: bool = True,
    geomKey: str | None = None,
    minWidth: int = 500,
    minHeight: int = 400,
    title: str = "Anki",
    copyBtn: bool = False,
    plain_text_edit: bool = False,
) -> tuple[QDialog, QDialogButtonBox] | None:
    if not parent:
        parent = aqt.mw.app.activeWindow() or aqt.mw
    diag = QDialog(parent)
    diag.setWindowTitle(title)
    disable_help_button(diag)
    layout = QVBoxLayout(diag)
    diag.setLayout(layout)
    text: QPlainTextEdit | QTextBrowser
    if plain_text_edit:
        # used by the importer
        text = QPlainTextEdit()
        text.setReadOnly(True)
        text.setWordWrapMode(QTextOption.WrapMode.NoWrap)
        text.setPlainText(txt)
    else:
        text = QTextBrowser()
        text.setOpenExternalLinks(True)
        if type == "text":
            text.setPlainText(txt)
        else:
            text.setHtml(txt)
    layout.addWidget(text)
    box = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
    layout.addWidget(box)
    if copyBtn:

        def onCopy() -> None:
            QApplication.clipboard().setText(text.toPlainText())

        btn = QPushButton(tr.qt_misc_copy_to_clipboard())
        qconnect(btn.clicked, onCopy)
        box.addButton(btn, QDialogButtonBox.ButtonRole.ActionRole)

    def onReject() -> None:
        if geomKey:
            saveGeom(diag, geomKey)
        QDialog.reject(diag)

    qconnect(box.rejected, onReject)

    def onFinish() -> None:
        if geomKey:
            saveGeom(diag, geomKey)

    qconnect(box.accepted, onFinish)
    diag.setMinimumHeight(minHeight)
    diag.setMinimumWidth(minWidth)
    if geomKey:
        restoreGeom(diag, geomKey)
    if run:
        diag.exec()
        return None
    else:
        return diag, box


def askUser(
    text: str,
    parent: QWidget = None,
    help: HelpPageArgument = None,
    defaultno: bool = False,
    msgfunc: Callable | None = None,
    title: str = "Anki",
) -> bool:
    "Show a yes/no question. Return true if yes."
    if not parent:
        parent = aqt.mw.app.activeWindow()
    if not msgfunc:
        msgfunc = QMessageBox.question
    sb = QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
    if help:
        sb |= QMessageBox.StandardButton.Help
    while 1:
        if defaultno:
            default = QMessageBox.StandardButton.No
        else:
            default = QMessageBox.StandardButton.Yes
        r = msgfunc(parent, title, text, sb, default)
        if r == QMessageBox.StandardButton.Help:
            openHelp(help)
        else:
            break
    return r == QMessageBox.StandardButton.Yes


class ButtonedDialog(QMessageBox):
    def __init__(
        self,
        text: str,
        buttons: list[str],
        parent: QWidget | None = None,
        help: HelpPageArgument = None,
        title: str = "Anki",
    ):
        QMessageBox.__init__(self, parent)
        self._buttons: list[QPushButton] = []
        self.setWindowTitle(title)
        self.help = help
        self.setIcon(QMessageBox.Icon.Warning)
        self.setText(text)
        for b in buttons:
            self._buttons.append(self.addButton(b, QMessageBox.ButtonRole.AcceptRole))
        if help:
            self.addButton(tr.actions_help(), QMessageBox.ButtonRole.HelpRole)
            buttons.append(tr.actions_help())

    def run(self) -> str:
        self.exec()
        but = self.clickedButton().text()
        if but == "Help":
            # FIXME stop dialog closing?
            openHelp(self.help)
        txt = self.clickedButton().text()
        # work around KDE 'helpfully' adding accelerators to button text of Qt apps
        return txt.replace("&", "")

    def setDefault(self, idx: int) -> None:
        self.setDefaultButton(self._buttons[idx])


def askUserDialog(
    text: str,
    buttons: list[str],
    parent: QWidget | None = None,
    help: HelpPageArgument = None,
    title: str = "Anki",
) -> ButtonedDialog:
    if not parent:
        parent = aqt.mw
    diag = ButtonedDialog(text, buttons, parent, help, title=title)
    return diag


class GetTextDialog(QDialog):
    def __init__(
        self,
        parent: QWidget | None,
        question: str,
        help: HelpPageArgument = None,
        edit: QLineEdit | None = None,
        default: str = "",
        title: str = "Anki",
        minWidth: int = 400,
    ) -> None:
        QDialog.__init__(self, parent)
        self.setWindowTitle(title)
        disable_help_button(self)
        self.question = question
        self.help = help
        self.qlabel = QLabel(question)
        self.setMinimumWidth(minWidth)
        v = QVBoxLayout()
        v.addWidget(self.qlabel)
        if not edit:
            edit = QLineEdit()
        self.l = edit
        if default:
            self.l.setText(default)
            self.l.selectAll()
        v.addWidget(self.l)
        buts = (
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        if help:
            buts |= QDialogButtonBox.StandardButton.Help
        b = QDialogButtonBox(buts)  # type: ignore
        v.addWidget(b)
        self.setLayout(v)
        qconnect(b.button(QDialogButtonBox.StandardButton.Ok).clicked, self.accept)
        qconnect(b.button(QDialogButtonBox.StandardButton.Cancel).clicked, self.reject)
        if help:
            qconnect(
                b.button(QDialogButtonBox.StandardButton.Help).clicked,
                self.helpRequested,
            )

    def accept(self) -> None:
        return QDialog.accept(self)

    def reject(self) -> None:
        return QDialog.reject(self)

    def helpRequested(self) -> None:
        openHelp(self.help)


def getText(
    prompt: str,
    parent: QWidget | None = None,
    help: HelpPageArgument = None,
    edit: QLineEdit | None = None,
    default: str = "",
    title: str = "Anki",
    geomKey: str | None = None,
    **kwargs: Any,
) -> tuple[str, int]:
    "Returns (string, succeeded)."
    if not parent:
        parent = aqt.mw.app.activeWindow() or aqt.mw
    d = GetTextDialog(
        parent, prompt, help=help, edit=edit, default=default, title=title, **kwargs
    )
    d.setWindowModality(Qt.WindowModality.WindowModal)
    if geomKey:
        restoreGeom(d, geomKey)
    ret = d.exec()
    if geomKey and ret:
        saveGeom(d, geomKey)
    return (str(d.l.text()), ret)


def getOnlyText(*args: Any, **kwargs: Any) -> str:
    (s, r) = getText(*args, **kwargs)
    if r:
        return s
    else:
        return ""


# fixme: these utilities could be combined into a single base class
# unused by Anki, but used by add-ons
def chooseList(
    prompt: str, choices: list[str], startrow: int = 0, parent: Any = None
) -> int:
    if not parent:
        parent = aqt.mw.app.activeWindow()
    d = QDialog(parent)
    disable_help_button(d)
    d.setWindowModality(Qt.WindowModality.WindowModal)
    l = QVBoxLayout()
    d.setLayout(l)
    t = QLabel(prompt)
    l.addWidget(t)
    c = QListWidget()
    c.addItems(choices)
    c.setCurrentRow(startrow)
    l.addWidget(c)
    bb = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok)
    qconnect(bb.accepted, d.accept)
    l.addWidget(bb)
    d.exec()
    return c.currentRow()


def getTag(
    parent: QWidget, deck: Collection, question: str, **kwargs: Any
) -> tuple[str, int]:
    from aqt.tagedit import TagEdit

    te = TagEdit(parent)
    te.setCol(deck)
    ret = getText(question, parent, edit=te, geomKey="getTag", **kwargs)
    te.hideCompleter()
    return ret


def disable_help_button(widget: QWidget) -> None:
    "Disable the help button in the window titlebar."
    widget.setWindowFlags(
        widget.windowFlags() & ~Qt.WindowType.WindowContextHelpButtonHint
    )


def setWindowIcon(widget: QWidget) -> None:
    icon = QIcon()
    icon.addPixmap(QPixmap("icons:anki.png"), QIcon.Mode.Normal, QIcon.State.Off)
    widget.setWindowIcon(icon)


# File handling
######################################################################


def getFile(
    parent: QWidget,
    title: str,
    # single file returned unless multi=True
    cb: Callable[[str | Sequence[str]], None] | None,
    filter: str = "*.*",
    dir: str | None = None,
    key: str | None = None,
    multi: bool = False,  # controls whether a single or multiple files is returned
) -> Sequence[str] | str | None:
    "Ask the user for a file."
    if dir and key:
        raise Exception("expected dir or key")
    if not dir:
        dirkey = f"{key}Directory"
        dir = aqt.mw.pm.profile.get(dirkey, "")
    else:
        dirkey = None
    d = QFileDialog(parent)
    mode = (
        QFileDialog.FileMode.ExistingFiles
        if multi
        else QFileDialog.FileMode.ExistingFile
    )
    d.setFileMode(mode)
    if os.path.exists(dir):
        d.setDirectory(dir)
    d.setWindowTitle(title)
    d.setNameFilter(filter)
    ret = []

    def accept() -> None:
        files = list(d.selectedFiles())
        if dirkey:
            dir = os.path.dirname(files[0])
            aqt.mw.pm.profile[dirkey] = dir
        result = files if multi else files[0]
        if cb:
            cb(result)
        ret.append(result)

    qconnect(d.accepted, accept)
    if key:
        restoreState(d, key)
    d.exec()
    if key:
        saveState(d, key)
    return ret[0] if ret else None


def getSaveFile(
    parent: QDialog,
    title: str,
    dir_description: str,
    key: str,
    ext: str,
    fname: str | None = None,
) -> str:
    """Ask the user for a file to save. Use DIR_DESCRIPTION as config
    variable. The file dialog will default to open with FNAME."""
    config_key = f"{dir_description}Directory"

    defaultPath = QStandardPaths.writableLocation(
        QStandardPaths.StandardLocation.DocumentsLocation
    )
    base = aqt.mw.pm.profile.get(config_key, defaultPath)
    path = os.path.join(base, fname)
    file = QFileDialog.getSaveFileName(
        parent,
        title,
        path,
        f"{key} (*{ext})",
        options=QFileDialog.Option.DontConfirmOverwrite,
    )[0]
    if file:
        # add extension
        if not file.lower().endswith(ext):
            file += ext
        # save new default
        dir = os.path.dirname(file)
        aqt.mw.pm.profile[config_key] = dir
        # check if it exists
        if os.path.exists(file):
            if not askUser(tr.qt_misc_this_file_exists_are_you_sure(), parent):
                return None
    return file


def saveGeom(widget: QWidget, key: str) -> None:
    # restoring a fullscreen window is buggy
    # (at the time of writing; Qt 6.2.2 and 5.15)
    if not widget.isFullScreen():
        aqt.mw.pm.profile[f"{key}Geom"] = widget.saveGeometry()


def restoreGeom(
    widget: QWidget, key: str, offset: int | None = None, adjustSize: bool = False
) -> None:
    key += "Geom"
    if aqt.mw.pm.profile.get(key):
        widget.restoreGeometry(aqt.mw.pm.profile[key])
        if is_mac and offset:
            if qtmajor > 5 or qtminor > 6:
                # bug in osx toolkit
                s = widget.size()
                widget.resize(s.width(), s.height() + offset * 2)
        ensureWidgetInScreenBoundaries(widget)
    else:
        if adjustSize:
            widget.adjustSize()


def ensureWidgetInScreenBoundaries(widget: QWidget) -> None:
    handle = widget.window().windowHandle()
    if not handle:
        # window has not yet been shown, retry later
        aqt.mw.progress.timer(
            50, lambda: ensureWidgetInScreenBoundaries(widget), False, parent=widget
        )
        return

    # ensure widget is smaller than screen bounds
    geom = handle.screen().availableGeometry()
    wsize = widget.size()
    cappedWidth = min(geom.width(), wsize.width())
    cappedHeight = min(geom.height(), wsize.height())
    if cappedWidth > wsize.width() or cappedHeight > wsize.height():
        widget.resize(QSize(cappedWidth, cappedHeight))

    # ensure widget is inside top left
    wpos = widget.pos()
    x = max(geom.x(), wpos.x())
    y = max(geom.y(), wpos.y())
    # and bottom right
    x = min(x, geom.width() + geom.x() - cappedWidth)
    y = min(y, geom.height() + geom.y() - cappedHeight)
    if x != wpos.x() or y != wpos.y():
        widget.move(x, y)


def saveState(widget: QFileDialog | QMainWindow, key: str) -> None:
    key += "State"
    aqt.mw.pm.profile[key] = widget.saveState()


def restoreState(widget: QFileDialog | QMainWindow, key: str) -> None:
    key += "State"
    if aqt.mw.pm.profile.get(key):
        widget.restoreState(aqt.mw.pm.profile[key])


def saveSplitter(widget: QSplitter, key: str) -> None:
    key += "Splitter"
    aqt.mw.pm.profile[key] = widget.saveState()


def restoreSplitter(widget: QSplitter, key: str) -> None:
    key += "Splitter"
    if aqt.mw.pm.profile.get(key):
        widget.restoreState(aqt.mw.pm.profile[key])


def _header_key(key: str) -> str:
    # not compatible across major versions
    qt_suffix = f"Qt{qtmajor}" if qtmajor > 5 else ""
    return f"{key}Header{qt_suffix}"


def saveHeader(widget: QHeaderView, key: str) -> None:
    aqt.mw.pm.profile[_header_key(key)] = widget.saveState()


def restoreHeader(widget: QHeaderView, key: str) -> None:
    if state := aqt.mw.pm.profile.get(_header_key(key)):
        widget.restoreState(state)


def save_is_checked(widget: QCheckBox, key: str) -> None:
    key += "IsChecked"
    aqt.mw.pm.profile[key] = widget.isChecked()


def restore_is_checked(widget: QCheckBox, key: str) -> None:
    key += "IsChecked"
    if aqt.mw.pm.profile.get(key) is not None:
        widget.setChecked(aqt.mw.pm.profile[key])


def save_combo_index_for_session(widget: QComboBox, key: str) -> None:
    textKey = f"{key}ComboActiveText"
    indexKey = f"{key}ComboActiveIndex"
    aqt.mw.pm.session[textKey] = widget.currentText()
    aqt.mw.pm.session[indexKey] = widget.currentIndex()


def restore_combo_index_for_session(
    widget: QComboBox, history: list[str], key: str
) -> None:
    textKey = f"{key}ComboActiveText"
    indexKey = f"{key}ComboActiveIndex"
    text = aqt.mw.pm.session.get(textKey)
    index = aqt.mw.pm.session.get(indexKey)
    if text is not None and index is not None:
        if index < len(history) and history[index] == text:
            widget.setCurrentIndex(index)


def save_combo_history(comboBox: QComboBox, history: list[str], name: str) -> str:
    name += "BoxHistory"
    text_input = comboBox.lineEdit().text()
    if text_input in history:
        history.remove(text_input)
    history.insert(0, text_input)
    history = history[:50]
    comboBox.clear()
    comboBox.addItems(history)
    aqt.mw.pm.session[name] = text_input
    aqt.mw.pm.profile[name] = history
    return text_input


def restore_combo_history(comboBox: QComboBox, name: str) -> list[str]:
    name += "BoxHistory"
    history = aqt.mw.pm.profile.get(name, [])
    comboBox.addItems([""] + history)
    if history:
        session_input = aqt.mw.pm.session.get(name)
        if session_input and session_input == history[0]:
            comboBox.lineEdit().setText(session_input)
            comboBox.lineEdit().selectAll()
    return history


def mungeQA(col: Collection, txt: str) -> str:
    print("mungeQA() deprecated; use mw.prepare_card_text_for_display()")
    txt = col.media.escape_media_filenames(txt)
    return txt


def openFolder(path: str) -> None:
    if is_win:
        subprocess.run(["explorer", f"file://{path}"], check=False)
    else:
        with no_bundled_libs():
            QDesktopServices.openUrl(QUrl(f"file://{path}"))


def shortcut(key: str) -> str:
    if is_mac:
        return re.sub("(?i)ctrl", "Command", key)
    return key


def maybeHideClose(bbox: QDialogButtonBox) -> None:
    if is_mac:
        b = bbox.button(QDialogButtonBox.StandardButton.Close)
        if b:
            bbox.removeButton(b)


def addCloseShortcut(widg: QDialog) -> None:
    if not is_mac:
        return
    shortcut = QShortcut(QKeySequence("Ctrl+W"), widg)
    qconnect(shortcut.activated, widg.reject)
    setattr(widg, "_closeShortcut", shortcut)


def downArrow() -> str:
    if is_win:
        return "▼"
    # windows 10 is lacking the smaller arrow on English installs
    return "▾"


def current_window() -> QWidget | None:
    if widget := QApplication.focusWidget():
        return widget.window()
    else:
        return None


def send_to_trash(path: Path) -> None:
    "Place file/folder in recyling bin, or delete permanently on failure."
    if not path.exists():
        return
    try:
        send2trash(path)
    except Exception as exc:
        # Linux users may not have a trash folder set up
        print("trash failure:", path, exc)
        if path.is_dir:
            shutil.rmtree(path)
        else:
            path.unlink()


# Tooltips
######################################################################

_tooltipTimer: QTimer | None = None
_tooltipLabel: QLabel | None = None


def tooltip(
    msg: str,
    period: int = 3000,
    parent: QWidget | None = None,
    x_offset: int = 0,
    y_offset: int = 100,
) -> None:
    global _tooltipTimer, _tooltipLabel

    class CustomLabel(QLabel):
        silentlyClose = True

        def mousePressEvent(self, evt: QMouseEvent) -> None:
            evt.accept()
            self.hide()

    closeTooltip()
    aw = parent or aqt.mw.app.activeWindow() or aqt.mw
    lab = CustomLabel(
        f"""<table cellpadding=10>
<tr>
<td>{msg}</td>
</tr>
</table>""",
        aw,
    )
    lab.setFrameStyle(QFrame.Shape.Panel)
    lab.setLineWidth(2)
    lab.setWindowFlags(Qt.WindowType.ToolTip)
    if not theme_manager.night_mode:
        p = QPalette()
        p.setColor(QPalette.ColorRole.Window, QColor("#feffc4"))
        p.setColor(QPalette.ColorRole.WindowText, QColor("#000000"))
        lab.setPalette(p)
    lab.move(aw.mapToGlobal(QPoint(0 + x_offset, aw.height() - y_offset)))
    lab.show()
    _tooltipTimer = aqt.mw.progress.timer(
        period, closeTooltip, False, requiresCollection=False, parent=aw
    )
    _tooltipLabel = lab


def closeTooltip() -> None:
    global _tooltipLabel, _tooltipTimer
    if _tooltipLabel:
        try:
            _tooltipLabel.deleteLater()
        except RuntimeError:
            # already deleted as parent window closed
            pass
        _tooltipLabel = None
    if _tooltipTimer:
        try:
            _tooltipTimer.deleteLater()
        except RuntimeError:
            pass
        _tooltipTimer = None


# true if invalid; print warning
def checkInvalidFilename(str: str, dirsep: bool = True) -> bool:
    bad = invalid_filename(str, dirsep)
    if bad:
        showWarning(tr.qt_misc_the_following_character_can_not_be(val=bad))
        return True
    return False


# Menus
######################################################################
# This code will be removed in the future, please don't rely on it.

MenuListChild = Union["SubMenu", QAction, "MenuItem", "MenuList"]


class MenuList:
    def __init__(self) -> None:
        traceback.print_stack(file=sys.stdout)
        print(
            "MenuList will be removed; please copy it into your add-on's code if you need it."
        )
        self.children: list[MenuListChild] = []

    def addItem(self, title: str, func: Callable) -> MenuItem:
        item = MenuItem(title, func)
        self.children.append(item)
        return item

    def addSeparator(self) -> None:
        self.children.append(None)

    def addMenu(self, title: str) -> SubMenu:
        submenu = SubMenu(title)
        self.children.append(submenu)
        return submenu

    def addChild(self, child: SubMenu | QAction | MenuList) -> None:
        self.children.append(child)

    def renderTo(self, qmenu: QMenu) -> None:
        for child in self.children:
            if child is None:
                qmenu.addSeparator()
            elif isinstance(child, QAction):
                qmenu.addAction(child)
            else:
                child.renderTo(qmenu)

    def popupOver(self, widget: QPushButton) -> None:
        qmenu = QMenu()
        self.renderTo(qmenu)
        qmenu.exec(widget.mapToGlobal(QPoint(0, 0)))


class SubMenu(MenuList):
    def __init__(self, title: str) -> None:
        super().__init__()
        self.title = title

    def renderTo(self, menu: QMenu) -> None:
        submenu = menu.addMenu(self.title)
        super().renderTo(submenu)


class MenuItem:
    def __init__(self, title: str, func: Callable) -> None:
        self.title = title
        self.func = func

    def renderTo(self, qmenu: QMenu) -> None:
        a = qmenu.addAction(self.title)
        qconnect(a.triggered, self.func)


def qtMenuShortcutWorkaround(qmenu: QMenu) -> None:
    for act in qmenu.actions():
        act.setShortcutVisibleInContextMenu(True)


######################################################################


def disallow_full_screen() -> bool:
    """Test for OpenGl on Windows, which is known to cause issues with full screen mode.
    On Qt6, the driver is not detectable, so check if it has been set explicitly.
    """
    from aqt import mw
    from aqt.profiles import VideoDriver

    return is_win and (
        (qtmajor == 5 and mw.pm.video_driver() == VideoDriver.OpenGL)
        or (
            qtmajor == 6
            and not os.environ.get("ANKI_SOFTWAREOPENGL")
            and os.environ.get("QT_OPENGL") != "software"
        )
    )


def add_ellipsis_to_action_label(*actions: QAction) -> None:
    """Pass actions to add '...' to their labels, indicating that more input is
    required before they can be performed.

    This approach is used so that the same fluent translations can be used on
    mobile, where the '...' convention does not exist.
    """
    for action in actions:
        action.setText(tr.actions_with_ellipsis(action=action.text()))


def supportText() -> str:
    import platform
    import time

    from aqt import mw

    if is_win:
        platname = f"Windows {platform.win32_ver()[0]}"
    elif is_mac:
        platname = f"Mac {platform.mac_ver()[0]}"
    else:
        platname = "Linux"

    def schedVer() -> str:
        try:
            if mw.col.v3_scheduler():
                return "3"
            else:
                return str(mw.col.sched_ver())
        except:
            return "?"

    lc = mw.pm.last_addon_update_check()
    lcfmt = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(lc))

    return """\
Anki {} Python {} Qt {} PyQt {}
Platform: {}
Flags: frz={} ao={} sv={}
Add-ons, last update check: {}
""".format(
        version_with_build(),
        platform.python_version(),
        QT_VERSION_STR,
        PYQT_VERSION_STR,
        platname,
        getattr(sys, "frozen", False),
        mw.addonManager.dirty,
        schedVer(),
        lcfmt,
    )


######################################################################

# adapted from version detection in qutebrowser
def opengl_vendor() -> str | None:
    if qtmajor != 5:
        return "unknown"
    old_context = QOpenGLContext.currentContext()
    old_surface = None if old_context is None else old_context.surface()

    surface = QOffscreenSurface()
    surface.create()

    ctx = QOpenGLContext()
    ok = ctx.create()
    if not ok:
        return None

    ok = ctx.makeCurrent(surface)
    if not ok:
        return None

    try:
        if ctx.isOpenGLES():
            # Can't use versionFunctions there
            return None

        vp = QOpenGLVersionProfile()  # type: ignore  # pylint: disable=undefined-variable
        vp.setVersion(2, 0)

        try:
            vf = ctx.versionFunctions(vp)  # type: ignore
        except ImportError as e:
            return None

        if vf is None:
            return None

        return vf.glGetString(vf.GL_VENDOR)
    finally:
        ctx.doneCurrent()
        if old_context and old_surface:
            old_context.makeCurrent(old_surface)


def gfxDriverIsBroken() -> bool:
    driver = opengl_vendor()
    return driver == "nouveau"


######################################################################


def startup_info() -> Any:
    "Use subprocess.Popen(startupinfo=...) to avoid opening a console window."
    if not sys.platform == "win32":
        return None
    si = subprocess.STARTUPINFO()  # pytype: disable=module-attr
    si.dwFlags |= subprocess.STARTF_USESHOWWINDOW  # pytype: disable=module-attr
    return si


def ensure_editor_saved(func: Callable) -> Callable:
    """Ensure the current editor's note is saved before running the wrapped function.

    Must be used on functions that may be invoked from a shortcut key while the
    editor has focus. For functions that can't be activated while the editor has
    focus, you don't need this.

    Will look for the editor as self.editor.
    """

    @wraps(func)
    def decorated(self: Any, *args: Any, **kwargs: Any) -> None:
        self.editor.call_after_note_saved(lambda: func(self, *args, **kwargs))

    return decorated


def skip_if_selection_is_empty(func: Callable) -> Callable:
    """Make the wrapped method a no-op and show a hint if the table selection is empty."""

    @wraps(func)
    def decorated(self: Any, *args: Any, **kwargs: Any) -> None:
        if self.table.len_selection() > 0:
            func(self, *args, **kwargs)
        else:
            tooltip(tr.browsing_no_selection())

    return decorated


def no_arg_trigger(func: Callable) -> Callable:
    """Tells Qt this function takes no args.

    This ensures PyQt doesn't attempt to pass a `toggled` arg
    into functions connected to a `triggered` signal.
    """

    return pyqtSlot()(func)  # type: ignore


def is_gesture_or_zoom_event(evt: QEvent) -> bool:
    """If the event is a gesture and/or will trigger zoom.

    Includes zoom by pinching, and Ctrl-scrolling on Win and Linux.
    """

    return isinstance(evt, QNativeGestureEvent) or (
        isinstance(evt, QWheelEvent)
        and not is_mac
        and KeyboardModifiersPressed().control
    )


class KeyboardModifiersPressed:
    "Util for type-safe checks of currently-pressed modifier keys."

    def __init__(self) -> None:
        from aqt import mw

        self._modifiers = mw.app.keyboardModifiers()

    @property
    def shift(self) -> bool:
        return bool(self._modifiers & Qt.KeyboardModifier.ShiftModifier)

    @property
    def control(self) -> bool:
        return bool(self._modifiers & Qt.KeyboardModifier.ControlModifier)

    @property
    def alt(self) -> bool:
        return bool(self._modifiers & Qt.KeyboardModifier.AltModifier)

    @property
    def meta(self) -> bool:
        return bool(self._modifiers & Qt.KeyboardModifier.MetaModifier)


# add-ons attempting to import isMac from this module :-(
_deprecated_names = DeprecatedNamesMixinForModule(globals())


@no_type_check
def __getattr__(name: str) -> Any:
    return _deprecated_names.__getattr__(name)
