# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import os
import re
import subprocess
import sys
from functools import wraps
from typing import (
    TYPE_CHECKING,
    Any,
    Callable,
    List,
    Literal,
    Optional,
    Sequence,
    Tuple,
    Union,
    cast,
)

from PyQt5.QtWidgets import (
    QAction,
    QDialog,
    QDialogButtonBox,
    QFileDialog,
    QHeaderView,
    QMenu,
    QPushButton,
    QSplitter,
    QWidget,
)

import aqt
from anki.collection import Collection, HelpPage
from anki.lang import TR, tr_legacyglobal  # pylint: disable=unused-import
from anki.utils import invalidFilename, isMac, isWin, noBundledLibs, versionWithBuild
from aqt.qt import *
from aqt.theme import theme_manager

if TYPE_CHECKING:
    TextFormat = Union[Literal["plain", "rich"]]


def aqt_data_folder() -> str:
    # running in place?
    dir = os.path.join(os.path.dirname(__file__), "data")
    if os.path.exists(dir):
        return dir
    # packaged install?
    if isMac:
        dir2 = os.path.join(sys.prefix, "..", "Resources", "aqt_data")
    else:
        dir2 = os.path.join(sys.prefix, "aqt_data")
    if os.path.exists(dir2):
        return dir2

    # should only happen when running unit tests
    print("warning, data folder not found")
    return "."


def locale_dir() -> str:
    return os.path.join(aqt_data_folder(), "locale")


# shortcut to access Fluent translations; set as
tr = tr_legacyglobal

HelpPageArgument = Union["HelpPage.V", str]


def openHelp(section: HelpPageArgument) -> None:
    if isinstance(section, str):
        link = tr.backend().help_page_link(page=HelpPage.INDEX) + section
    else:
        link = tr.backend().help_page_link(page=section)
    openLink(link)


def openLink(link: Union[str, QUrl]) -> None:
    tooltip(tr.qt_misc_loading(), period=1000)
    with noBundledLibs():
        QDesktopServices.openUrl(QUrl(link))


def showWarning(
    text: str,
    parent: Optional[QWidget] = None,
    help: HelpPageArgument = "",
    title: str = "Anki",
    textFormat: Optional[TextFormat] = None,
) -> int:
    "Show a small warning with an OK button."
    return showInfo(text, parent, help, "warning", title=title, textFormat=textFormat)


def showCritical(
    text: str,
    parent: Optional[QDialog] = None,
    help: str = "",
    title: str = "Anki",
    textFormat: Optional[TextFormat] = None,
) -> int:
    "Show a small critical error with an OK button."
    return showInfo(text, parent, help, "critical", title=title, textFormat=textFormat)


def showInfo(
    text: str,
    parent: Optional[QWidget] = None,
    help: HelpPageArgument = "",
    type: str = "info",
    title: str = "Anki",
    textFormat: Optional[TextFormat] = None,
    customBtns: Optional[List[QMessageBox.StandardButton]] = None,
) -> int:
    "Show a small info window with an OK button."
    parent_widget: QWidget
    if parent is None:
        parent_widget = aqt.mw.app.activeWindow() or aqt.mw
    else:
        parent_widget = parent
    if type == "warning":
        icon = QMessageBox.Warning
    elif type == "critical":
        icon = QMessageBox.Critical
    else:
        icon = QMessageBox.Information
    mb = QMessageBox(parent_widget)  #
    if textFormat == "plain":
        mb.setTextFormat(Qt.PlainText)
    elif textFormat == "rich":
        mb.setTextFormat(Qt.RichText)
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
        b = mb.addButton(QMessageBox.Ok)
        b.setDefault(True)
    if help:
        b = mb.addButton(QMessageBox.Help)
        qconnect(b.clicked, lambda: openHelp(help))
        b.setAutoDefault(False)
    return mb.exec_()


def showText(
    txt: str,
    parent: Optional[QWidget] = None,
    type: str = "text",
    run: bool = True,
    geomKey: Optional[str] = None,
    minWidth: int = 500,
    minHeight: int = 400,
    title: str = "Anki",
    copyBtn: bool = False,
    plain_text_edit: bool = False,
) -> Optional[Tuple[QDialog, QDialogButtonBox]]:
    if not parent:
        parent = aqt.mw.app.activeWindow() or aqt.mw
    diag = QDialog(parent)
    diag.setWindowTitle(title)
    disable_help_button(diag)
    layout = QVBoxLayout(diag)
    diag.setLayout(layout)
    text: Union[QPlainTextEdit, QTextBrowser]
    if plain_text_edit:
        # used by the importer
        text = QPlainTextEdit()
        text.setReadOnly(True)
        text.setWordWrapMode(QTextOption.NoWrap)
        text.setPlainText(txt)
    else:
        text = QTextBrowser()
        text.setOpenExternalLinks(True)
        if type == "text":
            text.setPlainText(txt)
        else:
            text.setHtml(txt)
    layout.addWidget(text)
    box = QDialogButtonBox(QDialogButtonBox.Close)
    layout.addWidget(box)
    if copyBtn:

        def onCopy() -> None:
            QApplication.clipboard().setText(text.toPlainText())

        btn = QPushButton(tr.qt_misc_copy_to_clipboard())
        qconnect(btn.clicked, onCopy)
        box.addButton(btn, QDialogButtonBox.ActionRole)

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
        diag.exec_()
        return None
    else:
        return diag, box


def askUser(
    text: str,
    parent: QWidget = None,
    help: HelpPageArgument = None,
    defaultno: bool = False,
    msgfunc: Optional[Callable] = None,
    title: str = "Anki",
) -> bool:
    "Show a yes/no question. Return true if yes."
    if not parent:
        parent = aqt.mw.app.activeWindow()
    if not msgfunc:
        msgfunc = QMessageBox.question
    sb = QMessageBox.Yes | QMessageBox.No
    if help:
        sb |= QMessageBox.Help
    while 1:
        if defaultno:
            default = QMessageBox.No
        else:
            default = QMessageBox.Yes
        r = msgfunc(parent, title, text, cast(QMessageBox.StandardButtons, sb), default)
        if r == QMessageBox.Help:
            openHelp(help)
        else:
            break
    return r == QMessageBox.Yes


class ButtonedDialog(QMessageBox):
    def __init__(
        self,
        text: str,
        buttons: List[str],
        parent: Optional[QWidget] = None,
        help: HelpPageArgument = None,
        title: str = "Anki",
    ):
        QMessageBox.__init__(self, parent)
        self._buttons: List[QPushButton] = []
        self.setWindowTitle(title)
        self.help = help
        self.setIcon(QMessageBox.Warning)
        self.setText(text)
        for b in buttons:
            self._buttons.append(self.addButton(b, QMessageBox.AcceptRole))
        if help:
            self.addButton(tr.actions_help(), QMessageBox.HelpRole)
            buttons.append(tr.actions_help())

    def run(self) -> str:
        self.exec_()
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
    buttons: List[str],
    parent: Optional[QWidget] = None,
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
        parent: Optional[QWidget],
        question: str,
        help: HelpPageArgument = None,
        edit: Optional[QLineEdit] = None,
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
        buts = QDialogButtonBox.Ok | QDialogButtonBox.Cancel
        if help:
            buts |= QDialogButtonBox.Help
        b = QDialogButtonBox(buts)  # type: ignore
        v.addWidget(b)
        self.setLayout(v)
        qconnect(b.button(QDialogButtonBox.Ok).clicked, self.accept)
        qconnect(b.button(QDialogButtonBox.Cancel).clicked, self.reject)
        if help:
            qconnect(b.button(QDialogButtonBox.Help).clicked, self.helpRequested)

    def accept(self) -> None:
        return QDialog.accept(self)

    def reject(self) -> None:
        return QDialog.reject(self)

    def helpRequested(self) -> None:
        openHelp(self.help)


def getText(
    prompt: str,
    parent: Optional[QWidget] = None,
    help: HelpPageArgument = None,
    edit: Optional[QLineEdit] = None,
    default: str = "",
    title: str = "Anki",
    geomKey: Optional[str] = None,
    **kwargs: Any,
) -> Tuple[str, int]:
    "Returns (string, succeeded)."
    if not parent:
        parent = aqt.mw.app.activeWindow() or aqt.mw
    d = GetTextDialog(
        parent, prompt, help=help, edit=edit, default=default, title=title, **kwargs
    )
    d.setWindowModality(Qt.WindowModal)
    if geomKey:
        restoreGeom(d, geomKey)
    ret = d.exec_()
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
    prompt: str, choices: List[str], startrow: int = 0, parent: Any = None
) -> int:
    if not parent:
        parent = aqt.mw.app.activeWindow()
    d = QDialog(parent)
    disable_help_button(d)
    d.setWindowModality(Qt.WindowModal)
    l = QVBoxLayout()
    d.setLayout(l)
    t = QLabel(prompt)
    l.addWidget(t)
    c = QListWidget()
    c.addItems(choices)
    c.setCurrentRow(startrow)
    l.addWidget(c)
    bb = QDialogButtonBox(QDialogButtonBox.Ok)
    qconnect(bb.accepted, d.accept)
    l.addWidget(bb)
    d.exec_()
    return c.currentRow()


def getTag(
    parent: QWidget, deck: Collection, question: str, **kwargs: Any
) -> Tuple[str, int]:
    from aqt.tagedit import TagEdit

    te = TagEdit(parent)
    te.setCol(deck)
    ret = getText(question, parent, edit=te, geomKey="getTag", **kwargs)
    te.hideCompleter()
    return ret


def disable_help_button(widget: QWidget) -> None:
    "Disable the help button in the window titlebar."
    flags_int = int(widget.windowFlags()) & ~Qt.WindowContextHelpButtonHint
    flags = Qt.WindowFlags(flags_int)  # type: ignore
    widget.setWindowFlags(flags)


# File handling
######################################################################


def getFile(
    parent: QWidget,
    title: str,
    # single file returned unless multi=True
    cb: Optional[Callable[[Union[str, Sequence[str]]], None]],
    filter: str = "*.*",
    dir: Optional[str] = None,
    key: Optional[str] = None,
    multi: bool = False,  # controls whether a single or multiple files is returned
) -> Optional[Union[Sequence[str], str]]:
    "Ask the user for a file."
    assert not dir or not key
    if not dir:
        dirkey = f"{key}Directory"
        dir = aqt.mw.pm.profile.get(dirkey, "")
    else:
        dirkey = None
    d = QFileDialog(parent)
    mode = QFileDialog.ExistingFiles if multi else QFileDialog.ExistingFile
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
    d.exec_()
    if key:
        saveState(d, key)
    return ret[0] if ret else None


def getSaveFile(
    parent: QDialog,
    title: str,
    dir_description: str,
    key: str,
    ext: str,
    fname: Optional[str] = None,
) -> str:
    """Ask the user for a file to save. Use DIR_DESCRIPTION as config
    variable. The file dialog will default to open with FNAME."""
    config_key = f"{dir_description}Directory"

    defaultPath = QStandardPaths.writableLocation(QStandardPaths.DocumentsLocation)
    base = aqt.mw.pm.profile.get(config_key, defaultPath)
    path = os.path.join(base, fname)
    file = QFileDialog.getSaveFileName(
        parent,
        title,
        path,
        f"{key} (*{ext})",
        options=QFileDialog.DontConfirmOverwrite,
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
    key += "Geom"
    if isMac and int(widget.windowState()) & Qt.WindowFullScreen:
        geom = None
    else:
        geom = widget.saveGeometry()
    aqt.mw.pm.profile[key] = geom


def restoreGeom(
    widget: QWidget, key: str, offset: Optional[int] = None, adjustSize: bool = False
) -> None:
    key += "Geom"
    if aqt.mw.pm.profile.get(key):
        widget.restoreGeometry(aqt.mw.pm.profile[key])
        if isMac and offset:
            if qtminor > 6:
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
        aqt.mw.progress.timer(50, lambda: ensureWidgetInScreenBoundaries(widget), False)
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


def saveState(widget: Union[QFileDialog, QMainWindow], key: str) -> None:
    key += "State"
    aqt.mw.pm.profile[key] = widget.saveState()


def restoreState(widget: Union[QFileDialog, QMainWindow], key: str) -> None:
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


def saveHeader(widget: QHeaderView, key: str) -> None:
    key += "Header"
    aqt.mw.pm.profile[key] = widget.saveState()


def restoreHeader(widget: QHeaderView, key: str) -> None:
    key += "Header"
    if aqt.mw.pm.profile.get(key):
        widget.restoreState(aqt.mw.pm.profile[key])


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
    widget: QComboBox, history: List[str], key: str
) -> None:
    textKey = f"{key}ComboActiveText"
    indexKey = f"{key}ComboActiveIndex"
    text = aqt.mw.pm.session.get(textKey)
    index = aqt.mw.pm.session.get(indexKey)
    if text is not None and index is not None:
        if index < len(history) and history[index] == text:
            widget.setCurrentIndex(index)


def save_combo_history(comboBox: QComboBox, history: List[str], name: str) -> str:
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


def restore_combo_history(comboBox: QComboBox, name: str) -> List[str]:
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
    if isWin:
        subprocess.run(["explorer", f"file://{path}"], check=False)
    else:
        with noBundledLibs():
            QDesktopServices.openUrl(QUrl(f"file://{path}"))


def shortcut(key: str) -> str:
    if isMac:
        return re.sub("(?i)ctrl", "Command", key)
    return key


def maybeHideClose(bbox: QDialogButtonBox) -> None:
    if isMac:
        b = bbox.button(QDialogButtonBox.Close)
        if b:
            bbox.removeButton(b)


def addCloseShortcut(widg: QDialog) -> None:
    if not isMac:
        return
    shortcut = QShortcut(QKeySequence("Ctrl+W"), widg)
    qconnect(shortcut.activated, widg.reject)
    setattr(widg, "_closeShortcut", shortcut)


def downArrow() -> str:
    if isWin:
        return "▼"
    # windows 10 is lacking the smaller arrow on English installs
    return "▾"


def current_window() -> Optional[QWidget]:
    if widget := QApplication.focusWidget():
        return widget.window()
    else:
        return None


# Tooltips
######################################################################

_tooltipTimer: Optional[QTimer] = None
_tooltipLabel: Optional[QLabel] = None


def tooltip(
    msg: str,
    period: int = 3000,
    parent: Optional[QWidget] = None,
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
    lab.setFrameStyle(QFrame.Panel)
    lab.setLineWidth(2)
    lab.setWindowFlags(Qt.ToolTip)
    if not theme_manager.night_mode:
        p = QPalette()
        p.setColor(QPalette.Window, QColor("#feffc4"))
        p.setColor(QPalette.WindowText, QColor("#000000"))
        lab.setPalette(p)
    lab.move(aw.mapToGlobal(QPoint(0 + x_offset, aw.height() - y_offset)))
    lab.show()
    _tooltipTimer = aqt.mw.progress.timer(
        period, closeTooltip, False, requiresCollection=False
    )
    _tooltipLabel = lab


def closeTooltip() -> None:
    global _tooltipLabel, _tooltipTimer
    if _tooltipLabel:
        try:
            _tooltipLabel.deleteLater()
        except:
            # already deleted as parent window closed
            pass
        _tooltipLabel = None
    if _tooltipTimer:
        _tooltipTimer.stop()
        _tooltipTimer = None


# true if invalid; print warning
def checkInvalidFilename(str: str, dirsep: bool = True) -> bool:
    bad = invalidFilename(str, dirsep)
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
        self.children: List[MenuListChild] = []

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

    def addChild(self, child: Union[SubMenu, QAction, MenuList]) -> None:
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
        qmenu.exec_(widget.mapToGlobal(QPoint(0, 0)))


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
    if qtminor < 10:
        return
    for act in qmenu.actions():
        act.setShortcutVisibleInContextMenu(True)


######################################################################


def supportText() -> str:
    import platform
    import time

    from aqt import mw

    if isWin:
        platname = f"Windows {platform.win32_ver()[0]}"
    elif isMac:
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
        versionWithBuild(),
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
def opengl_vendor() -> Optional[str]:
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

        vp = QOpenGLVersionProfile()
        vp.setVersion(2, 0)

        try:
            vf = ctx.versionFunctions(vp)
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


class KeyboardModifiersPressed:
    "Util for type-safe checks of currently-pressed modifier keys."

    def __init__(self) -> None:
        from aqt import mw

        self._modifiers = int(mw.app.keyboardModifiers())

    @property
    def shift(self) -> bool:
        return bool(self._modifiers & Qt.ShiftModifier)

    @property
    def control(self) -> bool:
        return bool(self._modifiers & Qt.ControlModifier)

    @property
    def alt(self) -> bool:
        return bool(self._modifiers & Qt.AltModifier)
