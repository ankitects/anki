# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# type: ignore
# pylint: disable=unused-import

"""
Patches and aliases that provide a PyQt5 → PyQt6 compatibility shim for add-ons
"""

import sys

import PyQt6.QtCore
import PyQt6.QtGui
import PyQt6.QtNetwork
import PyQt6.QtWebChannel
import PyQt6.QtWebEngineCore
import PyQt6.QtWebEngineWidgets
import PyQt6.QtWidgets

from anki._legacy import print_deprecation_warning

# Globally alias PyQt5 to PyQt6
# #########################################################################

sys.modules["PyQt5"] = PyQt6
# Need to alias QtCore explicitly as sip otherwise complains about repeat registration
sys.modules["PyQt5.QtCore"] = PyQt6.QtCore
# Need to alias QtWidgets and QtGui explicitly to facilitate patches
sys.modules["PyQt5.QtGui"] = PyQt6.QtGui
sys.modules["PyQt5.QtWidgets"] = PyQt6.QtWidgets
# Needed to maintain import order between QtWebEngineWidgets and QCoreApplication:
sys.modules["PyQt5.QtWebEngineWidgets"] = PyQt6.QtWebEngineWidgets
# Register other aliased top-level Qt modules just to be safe:
sys.modules["PyQt5.QtWebEngineCore"] = PyQt6.QtWebEngineCore
sys.modules["PyQt5.QtWebChannel"] = PyQt6.QtWebChannel
sys.modules["PyQt5.QtNetwork"] = PyQt6.QtNetwork
# Alias sip
sys.modules["sip"] = PyQt6.sip

# Alias classes shifted between QtWidgets and QtGui
##########################################################################

PyQt6.QtWidgets.QAction = PyQt6.QtGui.QAction
PyQt6.QtWidgets.QActionGroup = PyQt6.QtGui.QActionGroup
PyQt6.QtWidgets.QShortcut = PyQt6.QtGui.QShortcut

# Alias classes shifted between QtWebEngineWidgets and QtWebEngineCore
##########################################################################

PyQt6.QtWebEngineWidgets.QWebEnginePage = PyQt6.QtWebEngineCore.QWebEnginePage
PyQt6.QtWebEngineWidgets.QWebEngineHistory = PyQt6.QtWebEngineCore.QWebEngineHistory
PyQt6.QtWebEngineWidgets.QWebEngineProfile = PyQt6.QtWebEngineCore.QWebEngineProfile
PyQt6.QtWebEngineWidgets.QWebEngineScript = PyQt6.QtWebEngineCore.QWebEngineScript
PyQt6.QtWebEngineWidgets.QWebEngineScriptCollection = (
    PyQt6.QtWebEngineCore.QWebEngineScriptCollection
)
PyQt6.QtWebEngineWidgets.QWebEngineClientCertificateSelection = (
    PyQt6.QtWebEngineCore.QWebEngineClientCertificateSelection
)
PyQt6.QtWebEngineWidgets.QWebEngineSettings = PyQt6.QtWebEngineCore.QWebEngineSettings
PyQt6.QtWebEngineWidgets.QWebEngineFullScreenRequest = (
    PyQt6.QtWebEngineCore.QWebEngineFullScreenRequest
)
PyQt6.QtWebEngineWidgets.QWebEngineContextMenuData = (
    PyQt6.QtWebEngineCore.QWebEngineContextMenuRequest
)
PyQt6.QtWebEngineWidgets.QWebEngineDownloadItem = (
    PyQt6.QtWebEngineCore.QWebEngineDownloadRequest
)

# Aliases for other miscellaneous class changes
##########################################################################

PyQt6.QtCore.QRegExp = PyQt6.QtCore.QRegularExpression

# Restore QWebEnginePage.view()
# ########################################################################

from PyQt6.QtWebEngineCore import QWebEnginePage
from PyQt6.QtWebEngineWidgets import QWebEngineView


def qwebenginepage_view(page: QWebEnginePage) -> QWebEnginePage:
    print_deprecation_warning(
        "'QWebEnginePage.view()' is deprecated. "
        "Please use 'QWebEngineView.forPage(page)'"
    )
    return QWebEngineView.forPage(page)


PyQt6.QtWebEngineCore.QWebEnginePage.view = qwebenginepage_view

# Alias removed exec_ methods to exec
# ########################################################################

from PyQt6.QtCore import QCoreApplication, QEventLoop, QThread
from PyQt6.QtGui import QDrag, QGuiApplication
from PyQt6.QtWidgets import QApplication, QDialog, QMenu


# This helper function is needed as aliasing exec_ to exec directly will cause
# an unbound method error, even when wrapped with types.MethodType
def qt_exec_(object, *args, **kwargs):
    class_name = object.__class__.__name__
    print_deprecation_warning(
        f"'{class_name}.exec_()' is deprecated. Please use '{class_name}.exec()'"
    )
    return object.exec(*args, **kwargs)


QCoreApplication.exec_ = qt_exec_
QEventLoop.exec_ = qt_exec_
QThread.exec_ = qt_exec_
QDrag.exec_ = qt_exec_
QGuiApplication.exec_ = qt_exec_
QApplication.exec_ = qt_exec_
QDialog.exec_ = qt_exec_
QMenu.exec_ = qt_exec_

# Graciously handle removed Qt resource system
# ########################################################################

# Given that add-ons mostly use the Qt resource system to equip UI elements with
# icons – which oftentimes are not essential to the core UX –, printing a warning
# instead of preventing the add-on from loading seems appropriate.

def qt_resource_system_call(*args, **kwargs):
    print_deprecation_warning(
        "The Qt resource system no longer works on PyQt6. "
        "Please switch to a different solution."
    )

PyQt6.QtCore.qRegisterResourceData = qt_resource_system_call
PyQt6.QtCore.qUnregisterResourceData = qt_resource_system_call

# Patch unscoped enums back in, aliasing them to scoped enums
# ########################################################################

from PyQt6.QtCore import *
from PyQt6.QtDBus import QDBus
from PyQt6.QtGui import *
from PyQt6.QtWebEngineCore import *
from PyQt6.QtWidgets import *

# This is the subset of enums used in all public Anki add-ons as of 2021-10-19.
# Please note that this list is likely to be incomplete as the process used to
# find them probably missed dynamically constructed enums.
# Also, as mostly only public Anki add-ons were taken into consideration,
# some enums in other add-ons might not be included. In those cases please
# consider filing a PR to extend the assignments below.

# Important: These patches are not meant to provide compatibility for all
# add-ons going forward, but simply to maintain support with already
# existing add-ons. Add-on authors should take heed to use scoped enums
# in any future code changes.

# QtCore

QEvent.ChildAdded = QEvent.Type.ChildAdded
QEvent.ContextMenu = QEvent.Type.ContextMenu
QEvent.FocusIn = QEvent.Type.FocusIn
QEvent.HoverEnter = QEvent.Type.HoverEnter
QEvent.HoverMove = QEvent.Type.HoverMove
QEvent.KeyPress = QEvent.Type.KeyPress
QEvent.KeyRelease = QEvent.Type.KeyRelease
QEvent.MouseButtonDblClick = QEvent.Type.MouseButtonDblClick
QEvent.MouseButtonPress = QEvent.Type.MouseButtonPress
QEvent.MouseButtonRelease = QEvent.Type.MouseButtonRelease
QEvent.MouseMove = QEvent.Type.MouseMove
QEvent.Move = QEvent.Type.Move
QEvent.Paint = QEvent.Type.Paint
QEvent.Resize = QEvent.Type.Resize
QEvent.Shortcut = QEvent.Type.Shortcut
QEvent.ShortcutOverride = QEvent.Type.ShortcutOverride
QEvent.Wheel = QEvent.Type.Wheel
QEvent.WindowActivate = QEvent.Type.WindowActivate
QEvent.WindowDeactivate = QEvent.Type.WindowDeactivate
QEvent.WindowStateChange = QEvent.Type.WindowStateChange
QEventLoop.AllEvents = QEventLoop.ProcessEventsFlag.AllEvents
QEventLoop.ExcludeUserInputEvents = QEventLoop.ProcessEventsFlag.ExcludeUserInputEvents
QIODevice.ReadOnly = QIODevice.OpenModeFlag.ReadOnly
QIODevice.ReadWrite = QIODevice.OpenModeFlag.ReadWrite
QItemSelectionModel.Current = QItemSelectionModel.SelectionFlag.Current
QItemSelectionModel.Rows = QItemSelectionModel.SelectionFlag.Rows
QItemSelectionModel.Select = QItemSelectionModel.SelectionFlag.Select
QItemSelectionModel.SelectCurrent = QItemSelectionModel.SelectionFlag.SelectCurrent
QLocale.UnitedStates = QLocale.Country.UnitedStates
QLocale.English = QLocale.Language.English
QVariant.LongLong = QMetaType.Type.LongLong
QMetaType.QString = QMetaType.Type.QString
QMetaType.QStringList = QMetaType.Type.QStringList
QVariant.UInt = QMetaType.Type.UInt
QProcess.StandardOutput = QProcess.ProcessChannel.StandardOutput
QProcess.NotRunning = QProcess.ProcessState.NotRunning
QStandardPaths.DesktopLocation = QStandardPaths.StandardLocation.DesktopLocation
QStandardPaths.DocumentsLocation = QStandardPaths.StandardLocation.DocumentsLocation
QStandardPaths.DownloadLocation = QStandardPaths.StandardLocation.DownloadLocation
QStandardPaths.GenericDataLocation = QStandardPaths.StandardLocation.GenericDataLocation
Qt.AlignBottom = Qt.AlignmentFlag.AlignBottom
Qt.AlignCenter = Qt.AlignmentFlag.AlignCenter
Qt.AlignHCenter = Qt.AlignmentFlag.AlignHCenter
Qt.AlignLeading = Qt.AlignmentFlag.AlignLeading
Qt.AlignLeft = Qt.AlignmentFlag.AlignLeft
Qt.AlignRight = Qt.AlignmentFlag.AlignRight
Qt.AlignTop = Qt.AlignmentFlag.AlignTop
Qt.AlignTrailing = Qt.AlignmentFlag.AlignTrailing
Qt.AlignVCenter = Qt.AlignmentFlag.AlignVCenter
Qt.DownArrow = Qt.ArrowType.DownArrow
Qt.IgnoreAspectRatio = Qt.AspectRatioMode.IgnoreAspectRatio
Qt.KeepAspectRatio = Qt.AspectRatioMode.KeepAspectRatio
Qt.KeepAspectRatioByExpanding = Qt.AspectRatioMode.KeepAspectRatioByExpanding
Qt.SolidPattern = Qt.BrushStyle.SolidPattern
Qt.CaseInsensitive = Qt.CaseSensitivity.CaseInsensitive
Qt.Checked = Qt.CheckState.Checked
Qt.PartiallyChecked = Qt.CheckState.PartiallyChecked
Qt.Unchecked = Qt.CheckState.Unchecked
Qt.BlockingQueuedConnection = Qt.ConnectionType.BlockingQueuedConnection
Qt.ActionsContextMenu = Qt.ContextMenuPolicy.ActionsContextMenu
Qt.CustomContextMenu = Qt.ContextMenuPolicy.CustomContextMenu
Qt.NoContextMenu = Qt.ContextMenuPolicy.NoContextMenu
Qt.PreventContextMenu = Qt.ContextMenuPolicy.PreventContextMenu
Qt.ArrowCursor = Qt.CursorShape.ArrowCursor
Qt.BlankCursor = Qt.CursorShape.BlankCursor
Qt.CrossCursor = Qt.CursorShape.CrossCursor
Qt.PointingHandCursor = Qt.CursorShape.PointingHandCursor
Qt.SizeAllCursor = Qt.CursorShape.SizeAllCursor
Qt.SizeBDiagCursor = Qt.CursorShape.SizeBDiagCursor
Qt.SizeFDiagCursor = Qt.CursorShape.SizeFDiagCursor
Qt.SizeHorCursor = Qt.CursorShape.SizeHorCursor
Qt.SizeVerCursor = Qt.CursorShape.SizeVerCursor
Qt.UpArrow = Qt.CursorShape.UpArrowCursor
Qt.WaitCursor = Qt.CursorShape.WaitCursor
Qt.ISODate = Qt.DateFormat.ISODate
Qt.ISODateWithMs = Qt.DateFormat.ISODateWithMs
Qt.Sunday = Qt.DayOfWeek.Sunday
Qt.BottomDockWidgetArea = Qt.DockWidgetArea.BottomDockWidgetArea
Qt.LeftDockWidgetArea = Qt.DockWidgetArea.LeftDockWidgetArea
Qt.RightDockWidgetArea = Qt.DockWidgetArea.RightDockWidgetArea
Qt.TopDockWidgetArea = Qt.DockWidgetArea.TopDockWidgetArea
Qt.MoveAction = Qt.DropAction.CopyAction
Qt.IgnoreAction = Qt.DropAction.MoveAction
Qt.FindChildrenRecursively = Qt.FindChildOption.FindChildrenRecursively
Qt.ClickFocus = Qt.FocusPolicy.ClickFocus
Qt.ClickFocus = Qt.FocusPolicy.ClickFocus
Qt.NoFocus = Qt.FocusPolicy.NoFocus
Qt.TabFocus = Qt.FocusPolicy.TabFocus
Qt.WheelFocus = Qt.FocusPolicy.WheelFocus
Qt.MouseFocusReason = Qt.FocusReason.MouseFocusReason
Qt.Popup = Qt.FocusReason.PopupFocusReason
Qt.black = Qt.GlobalColor.black
Qt.blue = Qt.GlobalColor.blue
Qt.darkBlue = Qt.GlobalColor.darkBlue
Qt.darkGray = Qt.GlobalColor.darkGray
Qt.darkGreen = Qt.GlobalColor.darkGreen
Qt.darkRed = Qt.GlobalColor.darkRed
Qt.gray = Qt.GlobalColor.gray
Qt.green = Qt.GlobalColor.green
Qt.lightGray = Qt.GlobalColor.lightGray
Qt.red = Qt.GlobalColor.red
Qt.transparent = Qt.GlobalColor.transparent
Qt.white = Qt.GlobalColor.white
Qt.yellow = Qt.GlobalColor.yellow
Qt.Round = Qt.HighDpiScaleFactorRoundingPolicy.Round
Qt.AutoColor = Qt.ImageConversionFlag.AutoColor
Qt.ThresholdDither = Qt.ImageConversionFlag.ThresholdDither
Qt.ImhHiddenText = Qt.InputMethodHint.ImhHiddenText
Qt.ImhNone = Qt.InputMethodHint.ImhNone
Qt.BackgroundRole = Qt.ItemDataRole.BackgroundRole
Qt.CheckStateRole = Qt.ItemDataRole.CheckStateRole
Qt.DecorationRole = Qt.ItemDataRole.DecorationRole
Qt.DisplayRole = Qt.ItemDataRole.DisplayRole
Qt.EditRole = Qt.ItemDataRole.EditRole
Qt.FontRole = Qt.ItemDataRole.FontRole
Qt.ForegroundRole = Qt.ItemDataRole.ForegroundRole
Qt.TextAlignmentRole = Qt.ItemDataRole.TextAlignmentRole
Qt.ToolTipRole = Qt.ItemDataRole.ToolTipRole
Qt.UserRole = Qt.ItemDataRole.UserRole
Qt.ItemIsDragEnabled = Qt.ItemFlag.ItemIsDragEnabled
Qt.ItemIsDropEnabled = Qt.ItemFlag.ItemIsDropEnabled
Qt.ItemIsEditable = Qt.ItemFlag.ItemIsEditable
Qt.ItemIsEnabled = Qt.ItemFlag.ItemIsEnabled
Qt.ItemIsSelectable = Qt.ItemFlag.ItemIsSelectable
Qt.ItemIsUserCheckable = Qt.ItemFlag.ItemIsUserCheckable
Qt.NoItemFlags = Qt.ItemFlag.NoItemFlags
Qt.AltModifier = Qt.KeyboardModifier.AltModifier
Qt.ControlModifier = Qt.KeyboardModifier.ControlModifier
Qt.MetaModifier = Qt.KeyboardModifier.MetaModifier
Qt.NoModifier = Qt.KeyboardModifier.NoModifier
Qt.ShiftModifier = Qt.KeyboardModifier.ShiftModifier
Qt.LeftToRight = Qt.LayoutDirection.LeftToRight
Qt.RightToLeft = Qt.LayoutDirection.RightToLeft
Qt.MatchContains = Qt.MatchFlag.MatchContains
Qt.MatchEndsWith = Qt.MatchFlag.MatchEndsWith
Qt.MatchFixedString = Qt.MatchFlag.MatchFixedString
Qt.MatchRecursive = Qt.MatchFlag.MatchRecursive
Qt.MatchRegExp = Qt.MatchFlag.MatchRegularExpression
Qt.ALT = Qt.Modifier.ALT
Qt.CTRL = Qt.Modifier.CTRL
Qt.META = Qt.Modifier.META
Qt.SHIFT = Qt.Modifier.SHIFT
Qt.BackButton = Qt.MouseButton.BackButton
Qt.ForwardButton = Qt.MouseButton.ForwardButton
Qt.LeftButton = Qt.MouseButton.LeftButton
Qt.MidButton = Qt.MouseButton.MiddleButton
Qt.MiddleButton = Qt.MouseButton.MiddleButton
Qt.RightButton = Qt.MouseButton.RightButton
Qt.XButton1 = Qt.MouseButton.XButton1
Qt.XButton2 = Qt.MouseButton.XButton2
Qt.Horizontal = Qt.Orientation.Horizontal
Qt.Vertical = Qt.Orientation.Vertical
Qt.RoundCap = Qt.PenCapStyle.RoundCap
Qt.RoundJoin = Qt.PenJoinStyle.RoundJoin
Qt.NoPen = Qt.PenStyle.NoPen
Qt.SolidLine = Qt.PenStyle.SolidLine
Qt.ScrollBarAlwaysOff = Qt.ScrollBarPolicy.ScrollBarAlwaysOff
Qt.ScrollBarAlwaysOn = Qt.ScrollBarPolicy.ScrollBarAlwaysOn
Qt.ScrollBarAsNeeded = Qt.ScrollBarPolicy.ScrollBarAsNeeded
Qt.ApplicationShortcut = Qt.ShortcutContext.ApplicationShortcut
Qt.DescendingOrder = Qt.SortOrder.DescendingOrder
Qt.ElideLeft = Qt.TextElideMode.ElideLeft
Qt.ElideNone = Qt.TextElideMode.ElideNone
Qt.ElideRight = Qt.TextElideMode.ElideRight
Qt.TextDontPrint = Qt.TextFlag.TextDontPrint
Qt.AutoText = Qt.TextFormat.AutoText
Qt.PlainText = Qt.TextFormat.PlainText
Qt.RichText = Qt.TextFormat.RichText
Qt.LinksAccessibleByKeyboard = Qt.TextInteractionFlag.LinksAccessibleByKeyboard
Qt.LinksAccessibleByMouse = Qt.TextInteractionFlag.LinksAccessibleByMouse
Qt.NoTextInteraction = Qt.TextInteractionFlag.NoTextInteraction
Qt.TextBrowserInteraction = Qt.TextInteractionFlag.TextBrowserInteraction
Qt.TextSelectableByMouse = Qt.TextInteractionFlag.TextSelectableByMouse
Qt.LeftToolBarArea = Qt.ToolBarArea.LeftToolBarArea
Qt.RightToolBarArea = Qt.ToolBarArea.RightToolBarArea
Qt.TopToolBarArea = Qt.ToolBarArea.TopToolBarArea
Qt.ToolButtonIconOnly = Qt.ToolButtonStyle.ToolButtonIconOnly
Qt.FastTransformation = Qt.TransformationMode.FastTransformation
Qt.SmoothTransformation = Qt.TransformationMode.SmoothTransformation
Qt.WA_DeleteOnClose = Qt.WidgetAttribute.WA_DeleteOnClose
Qt.WA_Hover = Qt.WidgetAttribute.WA_Hover
Qt.WA_InputMethodEnabled = Qt.WidgetAttribute.WA_InputMethodEnabled
Qt.WA_NativeWindow = Qt.WidgetAttribute.WA_NativeWindow
Qt.WA_TranslucentBackground = Qt.WidgetAttribute.WA_TranslucentBackground
Qt.ApplicationModal = Qt.WindowModality.ApplicationModal
Qt.NonModal = Qt.WindowModality.NonModal
Qt.WindowModal = Qt.WindowModality.WindowModal
Qt.WindowActive = Qt.WindowState.WindowActive
Qt.WindowFullScreen = Qt.WindowState.WindowFullScreen
Qt.WindowMaximized = Qt.WindowState.WindowMaximized
Qt.WindowMinimized = Qt.WindowState.WindowMinimized
Qt.WindowNoState = Qt.WindowState.WindowNoState
Qt.CustomizeWindowHint = Qt.WindowType.CustomizeWindowHint
Qt.Dialog = Qt.WindowType.Dialog
Qt.FramelessWindowHint = Qt.WindowType.FramelessWindowHint
Qt.MSWindowsFixedSizeDialogHint = Qt.WindowType.MSWindowsFixedSizeDialogHint
Qt.NoDropShadowWindowHint = Qt.WindowType.NoDropShadowWindowHint
Qt.Tool = Qt.WindowType.Tool
Qt.ToolTip = Qt.WindowType.ToolTip
Qt.Window = Qt.WindowType.Window
Qt.WindowCloseButtonHint = Qt.WindowType.WindowCloseButtonHint
Qt.WindowMaximizeButtonHint = Qt.WindowType.WindowMaximizeButtonHint
Qt.WindowMinMaxButtonsHint = Qt.WindowType.WindowMinMaxButtonsHint
Qt.WindowMinimizeButtonHint = Qt.WindowType.WindowMinimizeButtonHint
Qt.WindowStaysOnTopHint = Qt.WindowType.WindowStaysOnTopHint
Qt.WindowSystemMenuHint = Qt.WindowType.WindowSystemMenuHint
Qt.WindowTitleHint = Qt.WindowType.WindowTitleHint


# QtCore Key Namespace

# not all if these are in active use by add-ons, but as some add-ons allow
# customizing hotkeys via Qt.Key assignments, it makes sense to incldue the
# full namespace
Qt.Key_Escape = Qt.Key.Key_Escape
Qt.Key_Tab = Qt.Key.Key_Tab
Qt.Key_Backtab = Qt.Key.Key_Backtab
Qt.Key_Backspace = Qt.Key.Key_Backspace
Qt.Key_Return = Qt.Key.Key_Return
Qt.Key_Enter = Qt.Key.Key_Enter
Qt.Key_Insert = Qt.Key.Key_Insert
Qt.Key_Delete = Qt.Key.Key_Delete
Qt.Key_Pause = Qt.Key.Key_Pause
Qt.Key_Print = Qt.Key.Key_Print
Qt.Key_SysReq = Qt.Key.Key_SysReq
Qt.Key_Clear = Qt.Key.Key_Clear
Qt.Key_Home = Qt.Key.Key_Home
Qt.Key_End = Qt.Key.Key_End
Qt.Key_Left = Qt.Key.Key_Left
Qt.Key_Up = Qt.Key.Key_Up
Qt.Key_Right = Qt.Key.Key_Right
Qt.Key_Down = Qt.Key.Key_Down
Qt.Key_PageUp = Qt.Key.Key_PageUp
Qt.Key_PageDown = Qt.Key.Key_PageDown
Qt.Key_Shift = Qt.Key.Key_Shift
Qt.Key_Control = Qt.Key.Key_Control
Qt.Key_Meta = Qt.Key.Key_Meta
Qt.Key_Alt = Qt.Key.Key_Alt
Qt.Key_CapsLock = Qt.Key.Key_CapsLock
Qt.Key_NumLock = Qt.Key.Key_NumLock
Qt.Key_ScrollLock = Qt.Key.Key_ScrollLock
Qt.Key_F1 = Qt.Key.Key_F1
Qt.Key_F2 = Qt.Key.Key_F2
Qt.Key_F3 = Qt.Key.Key_F3
Qt.Key_F4 = Qt.Key.Key_F4
Qt.Key_F5 = Qt.Key.Key_F5
Qt.Key_F6 = Qt.Key.Key_F6
Qt.Key_F7 = Qt.Key.Key_F7
Qt.Key_F8 = Qt.Key.Key_F8
Qt.Key_F9 = Qt.Key.Key_F9
Qt.Key_F10 = Qt.Key.Key_F10
Qt.Key_F11 = Qt.Key.Key_F11
Qt.Key_F12 = Qt.Key.Key_F12
Qt.Key_F13 = Qt.Key.Key_F13
Qt.Key_F14 = Qt.Key.Key_F14
Qt.Key_F15 = Qt.Key.Key_F15
Qt.Key_F16 = Qt.Key.Key_F16
Qt.Key_F17 = Qt.Key.Key_F17
Qt.Key_F18 = Qt.Key.Key_F18
Qt.Key_F19 = Qt.Key.Key_F19
Qt.Key_F20 = Qt.Key.Key_F20
Qt.Key_F21 = Qt.Key.Key_F21
Qt.Key_F22 = Qt.Key.Key_F22
Qt.Key_F23 = Qt.Key.Key_F23
Qt.Key_F24 = Qt.Key.Key_F24
Qt.Key_F25 = Qt.Key.Key_F25
Qt.Key_F26 = Qt.Key.Key_F26
Qt.Key_F27 = Qt.Key.Key_F27
Qt.Key_F28 = Qt.Key.Key_F28
Qt.Key_F29 = Qt.Key.Key_F29
Qt.Key_F30 = Qt.Key.Key_F30
Qt.Key_F31 = Qt.Key.Key_F31
Qt.Key_F32 = Qt.Key.Key_F32
Qt.Key_F33 = Qt.Key.Key_F33
Qt.Key_F34 = Qt.Key.Key_F34
Qt.Key_F35 = Qt.Key.Key_F35
Qt.Key_Super_L = Qt.Key.Key_Super_L
Qt.Key_Super_R = Qt.Key.Key_Super_R
Qt.Key_Menu = Qt.Key.Key_Menu
Qt.Key_Hyper_L = Qt.Key.Key_Hyper_L
Qt.Key_Hyper_R = Qt.Key.Key_Hyper_R
Qt.Key_Help = Qt.Key.Key_Help
Qt.Key_Direction_L = Qt.Key.Key_Direction_L
Qt.Key_Direction_R = Qt.Key.Key_Direction_R
Qt.Key_Space = Qt.Key.Key_Space
Qt.Key_Any = Qt.Key.Key_Any
Qt.Key_Exclam = Qt.Key.Key_Exclam
Qt.Key_QuoteDbl = Qt.Key.Key_QuoteDbl
Qt.Key_NumberSign = Qt.Key.Key_NumberSign
Qt.Key_Dollar = Qt.Key.Key_Dollar
Qt.Key_Percent = Qt.Key.Key_Percent
Qt.Key_Ampersand = Qt.Key.Key_Ampersand
Qt.Key_Apostrophe = Qt.Key.Key_Apostrophe
Qt.Key_ParenLeft = Qt.Key.Key_ParenLeft
Qt.Key_ParenRight = Qt.Key.Key_ParenRight
Qt.Key_Asterisk = Qt.Key.Key_Asterisk
Qt.Key_Plus = Qt.Key.Key_Plus
Qt.Key_Comma = Qt.Key.Key_Comma
Qt.Key_Minus = Qt.Key.Key_Minus
Qt.Key_Period = Qt.Key.Key_Period
Qt.Key_Slash = Qt.Key.Key_Slash
Qt.Key_0 = Qt.Key.Key_0
Qt.Key_1 = Qt.Key.Key_1
Qt.Key_2 = Qt.Key.Key_2
Qt.Key_3 = Qt.Key.Key_3
Qt.Key_4 = Qt.Key.Key_4
Qt.Key_5 = Qt.Key.Key_5
Qt.Key_6 = Qt.Key.Key_6
Qt.Key_7 = Qt.Key.Key_7
Qt.Key_8 = Qt.Key.Key_8
Qt.Key_9 = Qt.Key.Key_9
Qt.Key_Colon = Qt.Key.Key_Colon
Qt.Key_Semicolon = Qt.Key.Key_Semicolon
Qt.Key_Less = Qt.Key.Key_Less
Qt.Key_Equal = Qt.Key.Key_Equal
Qt.Key_Greater = Qt.Key.Key_Greater
Qt.Key_Question = Qt.Key.Key_Question
Qt.Key_At = Qt.Key.Key_At
Qt.Key_A = Qt.Key.Key_A
Qt.Key_B = Qt.Key.Key_B
Qt.Key_C = Qt.Key.Key_C
Qt.Key_D = Qt.Key.Key_D
Qt.Key_E = Qt.Key.Key_E
Qt.Key_F = Qt.Key.Key_F
Qt.Key_G = Qt.Key.Key_G
Qt.Key_H = Qt.Key.Key_H
Qt.Key_I = Qt.Key.Key_I
Qt.Key_J = Qt.Key.Key_J
Qt.Key_K = Qt.Key.Key_K
Qt.Key_L = Qt.Key.Key_L
Qt.Key_M = Qt.Key.Key_M
Qt.Key_N = Qt.Key.Key_N
Qt.Key_O = Qt.Key.Key_O
Qt.Key_P = Qt.Key.Key_P
Qt.Key_Q = Qt.Key.Key_Q
Qt.Key_R = Qt.Key.Key_R
Qt.Key_S = Qt.Key.Key_S
Qt.Key_T = Qt.Key.Key_T
Qt.Key_U = Qt.Key.Key_U
Qt.Key_V = Qt.Key.Key_V
Qt.Key_W = Qt.Key.Key_W
Qt.Key_X = Qt.Key.Key_X
Qt.Key_Y = Qt.Key.Key_Y
Qt.Key_Z = Qt.Key.Key_Z
Qt.Key_BracketLeft = Qt.Key.Key_BracketLeft
Qt.Key_Backslash = Qt.Key.Key_Backslash
Qt.Key_BracketRight = Qt.Key.Key_BracketRight
Qt.Key_AsciiCircum = Qt.Key.Key_AsciiCircum
Qt.Key_Underscore = Qt.Key.Key_Underscore
Qt.Key_QuoteLeft = Qt.Key.Key_QuoteLeft
Qt.Key_BraceLeft = Qt.Key.Key_BraceLeft
Qt.Key_Bar = Qt.Key.Key_Bar
Qt.Key_BraceRight = Qt.Key.Key_BraceRight
Qt.Key_AsciiTilde = Qt.Key.Key_AsciiTilde
Qt.Key_nobreakspace = Qt.Key.Key_nobreakspace
Qt.Key_exclamdown = Qt.Key.Key_exclamdown
Qt.Key_cent = Qt.Key.Key_cent
Qt.Key_sterling = Qt.Key.Key_sterling
Qt.Key_currency = Qt.Key.Key_currency
Qt.Key_yen = Qt.Key.Key_yen
Qt.Key_brokenbar = Qt.Key.Key_brokenbar
Qt.Key_section = Qt.Key.Key_section
Qt.Key_diaeresis = Qt.Key.Key_diaeresis
Qt.Key_copyright = Qt.Key.Key_copyright
Qt.Key_ordfeminine = Qt.Key.Key_ordfeminine
Qt.Key_guillemotleft = Qt.Key.Key_guillemotleft
Qt.Key_notsign = Qt.Key.Key_notsign
Qt.Key_hyphen = Qt.Key.Key_hyphen
Qt.Key_registered = Qt.Key.Key_registered
Qt.Key_macron = Qt.Key.Key_macron
Qt.Key_degree = Qt.Key.Key_degree
Qt.Key_plusminus = Qt.Key.Key_plusminus
Qt.Key_twosuperior = Qt.Key.Key_twosuperior
Qt.Key_threesuperior = Qt.Key.Key_threesuperior
Qt.Key_acute = Qt.Key.Key_acute
Qt.Key_mu = Qt.Key.Key_mu
Qt.Key_paragraph = Qt.Key.Key_paragraph
Qt.Key_periodcentered = Qt.Key.Key_periodcentered
Qt.Key_cedilla = Qt.Key.Key_cedilla
Qt.Key_onesuperior = Qt.Key.Key_onesuperior
Qt.Key_masculine = Qt.Key.Key_masculine
Qt.Key_guillemotright = Qt.Key.Key_guillemotright
Qt.Key_onequarter = Qt.Key.Key_onequarter
Qt.Key_onehalf = Qt.Key.Key_onehalf
Qt.Key_threequarters = Qt.Key.Key_threequarters
Qt.Key_questiondown = Qt.Key.Key_questiondown
Qt.Key_Agrave = Qt.Key.Key_Agrave
Qt.Key_Aacute = Qt.Key.Key_Aacute
Qt.Key_Acircumflex = Qt.Key.Key_Acircumflex
Qt.Key_Atilde = Qt.Key.Key_Atilde
Qt.Key_Adiaeresis = Qt.Key.Key_Adiaeresis
Qt.Key_Aring = Qt.Key.Key_Aring
Qt.Key_AE = Qt.Key.Key_AE
Qt.Key_Ccedilla = Qt.Key.Key_Ccedilla
Qt.Key_Egrave = Qt.Key.Key_Egrave
Qt.Key_Eacute = Qt.Key.Key_Eacute
Qt.Key_Ecircumflex = Qt.Key.Key_Ecircumflex
Qt.Key_Ediaeresis = Qt.Key.Key_Ediaeresis
Qt.Key_Igrave = Qt.Key.Key_Igrave
Qt.Key_Iacute = Qt.Key.Key_Iacute
Qt.Key_Icircumflex = Qt.Key.Key_Icircumflex
Qt.Key_Idiaeresis = Qt.Key.Key_Idiaeresis
Qt.Key_ETH = Qt.Key.Key_ETH
Qt.Key_Ntilde = Qt.Key.Key_Ntilde
Qt.Key_Ograve = Qt.Key.Key_Ograve
Qt.Key_Oacute = Qt.Key.Key_Oacute
Qt.Key_Ocircumflex = Qt.Key.Key_Ocircumflex
Qt.Key_Otilde = Qt.Key.Key_Otilde
Qt.Key_Odiaeresis = Qt.Key.Key_Odiaeresis
Qt.Key_multiply = Qt.Key.Key_multiply
Qt.Key_Ooblique = Qt.Key.Key_Ooblique
Qt.Key_Ugrave = Qt.Key.Key_Ugrave
Qt.Key_Uacute = Qt.Key.Key_Uacute
Qt.Key_Ucircumflex = Qt.Key.Key_Ucircumflex
Qt.Key_Udiaeresis = Qt.Key.Key_Udiaeresis
Qt.Key_Yacute = Qt.Key.Key_Yacute
Qt.Key_THORN = Qt.Key.Key_THORN
Qt.Key_ssharp = Qt.Key.Key_ssharp
Qt.Key_division = Qt.Key.Key_division
Qt.Key_ydiaeresis = Qt.Key.Key_ydiaeresis
Qt.Key_AltGr = Qt.Key.Key_AltGr
Qt.Key_Multi_key = Qt.Key.Key_Multi_key
Qt.Key_Codeinput = Qt.Key.Key_Codeinput
Qt.Key_SingleCandidate = Qt.Key.Key_SingleCandidate
Qt.Key_MultipleCandidate = Qt.Key.Key_MultipleCandidate
Qt.Key_PreviousCandidate = Qt.Key.Key_PreviousCandidate
Qt.Key_Mode_switch = Qt.Key.Key_Mode_switch
Qt.Key_Kanji = Qt.Key.Key_Kanji
Qt.Key_Muhenkan = Qt.Key.Key_Muhenkan
Qt.Key_Henkan = Qt.Key.Key_Henkan
Qt.Key_Romaji = Qt.Key.Key_Romaji
Qt.Key_Hiragana = Qt.Key.Key_Hiragana
Qt.Key_Katakana = Qt.Key.Key_Katakana
Qt.Key_Hiragana_Katakana = Qt.Key.Key_Hiragana_Katakana
Qt.Key_Zenkaku = Qt.Key.Key_Zenkaku
Qt.Key_Hankaku = Qt.Key.Key_Hankaku
Qt.Key_Zenkaku_Hankaku = Qt.Key.Key_Zenkaku_Hankaku
Qt.Key_Touroku = Qt.Key.Key_Touroku
Qt.Key_Massyo = Qt.Key.Key_Massyo
Qt.Key_Kana_Lock = Qt.Key.Key_Kana_Lock
Qt.Key_Kana_Shift = Qt.Key.Key_Kana_Shift
Qt.Key_Eisu_Shift = Qt.Key.Key_Eisu_Shift
Qt.Key_Eisu_toggle = Qt.Key.Key_Eisu_toggle
Qt.Key_Hangul = Qt.Key.Key_Hangul
Qt.Key_Hangul_Start = Qt.Key.Key_Hangul_Start
Qt.Key_Hangul_End = Qt.Key.Key_Hangul_End
Qt.Key_Hangul_Hanja = Qt.Key.Key_Hangul_Hanja
Qt.Key_Hangul_Jamo = Qt.Key.Key_Hangul_Jamo
Qt.Key_Hangul_Romaja = Qt.Key.Key_Hangul_Romaja
Qt.Key_Hangul_Jeonja = Qt.Key.Key_Hangul_Jeonja
Qt.Key_Hangul_Banja = Qt.Key.Key_Hangul_Banja
Qt.Key_Hangul_PreHanja = Qt.Key.Key_Hangul_PreHanja
Qt.Key_Hangul_PostHanja = Qt.Key.Key_Hangul_PostHanja
Qt.Key_Hangul_Special = Qt.Key.Key_Hangul_Special
Qt.Key_Dead_Grave = Qt.Key.Key_Dead_Grave
Qt.Key_Dead_Acute = Qt.Key.Key_Dead_Acute
Qt.Key_Dead_Circumflex = Qt.Key.Key_Dead_Circumflex
Qt.Key_Dead_Tilde = Qt.Key.Key_Dead_Tilde
Qt.Key_Dead_Macron = Qt.Key.Key_Dead_Macron
Qt.Key_Dead_Breve = Qt.Key.Key_Dead_Breve
Qt.Key_Dead_Abovedot = Qt.Key.Key_Dead_Abovedot
Qt.Key_Dead_Diaeresis = Qt.Key.Key_Dead_Diaeresis
Qt.Key_Dead_Abovering = Qt.Key.Key_Dead_Abovering
Qt.Key_Dead_Doubleacute = Qt.Key.Key_Dead_Doubleacute
Qt.Key_Dead_Caron = Qt.Key.Key_Dead_Caron
Qt.Key_Dead_Cedilla = Qt.Key.Key_Dead_Cedilla
Qt.Key_Dead_Ogonek = Qt.Key.Key_Dead_Ogonek
Qt.Key_Dead_Iota = Qt.Key.Key_Dead_Iota
Qt.Key_Dead_Voiced_Sound = Qt.Key.Key_Dead_Voiced_Sound
Qt.Key_Dead_Semivoiced_Sound = Qt.Key.Key_Dead_Semivoiced_Sound
Qt.Key_Dead_Belowdot = Qt.Key.Key_Dead_Belowdot
Qt.Key_Dead_Hook = Qt.Key.Key_Dead_Hook
Qt.Key_Dead_Horn = Qt.Key.Key_Dead_Horn
Qt.Key_Back = Qt.Key.Key_Back
Qt.Key_Forward = Qt.Key.Key_Forward
Qt.Key_Stop = Qt.Key.Key_Stop
Qt.Key_Refresh = Qt.Key.Key_Refresh
Qt.Key_VolumeDown = Qt.Key.Key_VolumeDown
Qt.Key_VolumeMute = Qt.Key.Key_VolumeMute
Qt.Key_VolumeUp = Qt.Key.Key_VolumeUp
Qt.Key_BassBoost = Qt.Key.Key_BassBoost
Qt.Key_BassUp = Qt.Key.Key_BassUp
Qt.Key_BassDown = Qt.Key.Key_BassDown
Qt.Key_TrebleUp = Qt.Key.Key_TrebleUp
Qt.Key_TrebleDown = Qt.Key.Key_TrebleDown
Qt.Key_MediaPlay = Qt.Key.Key_MediaPlay
Qt.Key_MediaStop = Qt.Key.Key_MediaStop
Qt.Key_MediaPrevious = Qt.Key.Key_MediaPrevious
Qt.Key_MediaNext = Qt.Key.Key_MediaNext
Qt.Key_MediaRecord = Qt.Key.Key_MediaRecord
Qt.Key_HomePage = Qt.Key.Key_HomePage
Qt.Key_Favorites = Qt.Key.Key_Favorites
Qt.Key_Search = Qt.Key.Key_Search
Qt.Key_Standby = Qt.Key.Key_Standby
Qt.Key_OpenUrl = Qt.Key.Key_OpenUrl
Qt.Key_LaunchMail = Qt.Key.Key_LaunchMail
Qt.Key_LaunchMedia = Qt.Key.Key_LaunchMedia
Qt.Key_Launch0 = Qt.Key.Key_Launch0
Qt.Key_Launch1 = Qt.Key.Key_Launch1
Qt.Key_Launch2 = Qt.Key.Key_Launch2
Qt.Key_Launch3 = Qt.Key.Key_Launch3
Qt.Key_Launch4 = Qt.Key.Key_Launch4
Qt.Key_Launch5 = Qt.Key.Key_Launch5
Qt.Key_Launch6 = Qt.Key.Key_Launch6
Qt.Key_Launch7 = Qt.Key.Key_Launch7
Qt.Key_Launch8 = Qt.Key.Key_Launch8
Qt.Key_Launch9 = Qt.Key.Key_Launch9
Qt.Key_LaunchA = Qt.Key.Key_LaunchA
Qt.Key_LaunchB = Qt.Key.Key_LaunchB
Qt.Key_LaunchC = Qt.Key.Key_LaunchC
Qt.Key_LaunchD = Qt.Key.Key_LaunchD
Qt.Key_LaunchE = Qt.Key.Key_LaunchE
Qt.Key_LaunchF = Qt.Key.Key_LaunchF
Qt.Key_MediaLast = Qt.Key.Key_MediaLast
Qt.Key_Select = Qt.Key.Key_Select
Qt.Key_Yes = Qt.Key.Key_Yes
Qt.Key_No = Qt.Key.Key_No
Qt.Key_Context1 = Qt.Key.Key_Context1
Qt.Key_Context2 = Qt.Key.Key_Context2
Qt.Key_Context3 = Qt.Key.Key_Context3
Qt.Key_Context4 = Qt.Key.Key_Context4
Qt.Key_Call = Qt.Key.Key_Call
Qt.Key_Hangup = Qt.Key.Key_Hangup
Qt.Key_Flip = Qt.Key.Key_Flip
Qt.Key_unknown = Qt.Key.Key_unknown
Qt.Key_Execute = Qt.Key.Key_Execute
Qt.Key_Printer = Qt.Key.Key_Printer
Qt.Key_Play = Qt.Key.Key_Play
Qt.Key_Sleep = Qt.Key.Key_Sleep
Qt.Key_Zoom = Qt.Key.Key_Zoom
Qt.Key_Cancel = Qt.Key.Key_Cancel
Qt.Key_MonBrightnessUp = Qt.Key.Key_MonBrightnessUp
Qt.Key_MonBrightnessDown = Qt.Key.Key_MonBrightnessDown
Qt.Key_KeyboardLightOnOff = Qt.Key.Key_KeyboardLightOnOff
Qt.Key_KeyboardBrightnessUp = Qt.Key.Key_KeyboardBrightnessUp
Qt.Key_KeyboardBrightnessDown = Qt.Key.Key_KeyboardBrightnessDown
Qt.Key_PowerOff = Qt.Key.Key_PowerOff
Qt.Key_WakeUp = Qt.Key.Key_WakeUp
Qt.Key_Eject = Qt.Key.Key_Eject
Qt.Key_ScreenSaver = Qt.Key.Key_ScreenSaver
Qt.Key_WWW = Qt.Key.Key_WWW
Qt.Key_Memo = Qt.Key.Key_Memo
Qt.Key_LightBulb = Qt.Key.Key_LightBulb
Qt.Key_Shop = Qt.Key.Key_Shop
Qt.Key_History = Qt.Key.Key_History
Qt.Key_AddFavorite = Qt.Key.Key_AddFavorite
Qt.Key_HotLinks = Qt.Key.Key_HotLinks
Qt.Key_BrightnessAdjust = Qt.Key.Key_BrightnessAdjust
Qt.Key_Finance = Qt.Key.Key_Finance
Qt.Key_Community = Qt.Key.Key_Community
Qt.Key_AudioRewind = Qt.Key.Key_AudioRewind
Qt.Key_BackForward = Qt.Key.Key_BackForward
Qt.Key_ApplicationLeft = Qt.Key.Key_ApplicationLeft
Qt.Key_ApplicationRight = Qt.Key.Key_ApplicationRight
Qt.Key_Book = Qt.Key.Key_Book
Qt.Key_CD = Qt.Key.Key_CD
Qt.Key_Calculator = Qt.Key.Key_Calculator
Qt.Key_ToDoList = Qt.Key.Key_ToDoList
Qt.Key_ClearGrab = Qt.Key.Key_ClearGrab
Qt.Key_Close = Qt.Key.Key_Close
Qt.Key_Copy = Qt.Key.Key_Copy
Qt.Key_Cut = Qt.Key.Key_Cut
Qt.Key_Display = Qt.Key.Key_Display
Qt.Key_DOS = Qt.Key.Key_DOS
Qt.Key_Documents = Qt.Key.Key_Documents
Qt.Key_Excel = Qt.Key.Key_Excel
Qt.Key_Explorer = Qt.Key.Key_Explorer
Qt.Key_Game = Qt.Key.Key_Game
Qt.Key_Go = Qt.Key.Key_Go
Qt.Key_iTouch = Qt.Key.Key_iTouch
Qt.Key_LogOff = Qt.Key.Key_LogOff
Qt.Key_Market = Qt.Key.Key_Market
Qt.Key_Meeting = Qt.Key.Key_Meeting
Qt.Key_MenuKB = Qt.Key.Key_MenuKB
Qt.Key_MenuPB = Qt.Key.Key_MenuPB
Qt.Key_MySites = Qt.Key.Key_MySites
Qt.Key_News = Qt.Key.Key_News
Qt.Key_OfficeHome = Qt.Key.Key_OfficeHome
Qt.Key_Option = Qt.Key.Key_Option
Qt.Key_Paste = Qt.Key.Key_Paste
Qt.Key_Phone = Qt.Key.Key_Phone
Qt.Key_Calendar = Qt.Key.Key_Calendar
Qt.Key_Reply = Qt.Key.Key_Reply
Qt.Key_Reload = Qt.Key.Key_Reload
Qt.Key_RotateWindows = Qt.Key.Key_RotateWindows
Qt.Key_RotationPB = Qt.Key.Key_RotationPB
Qt.Key_RotationKB = Qt.Key.Key_RotationKB
Qt.Key_Save = Qt.Key.Key_Save
Qt.Key_Send = Qt.Key.Key_Send
Qt.Key_Spell = Qt.Key.Key_Spell
Qt.Key_SplitScreen = Qt.Key.Key_SplitScreen
Qt.Key_Support = Qt.Key.Key_Support
Qt.Key_TaskPane = Qt.Key.Key_TaskPane
Qt.Key_Terminal = Qt.Key.Key_Terminal
Qt.Key_Tools = Qt.Key.Key_Tools
Qt.Key_Travel = Qt.Key.Key_Travel
Qt.Key_Video = Qt.Key.Key_Video
Qt.Key_Word = Qt.Key.Key_Word
Qt.Key_Xfer = Qt.Key.Key_Xfer
Qt.Key_ZoomIn = Qt.Key.Key_ZoomIn
Qt.Key_ZoomOut = Qt.Key.Key_ZoomOut
Qt.Key_Away = Qt.Key.Key_Away
Qt.Key_Messenger = Qt.Key.Key_Messenger
Qt.Key_WebCam = Qt.Key.Key_WebCam
Qt.Key_MailForward = Qt.Key.Key_MailForward
Qt.Key_Pictures = Qt.Key.Key_Pictures
Qt.Key_Music = Qt.Key.Key_Music
Qt.Key_Battery = Qt.Key.Key_Battery
Qt.Key_Bluetooth = Qt.Key.Key_Bluetooth
Qt.Key_WLAN = Qt.Key.Key_WLAN
Qt.Key_UWB = Qt.Key.Key_UWB
Qt.Key_AudioForward = Qt.Key.Key_AudioForward
Qt.Key_AudioRepeat = Qt.Key.Key_AudioRepeat
Qt.Key_AudioRandomPlay = Qt.Key.Key_AudioRandomPlay
Qt.Key_Subtitle = Qt.Key.Key_Subtitle
Qt.Key_AudioCycleTrack = Qt.Key.Key_AudioCycleTrack
Qt.Key_Time = Qt.Key.Key_Time
Qt.Key_Hibernate = Qt.Key.Key_Hibernate
Qt.Key_View = Qt.Key.Key_View
Qt.Key_TopMenu = Qt.Key.Key_TopMenu
Qt.Key_PowerDown = Qt.Key.Key_PowerDown
Qt.Key_Suspend = Qt.Key.Key_Suspend
Qt.Key_ContrastAdjust = Qt.Key.Key_ContrastAdjust
Qt.Key_MediaPause = Qt.Key.Key_MediaPause
Qt.Key_MediaTogglePlayPause = Qt.Key.Key_MediaTogglePlayPause
Qt.Key_LaunchG = Qt.Key.Key_LaunchG
Qt.Key_LaunchH = Qt.Key.Key_LaunchH
Qt.Key_ToggleCallHangup = Qt.Key.Key_ToggleCallHangup
Qt.Key_VoiceDial = Qt.Key.Key_VoiceDial
Qt.Key_LastNumberRedial = Qt.Key.Key_LastNumberRedial
Qt.Key_Camera = Qt.Key.Key_Camera
Qt.Key_CameraFocus = Qt.Key.Key_CameraFocus
Qt.Key_TouchpadToggle = Qt.Key.Key_TouchpadToggle
Qt.Key_TouchpadOn = Qt.Key.Key_TouchpadOn
Qt.Key_TouchpadOff = Qt.Key.Key_TouchpadOff
Qt.Key_MicMute = Qt.Key.Key_MicMute
Qt.Key_Red = Qt.Key.Key_Red
Qt.Key_Green = Qt.Key.Key_Green
Qt.Key_Yellow = Qt.Key.Key_Yellow
Qt.Key_Blue = Qt.Key.Key_Blue
Qt.Key_ChannelUp = Qt.Key.Key_ChannelUp
Qt.Key_ChannelDown = Qt.Key.Key_ChannelDown
Qt.Key_Guide = Qt.Key.Key_Guide
Qt.Key_Info = Qt.Key.Key_Info
Qt.Key_Settings = Qt.Key.Key_Settings
Qt.Key_Exit = Qt.Key.Key_Exit
Qt.Key_MicVolumeUp = Qt.Key.Key_MicVolumeUp
Qt.Key_MicVolumeDown = Qt.Key.Key_MicVolumeDown
Qt.Key_New = Qt.Key.Key_New
Qt.Key_Open = Qt.Key.Key_Open
Qt.Key_Find = Qt.Key.Key_Find
Qt.Key_Undo = Qt.Key.Key_Undo
Qt.Key_Redo = Qt.Key.Key_Redo
Qt.Key_Dead_Stroke = Qt.Key.Key_Dead_Stroke
Qt.Key_Dead_Abovecomma = Qt.Key.Key_Dead_Abovecomma
Qt.Key_Dead_Abovereversedcomma = Qt.Key.Key_Dead_Abovereversedcomma
Qt.Key_Dead_Doublegrave = Qt.Key.Key_Dead_Doublegrave
Qt.Key_Dead_Belowring = Qt.Key.Key_Dead_Belowring
Qt.Key_Dead_Belowmacron = Qt.Key.Key_Dead_Belowmacron
Qt.Key_Dead_Belowcircumflex = Qt.Key.Key_Dead_Belowcircumflex
Qt.Key_Dead_Belowtilde = Qt.Key.Key_Dead_Belowtilde
Qt.Key_Dead_Belowbreve = Qt.Key.Key_Dead_Belowbreve
Qt.Key_Dead_Belowdiaeresis = Qt.Key.Key_Dead_Belowdiaeresis
Qt.Key_Dead_Invertedbreve = Qt.Key.Key_Dead_Invertedbreve
Qt.Key_Dead_Belowcomma = Qt.Key.Key_Dead_Belowcomma
Qt.Key_Dead_Currency = Qt.Key.Key_Dead_Currency
Qt.Key_Dead_a = Qt.Key.Key_Dead_a
Qt.Key_Dead_A = Qt.Key.Key_Dead_A
Qt.Key_Dead_e = Qt.Key.Key_Dead_e
Qt.Key_Dead_E = Qt.Key.Key_Dead_E
Qt.Key_Dead_i = Qt.Key.Key_Dead_i
Qt.Key_Dead_I = Qt.Key.Key_Dead_I
Qt.Key_Dead_o = Qt.Key.Key_Dead_o
Qt.Key_Dead_O = Qt.Key.Key_Dead_O
Qt.Key_Dead_u = Qt.Key.Key_Dead_u
Qt.Key_Dead_U = Qt.Key.Key_Dead_U
Qt.Key_Dead_Small_Schwa = Qt.Key.Key_Dead_Small_Schwa
Qt.Key_Dead_Capital_Schwa = Qt.Key.Key_Dead_Capital_Schwa
Qt.Key_Dead_Greek = Qt.Key.Key_Dead_Greek
Qt.Key_Dead_Lowline = Qt.Key.Key_Dead_Lowline
Qt.Key_Dead_Aboveverticalline = Qt.Key.Key_Dead_Aboveverticalline
Qt.Key_Dead_Belowverticalline = Qt.Key.Key_Dead_Belowverticalline
Qt.Key_Dead_Longsolidusoverlay = Qt.Key.Key_Dead_Longsolidusoverlay

# QtGui

QClipboard.Clipboard = QClipboard.Mode.Clipboard
QClipboard.Selection = QClipboard.Mode.Selection
QColor.HexRgb = QColor.NameFormat.HexRgb
QFont.StyleNormal = QFont.Style.StyleNormal
QFont.Monospace = QFont.StyleHint.Monospace
QFont.TypeWriter = QFont.StyleHint.TypeWriter
QFont.Bold = QFont.Weight.Bold
QFont.Normal = QFont.Weight.Normal
QFont.Thin = QFont.Weight.Thin
QFontDatabase.FixedFont = QFontDatabase.SystemFont.FixedFont
QFontDatabase.GeneralFont = QFontDatabase.SystemFont.GeneralFont
QIcon.Active = QIcon.Mode.Active
QIcon.Disabled = QIcon.Mode.Disabled
QIcon.Normal = QIcon.Mode.Normal
QIcon.Selected = QIcon.Mode.Selected
QIcon.Off = QIcon.State.Off
QIcon.On = QIcon.State.On
QImage.Format_ARGB32 = QImage.Format.Format_ARGB32
QImage.Format_Indexed8 = QImage.Format.Format_Indexed8
QImage.Format_Mono = QImage.Format.Format_Mono
QImage.Format_RGB32 = QImage.Format.Format_RGB32
QKeySequence.NativeText = QKeySequence.SequenceFormat.NativeText
QKeySequence.PortableText = QKeySequence.SequenceFormat.PortableText
QKeySequence.Find = QKeySequence.StandardKey.Find
QKeySequence.FindNext = QKeySequence.StandardKey.FindNext
QKeySequence.FindPrevious = QKeySequence.StandardKey.FindPrevious
QKeySequence.InsertParagraphSeparator = (
    QKeySequence.StandardKey.InsertParagraphSeparator
)
QKeySequence.Refresh = QKeySequence.StandardKey.Refresh
QMovie.CacheAll = QMovie.CacheMode.CacheAll
QPainter.Antialiasing = QPainter.RenderHint.Antialiasing
QPalette.Active = QPalette.ColorGroup.Active
QPalette.Disabled = QPalette.ColorGroup.Disabled
QPalette.Inactive = QPalette.ColorGroup.Inactive
QPalette.AlternateBase = QPalette.ColorRole.AlternateBase
QPalette.Base = QPalette.ColorRole.Base
QPalette.BrightText = QPalette.ColorRole.BrightText
QPalette.Button = QPalette.ColorRole.Button
QPalette.ButtonText = QPalette.ColorRole.ButtonText
QPalette.Highlight = QPalette.ColorRole.Highlight
QPalette.HighlightedText = QPalette.ColorRole.HighlightedText
QPalette.Link = QPalette.ColorRole.Link
QPalette.Text = QPalette.ColorRole.Text
QPalette.ToolTipBase = QPalette.ColorRole.ToolTipBase
QPalette.ToolTipText = QPalette.ColorRole.ToolTipText
QPalette.Background = QPalette.ColorRole.Window
QPalette.Window = QPalette.ColorRole.Window
QPalette.WindowText = QPalette.ColorRole.WindowText
QTextCharFormat.NoUnderline = QTextCharFormat.UnderlineStyle.NoUnderline
QTextCharFormat.SingleUnderline = QTextCharFormat.UnderlineStyle.SingleUnderline
QTextCursor.MoveAnchor = QTextCursor.MoveMode.MoveAnchor
QTextCursor.End = QTextCursor.MoveOperation.End
QTextCursor.Start = QTextCursor.MoveOperation.Start
QTextCursor.StartOfLine = QTextCursor.MoveOperation.StartOfLine
QTextCursor.LineUnderCursor = QTextCursor.SelectionType.LineUnderCursor
QTextFormat.FullWidthSelection = QTextFormat.Property.FullWidthSelection
QTextOption.NoWrap = QTextOption.WrapMode.NoWrap


# QtWebEngineCore

QWebEnginePage.FindBackward = QWebEnginePage.FindFlag.FindBackward
QWebEnginePage.FindCaseSensitively = QWebEnginePage.FindFlag.FindCaseSensitively
QWebEnginePage.Back = QWebEnginePage.WebAction.Back
QWebEnginePage.Forward = QWebEnginePage.WebAction.Forward
QWebEnginePage.WebBrowserTab = QWebEnginePage.WebWindowType.WebBrowserTab
QWebEngineProfile.NoPersistentCookies = (
    QWebEngineProfile.PersistentCookiesPolicy.NoPersistentCookies
)
QWebEngineScript.DocumentReady = QWebEngineScript.InjectionPoint.DocumentReady
QWebEngineScript.MainWorld = QWebEngineScript.ScriptWorldId.MainWorld
QWebEngineSettings.DefaultFontSize = QWebEngineSettings.FontSize.DefaultFontSize
QWebEngineSettings.MinimumFontSize = QWebEngineSettings.FontSize.MinimumFontSize
QWebEngineSettings.MinimumLogicalFontSize = (
    QWebEngineSettings.FontSize.MinimumLogicalFontSize
)
QWebEngineSettings.AllowGeolocationOnInsecureOrigins = (
    QWebEngineSettings.WebAttribute.AllowGeolocationOnInsecureOrigins
)
QWebEngineSettings.AllowRunningInsecureContent = (
    QWebEngineSettings.WebAttribute.AllowRunningInsecureContent
)
QWebEngineSettings.ErrorPageEnabled = QWebEngineSettings.WebAttribute.ErrorPageEnabled
QWebEngineSettings.JavascriptCanOpenWindows = (
    QWebEngineSettings.WebAttribute.JavascriptCanOpenWindows
)
QWebEngineSettings.JavascriptEnabled = QWebEngineSettings.WebAttribute.JavascriptEnabled
QWebEngineSettings.LocalContentCanAccessFileUrls = (
    QWebEngineSettings.WebAttribute.LocalContentCanAccessFileUrls
)
QWebEngineSettings.LocalStorageEnabled = (
    QWebEngineSettings.WebAttribute.LocalStorageEnabled
)
QWebEngineSettings.PdfViewerEnabled = QWebEngineSettings.WebAttribute.PdfViewerEnabled
QWebEngineSettings.PlaybackRequiresUserGesture = (
    QWebEngineSettings.WebAttribute.PlaybackRequiresUserGesture
)
QWebEngineSettings.PluginsEnabled = QWebEngineSettings.WebAttribute.PluginsEnabled
QWebEngineSettings.ScreenCaptureEnabled = (
    QWebEngineSettings.WebAttribute.ScreenCaptureEnabled
)

# QtWidgets

QAbstractItemView.MoveDown = QAbstractItemView.CursorAction.MoveDown
QAbstractItemView.MoveUp = QAbstractItemView.CursorAction.MoveUp
QAbstractItemView.InternalMove = QAbstractItemView.DragDropMode.InternalMove
QAbstractItemView.AboveItem = QAbstractItemView.DropIndicatorPosition.AboveItem
QAbstractItemView.BelowItem = QAbstractItemView.DropIndicatorPosition.BelowItem
QAbstractItemView.OnItem = QAbstractItemView.DropIndicatorPosition.OnItem
QAbstractItemView.OnViewport = QAbstractItemView.DropIndicatorPosition.OnViewport
QAbstractItemView.NoEditTriggers = QAbstractItemView.EditTrigger.NoEditTriggers
QAbstractItemView.PositionAtCenter = QAbstractItemView.ScrollHint.PositionAtCenter
QAbstractItemView.PositionAtTop = QAbstractItemView.ScrollHint.PositionAtTop
QAbstractItemView.ScrollPerPixel = QAbstractItemView.ScrollMode.ScrollPerPixel
QAbstractItemView.SelectItems = QAbstractItemView.SelectionBehavior.SelectItems
QAbstractItemView.SelectRows = QAbstractItemView.SelectionBehavior.SelectRows
QAbstractItemView.ContiguousSelection = (
    QAbstractItemView.SelectionMode.ContiguousSelection
)
QAbstractItemView.ExtendedSelection = QAbstractItemView.SelectionMode.ExtendedSelection
QAbstractItemView.MultiSelection = QAbstractItemView.SelectionMode.MultiSelection
QAbstractItemView.SingleSelection = QAbstractItemView.SelectionMode.SingleSelection
QAbstractScrollArea.AdjustToContents = (
    QAbstractScrollArea.SizeAdjustPolicy.AdjustToContents
)
QAbstractScrollArea.AdjustToContentsOnFirstShow = (
    QAbstractScrollArea.SizeAdjustPolicy.AdjustToContentsOnFirstShow
)
QAbstractSpinBox.NoButtons = QAbstractSpinBox.ButtonSymbols.NoButtons
QAbstractSpinBox.PlusMinus = QAbstractSpinBox.ButtonSymbols.PlusMinus
QAbstractSpinBox.UpDownArrows = QAbstractSpinBox.ButtonSymbols.UpDownArrows
QAction.Trigger = QAction.ActionEvent.Trigger
QBoxLayout.LeftToRight = QBoxLayout.Direction.LeftToRight
QBoxLayout.RightToLeft = QBoxLayout.Direction.RightToLeft
QBoxLayout.TopToBottom = QBoxLayout.Direction.TopToBottom
QColorDialog.DontUseNativeDialog = QColorDialog.ColorDialogOption.DontUseNativeDialog
QComboBox.InsertAtBottom = QComboBox.InsertPolicy.InsertAtBottom
QComboBox.NoInsert = QComboBox.InsertPolicy.NoInsert
QComboBox.AdjustToContents = QComboBox.SizeAdjustPolicy.AdjustToContents
QCompleter.UnfilteredPopupCompletion = (
    QCompleter.CompletionMode.UnfilteredPopupCompletion
)
QDateTimeEdit.HourSection = QDateTimeEdit.Section.HourSection
QDialog.Accepted = QDialog.DialogCode.Accepted
QDialog.Rejected = QDialog.DialogCode.Rejected
QDialogButtonBox.AcceptRole = QDialogButtonBox.ButtonRole.AcceptRole
QDialogButtonBox.ActionRole = QDialogButtonBox.ButtonRole.ActionRole
QDialogButtonBox.ActionRole = QDialogButtonBox.ButtonRole.ActionRole
QDialogButtonBox.ApplyRole = QDialogButtonBox.ButtonRole.ApplyRole
QDialogButtonBox.DestructiveRole = QDialogButtonBox.ButtonRole.DestructiveRole
QDialogButtonBox.DestructiveRole = QDialogButtonBox.ButtonRole.DestructiveRole
QDialogButtonBox.HelpRole = QDialogButtonBox.ButtonRole.HelpRole
QDialogButtonBox.InvalidRole = QDialogButtonBox.ButtonRole.InvalidRole
QDialogButtonBox.NoRole = QDialogButtonBox.ButtonRole.NoRole
QDialogButtonBox.RejectRole = QDialogButtonBox.ButtonRole.RejectRole
QDialogButtonBox.RejectRole = QDialogButtonBox.ButtonRole.RejectRole
QDialogButtonBox.ResetRole = QDialogButtonBox.ButtonRole.ResetRole
QDialogButtonBox.ResetRole = QDialogButtonBox.ButtonRole.ResetRole
QDialogButtonBox.YesRole = QDialogButtonBox.ButtonRole.YesRole
QDialogButtonBox.Abort = QDialogButtonBox.StandardButton.Abort
QDialogButtonBox.Apply = QDialogButtonBox.StandardButton.Apply
QDialogButtonBox.Cancel = QDialogButtonBox.StandardButton.Cancel
QDialogButtonBox.Cancel = QDialogButtonBox.StandardButton.Cancel
QDialogButtonBox.Close = QDialogButtonBox.StandardButton.Close
QDialogButtonBox.Close = QDialogButtonBox.StandardButton.Close
QDialogButtonBox.Discard = QDialogButtonBox.StandardButton.Discard
QDialogButtonBox.Help = QDialogButtonBox.StandardButton.Help
QDialogButtonBox.Help = QDialogButtonBox.StandardButton.Help
QDialogButtonBox.Ignore = QDialogButtonBox.StandardButton.Ignore
QDialogButtonBox.No = QDialogButtonBox.StandardButton.No
QDialogButtonBox.NoButton = QDialogButtonBox.StandardButton.NoButton
QDialogButtonBox.NoToAll = QDialogButtonBox.StandardButton.NoToAll
QDialogButtonBox.Ok = QDialogButtonBox.StandardButton.Ok
QDialogButtonBox.Ok = QDialogButtonBox.StandardButton.Ok
QDialogButtonBox.Open = QDialogButtonBox.StandardButton.Open
QDialogButtonBox.Reset = QDialogButtonBox.StandardButton.Reset
QDialogButtonBox.Reset = QDialogButtonBox.StandardButton.Reset
QDialogButtonBox.RestoreDefaults = QDialogButtonBox.StandardButton.RestoreDefaults
QDialogButtonBox.RestoreDefaults = QDialogButtonBox.StandardButton.RestoreDefaults
QDialogButtonBox.Retry = QDialogButtonBox.StandardButton.Retry
QDialogButtonBox.Save = QDialogButtonBox.StandardButton.Save
QDialogButtonBox.Save = QDialogButtonBox.StandardButton.Save
QDialogButtonBox.SaveAll = QDialogButtonBox.StandardButton.SaveAll
QDialogButtonBox.Yes = QDialogButtonBox.StandardButton.Yes
QDialogButtonBox.YesToAll = QDialogButtonBox.StandardButton.YesToAll
QDockWidget.DockWidgetClosable = QDockWidget.DockWidgetFeature.DockWidgetClosable
QDockWidget.AllDockWidgetFeatures = (
    QDockWidget.DockWidgetFeature.DockWidgetClosable
    | QDockWidget.DockWidgetFeature.DockWidgetMovable
    | QDockWidget.DockWidgetFeature.DockWidgetFloatable
)
QDockWidget.DockWidgetFloatable = QDockWidget.DockWidgetFeature.DockWidgetFloatable
QDockWidget.DockWidgetMovable = QDockWidget.DockWidgetFeature.DockWidgetMovable
QDockWidget.DockWidgetVerticalTitleBar = (
    QDockWidget.DockWidgetFeature.DockWidgetVerticalTitleBar
)
QDockWidget.NoDockWidgetFeatures = QDockWidget.DockWidgetFeature.NoDockWidgetFeatures
QFileDialog.AcceptOpen = QFileDialog.AcceptMode.AcceptOpen
QFileDialog.Accept = QFileDialog.DialogLabel.Accept
QFileDialog.AnyFile = QFileDialog.FileMode.AnyFile
QFileDialog.Directory = QFileDialog.FileMode.Directory
QFileDialog.ExistingFile = QFileDialog.FileMode.ExistingFile
QFileDialog.ExistingFiles = QFileDialog.FileMode.ExistingFiles
QFileDialog.DontConfirmOverwrite = QFileDialog.Option.DontConfirmOverwrite
QFileDialog.DontResolveSymlinks = QFileDialog.Option.DontResolveSymlinks
QFileDialog.DontUseNativeDialog = QFileDialog.Option.DontUseNativeDialog
QFileDialog.ShowDirsOnly = QFileDialog.Option.ShowDirsOnly
QFormLayout.FieldRole = QFormLayout.ItemRole.FieldRole
QFormLayout.LabelRole = QFormLayout.ItemRole.LabelRole
QFormLayout.SpanningRole = QFormLayout.ItemRole.SpanningRole
QFrame.Plain = QFrame.Shadow.Plain
QFrame.Raised = QFrame.Shadow.Raised
QFrame.Sunken = QFrame.Shadow.Sunken
QFrame.Box = QFrame.Shape.Box
QFrame.HLine = QFrame.Shape.HLine
QFrame.NoFrame = QFrame.Shape.NoFrame
QFrame.Panel = QFrame.Shape.Panel
QFrame.StyledPanel = QFrame.Shape.StyledPanel
QFrame.VLine = QFrame.Shape.VLine
QGraphicsItem.ItemIsFocusable = QGraphicsItem.GraphicsItemFlag.ItemIsFocusable
QGraphicsItem.ItemIsMovable = QGraphicsItem.GraphicsItemFlag.ItemIsMovable
QGraphicsItem.ItemIsSelectable = QGraphicsItem.GraphicsItemFlag.ItemIsSelectable
QGraphicsItem.ItemSendsGeometryChanges = (
    QGraphicsItem.GraphicsItemFlag.ItemSendsGeometryChanges
)
QGraphicsView.NoDrag = QGraphicsView.DragMode.NoDrag
QGraphicsView.RubberBandDrag = QGraphicsView.DragMode.RubberBandDrag
QGraphicsView.ScrollHandDrag = QGraphicsView.DragMode.ScrollHandDrag
QHeaderView.Fixed = QHeaderView.ResizeMode.Fixed
QHeaderView.Interactive = QHeaderView.ResizeMode.Interactive
QHeaderView.ResizeToContents = QHeaderView.ResizeMode.ResizeToContents
QHeaderView.Stretch = QHeaderView.ResizeMode.Stretch
QLayout.SetDefaultConstraint = QLayout.SizeConstraint.SetDefaultConstraint
QLayout.SetFixedSize = QLayout.SizeConstraint.SetFixedSize
QLayout.SetMaximumSize = QLayout.SizeConstraint.SetMaximumSize
QLayout.SetMinAndMaxSize = QLayout.SizeConstraint.SetMinAndMaxSize
QLayout.SetNoConstraint = QLayout.SizeConstraint.SetNoConstraint
QLineEdit.Normal = QLineEdit.EchoMode.Normal
QLineEdit.Password = QLineEdit.EchoMode.Password
QLineEdit.PasswordEchoOnEdit = QLineEdit.EchoMode.PasswordEchoOnEdit
QListView.LeftToRight = QListView.Flow.LeftToRight
QListView.Adjust = QListView.ResizeMode.Adjust
QListView.Fixed = QListView.ResizeMode.Fixed
QListView.IconMode = QListView.ViewMode.IconMode
QListWidgetItem.Type = QListWidgetItem.ItemType.Type
QMessageBox.AcceptRole = QMessageBox.ButtonRole.AcceptRole
QMessageBox.Critical = QMessageBox.Icon.Critical
QMessageBox.Information = QMessageBox.Icon.Information
QMessageBox.Question = QMessageBox.Icon.Question
QMessageBox.Warning = QMessageBox.Icon.Warning
QMessageBox.Abort = QMessageBox.StandardButton.Abort
QMessageBox.Cancel = QMessageBox.StandardButton.Cancel
QMessageBox.Close = QMessageBox.StandardButton.Close
QMessageBox.Help = QMessageBox.StandardButton.Help
QMessageBox.Ignore = QMessageBox.StandardButton.Ignore
QMessageBox.No = QMessageBox.StandardButton.No
QMessageBox.Ok = QMessageBox.StandardButton.Ok
QMessageBox.Retry = QMessageBox.StandardButton.Retry
QMessageBox.Yes = QMessageBox.StandardButton.Yes
QPlainTextEdit.NoWrap = QPlainTextEdit.LineWrapMode.NoWrap
QProgressBar.BottomToTop = QProgressBar.Direction.BottomToTop
QProgressBar.TopToBottom = QProgressBar.Direction.TopToBottom
QRubberBand.Rectangle = QRubberBand.Shape.Rectangle
QSizePolicy.PushButton = QSizePolicy.ControlType.PushButton
QSizePolicy.Expanding = QSizePolicy.Policy.Expanding
QSizePolicy.Fixed = QSizePolicy.Policy.Fixed
QSizePolicy.Ignored = QSizePolicy.Policy.Ignored
QSizePolicy.Maximum = QSizePolicy.Policy.Maximum
QSizePolicy.Minimum = QSizePolicy.Policy.Minimum
QSizePolicy.MinimumExpanding = QSizePolicy.Policy.MinimumExpanding
QSizePolicy.Preferred = QSizePolicy.Policy.Preferred
QSlider.NoTicks = QSlider.TickPosition.NoTicks
QSlider.TicksAbove = QSlider.TickPosition.TicksAbove
QSlider.TicksBelow = QSlider.TickPosition.TicksBelow
QSlider.TicksBothSides = QSlider.TickPosition.TicksBothSides
QSlider.TicksLeft = QSlider.TickPosition.TicksLeft
QSlider.TicksRight = QSlider.TickPosition.TicksRight
QStyle.CC_Slider = QStyle.ComplexControl.CC_Slider
QStyle.CE_ItemViewItem = QStyle.ControlElement.CE_ItemViewItem
QStyle.PM_LayoutBottomMargin = QStyle.PixelMetric.PM_LayoutBottomMargin
QStyle.PM_LayoutHorizontalSpacing = QStyle.PixelMetric.PM_LayoutHorizontalSpacing
QStyle.PM_LayoutLeftMargin = QStyle.PixelMetric.PM_LayoutLeftMargin
QStyle.PM_LayoutRightMargin = QStyle.PixelMetric.PM_LayoutRightMargin
QStyle.PM_LayoutTopMargin = QStyle.PixelMetric.PM_LayoutTopMargin
QStyle.PM_LayoutVerticalSpacing = QStyle.PixelMetric.PM_LayoutVerticalSpacing
QStyle.PM_SliderLength = QStyle.PixelMetric.PM_SliderLength
QStyle.PM_SliderSpaceAvailable = QStyle.PixelMetric.PM_SliderSpaceAvailable
QStyle.SP_ArrowDown = QStyle.StandardPixmap.SP_ArrowDown
QStyle.SP_BrowserReload = QStyle.StandardPixmap.SP_BrowserReload
QStyle.SP_DialogApplyButton = QStyle.StandardPixmap.SP_DialogApplyButton
QStyle.SP_DialogCancelButton = QStyle.StandardPixmap.SP_DialogCancelButton
QStyle.SP_DirLinkIcon = QStyle.StandardPixmap.SP_DirLinkIcon
QStyle.SP_TrashIcon = QStyle.StandardPixmap.SP_TrashIcon
QStyle.State_Children = QStyle.StateFlag.State_Children
QStyle.State_Selected = QStyle.StateFlag.State_Selected
QStyle.SC_SliderGroove = QStyle.SubControl.SC_SliderGroove
QStyle.SC_SliderHandle = QStyle.SubControl.SC_SliderHandle
QStyle.SE_ItemViewItemText = QStyle.SubElement.SE_ItemViewItemText
QSystemTrayIcon.DoubleClick = QSystemTrayIcon.ActivationReason.DoubleClick
QSystemTrayIcon.Trigger = QSystemTrayIcon.ActivationReason.Trigger
QSystemTrayIcon.Critical = QSystemTrayIcon.MessageIcon.Critical
QSystemTrayIcon.Information = QSystemTrayIcon.MessageIcon.Information
QSystemTrayIcon.NoIcon = QSystemTrayIcon.MessageIcon.NoIcon
QSystemTrayIcon.Warning = QSystemTrayIcon.MessageIcon.Warning
QTabBar.RightSide = QTabBar.ButtonPosition.RightSide
QTabWidget.North = QTabWidget.TabPosition.North
QTabWidget.Rounded = QTabWidget.TabShape.Rounded
QTextEdit.NoWrap = QTextEdit.LineWrapMode.NoWrap
QToolButton.InstantPopup = QToolButton.ToolButtonPopupMode.InstantPopup
QWizard.NoBackButtonOnLastPage = QWizard.WizardOption.NoBackButtonOnLastPage
QWizard.ClassicStyle = QWizard.WizardStyle.ClassicStyle

# QtDBus

QDBus.AutoDetect = QDBus.CallMode.AutoDetect

# Globally alias removed PyQt5.Qt to PyQt6.QtCore.Qt
##########################################################################

from . import qt5qt

sys.modules["PyQt5.Qt"] = qt5qt
