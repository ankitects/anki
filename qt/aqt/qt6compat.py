# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# type: ignore
# pylint: disable=unused-import

"""
Patches and aliases that provide a PyQt5 → PyQt6 compatibility shim for add-ons
"""

import sys

import PyQt6.QtCore

# Globally alias PyQt5 to PyQt6 ####

sys.modules["PyQt5"] = PyQt6
# Need to register QtCore early as sip otherwise raises an error about PyQt6.QtCore
# already being registered
sys.modules["PyQt5.QtCore"] = PyQt6.QtCore

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *
from PyQt6.QtWebEngineCore import *

# Patch unscoped enums back in, aliasing them to scoped enums ####

# This is the subset of enums used in all public Anki add-ons as of 2021-10-19.
# Please note that this list is likely to be incomplete as the process used
# might have missed dynamically constructed enums. Also, as mostly only public
# Anki add-ons were taken into consideration, some enums in other add-ons
# might not be included. In those cases please consider filing a PR to
# extend the assignments below.

# Important: These patches are not meant to provide compatibility for all
# add-ons going forward, but simply to maintain support with already
# existing add-ons. Add-on authors should take heed to use scoped enums
# for any future code changes.

# QtCore

Qt.Round = Qt.HighDpiScaleFactorRoundingPolicy.Round
Qt.FindChildrenRecursively = Qt.FindChildOption.FindChildrenRecursively
Qt.TextSelectableByMouse = Qt.TextInteractionFlag.TextSelectableByMouse
Qt.TextBrowserInteraction = Qt.TextInteractionFlag.TextBrowserInteraction
Qt.NonModal = Qt.WindowModality.NonModal
Qt.WindowModal = Qt.WindowModality.WindowModal
Qt.ApplicationModal = Qt.WindowModality.ApplicationModal
Qt.MatchFixedString = Qt.MatchFlag.MatchFixedString
Qt.MatchContains = Qt.MatchFlag.MatchContains
Qt.MatchEndsWith = Qt.MatchFlag.MatchEndsWith
Qt.MatchRegExp = Qt.MatchFlag.MatchRegularExpression
Qt.MatchRecursive = Qt.MatchFlag.MatchRecursive
Qt.NoItemFlags = Qt.ItemFlag.NoItemFlags
Qt.ItemIsSelectable = Qt.ItemFlag.ItemIsSelectable
Qt.ItemIsEditable = Qt.ItemFlag.ItemIsEditable
Qt.ItemIsDragEnabled = Qt.ItemFlag.ItemIsDragEnabled
Qt.ItemIsDropEnabled = Qt.ItemFlag.ItemIsDropEnabled
Qt.ItemIsUserCheckable = Qt.ItemFlag.ItemIsUserCheckable
Qt.ItemIsEnabled = Qt.ItemFlag.ItemIsEnabled
Qt.DisplayRole = Qt.ItemDataRole.DisplayRole
Qt.DecorationRole = Qt.ItemDataRole.DecorationRole
Qt.EditRole = Qt.ItemDataRole.EditRole
Qt.ToolTipRole = Qt.ItemDataRole.ToolTipRole
Qt.FontRole = Qt.ItemDataRole.FontRole
Qt.BackgroundRole = Qt.ItemDataRole.BackgroundRole
Qt.ForegroundRole = Qt.ItemDataRole.ForegroundRole
Qt.UserRole = Qt.ItemDataRole.UserRole
Qt.Unchecked = Qt.CheckState.Unchecked
Qt.Checked = Qt.CheckState.Checked
Qt.MoveAction = Qt.DropAction.CopyAction
Qt.IgnoreAction = Qt.DropAction.MoveAction
Qt.LeftToRight = Qt.LayoutDirection.LeftToRight
Qt.ToolButtonIconOnly = Qt.ToolButtonStyle.ToolButtonIconOnly
Qt.PreventContextMenu = Qt.ContextMenuPolicy.PreventContextMenu
Qt.ActionsContextMenu = Qt.ContextMenuPolicy.ActionsContextMenu
Qt.CustomContextMenu = Qt.ContextMenuPolicy.CustomContextMenu
Qt.FastTransformation = Qt.TransformationMode.FastTransformation
Qt.SmoothTransformation = Qt.TransformationMode.SmoothTransformation
Qt.ScrollBarAsNeeded = Qt.ScrollBarPolicy.ScrollBarAsNeeded
Qt.ScrollBarAlwaysOff = Qt.ScrollBarPolicy.ScrollBarAlwaysOff
Qt.ScrollBarAlwaysOn = Qt.ScrollBarPolicy.ScrollBarAlwaysOn
Qt.LeftToolBarArea = Qt.ToolBarArea.LeftToolBarArea
Qt.RightToolBarArea = Qt.ToolBarArea.RightToolBarArea
Qt.TopToolBarArea = Qt.ToolBarArea.TopToolBarArea
Qt.LeftDockWidgetArea = Qt.DockWidgetArea.LeftDockWidgetArea
Qt.RightDockWidgetArea = Qt.DockWidgetArea.RightDockWidgetArea
Qt.TopDockWidgetArea = Qt.DockWidgetArea.TopDockWidgetArea
Qt.BottomDockWidgetArea = Qt.DockWidgetArea.BottomDockWidgetArea
Qt.IgnoreAspectRatio = Qt.AspectRatioMode.IgnoreAspectRatio
Qt.KeepAspectRatio = Qt.AspectRatioMode.KeepAspectRatio
Qt.KeepAspectRatioByExpanding = Qt.AspectRatioMode.KeepAspectRatioByExpanding
Qt.PlainText = Qt.TextFormat.PlainText
Qt.RichText = Qt.TextFormat.RichText
Qt.ArrowCursor = Qt.CursorShape.ArrowCursor
Qt.CrossCursor = Qt.CursorShape.CrossCursor
Qt.SizeVerCursor = Qt.CursorShape.SizeVerCursor
Qt.SizeHorCursor = Qt.CursorShape.SizeHorCursor
Qt.SizeBDiagCursor = Qt.CursorShape.SizeBDiagCursor
Qt.SizeFDiagCursor = Qt.CursorShape.SizeFDiagCursor
Qt.SizeAllCursor = Qt.CursorShape.SizeAllCursor
Qt.PointingHandCursor = Qt.CursorShape.PointingHandCursor
Qt.RoundJoin = Qt.PenJoinStyle.RoundJoin
Qt.RoundCap = Qt.PenCapStyle.RoundCap
Qt.NoPen = Qt.PenStyle.NoPen
Qt.SolidLine = Qt.PenStyle.SolidLine
Qt.WA_DeleteOnClose = Qt.WidgetAttribute.WA_DeleteOnClose
Qt.WA_TranslucentBackground = Qt.WidgetAttribute.WA_TranslucentBackground
Qt.WindowFullScreen = Qt.WindowState.WindowFullScreen
Qt.WindowActive = Qt.WindowState.WindowActive
Qt.Window = Qt.WindowType.Window
Qt.Tool = Qt.WindowType.Tool
Qt.ToolTip = Qt.WindowType.ToolTip
Qt.FramelessWindowHint = Qt.WindowType.FramelessWindowHint
Qt.CustomizeWindowHint = Qt.WindowType.CustomizeWindowHint
Qt.WindowStaysOnTopHint = Qt.WindowType.WindowStaysOnTopHint
Qt.NoDropShadowWindowHint = Qt.WindowType.NoDropShadowWindowHint
Qt.ElideLeft = Qt.TextElideMode.ElideLeft
Qt.ElideRight = Qt.TextElideMode.ElideRight
Qt.AlignLeft = Qt.AlignmentFlag.AlignLeft
Qt.AlignLeading = Qt.AlignmentFlag.AlignLeading
Qt.AlignRight = Qt.AlignmentFlag.AlignRight
Qt.AlignHCenter = Qt.AlignmentFlag.AlignHCenter
Qt.AlignTop = Qt.AlignmentFlag.AlignTop
Qt.AlignBottom = Qt.AlignmentFlag.AlignBottom
Qt.AlignVCenter = Qt.AlignmentFlag.AlignVCenter
Qt.AlignCenter = Qt.AlignmentFlag.AlignCenter
Qt.NoFocus = Qt.FocusPolicy.NoFocus
Qt.ClickFocus = Qt.FocusPolicy.ClickFocus
Qt.ClickFocus = Qt.FocusPolicy.ClickFocus
Qt.Horizontal = Qt.Orientation.Horizontal
Qt.Vertical = Qt.Orientation.Vertical
Qt.LeftButton = Qt.MouseButton.LeftButton
Qt.RightButton = Qt.MouseButton.RightButton
Qt.META = Qt.Modifier.META
Qt.SHIFT = Qt.Modifier.SHIFT
Qt.CTRL = Qt.Modifier.CTRL
Qt.ALT = Qt.Modifier.ALT
Qt.NoModifier = Qt.KeyboardModifier.NoModifier
Qt.ShiftModifier = Qt.KeyboardModifier.ShiftModifier
Qt.ControlModifier = Qt.KeyboardModifier.ControlModifier
Qt.AltModifier = Qt.KeyboardModifier.AltModifier
Qt.MetaModifier = Qt.KeyboardModifier.MetaModifier
Qt.black = Qt.GlobalColor.black
Qt.white = Qt.GlobalColor.white
Qt.darkGray = Qt.GlobalColor.darkGray
Qt.gray = Qt.GlobalColor.gray
Qt.lightGray = Qt.GlobalColor.lightGray
Qt.red = Qt.GlobalColor.red
Qt.green = Qt.GlobalColor.green
Qt.yellow = Qt.GlobalColor.yellow
Qt.darkRed = Qt.GlobalColor.darkRed
Qt.darkGreen = Qt.GlobalColor.darkGreen
Qt.darkBlue = Qt.GlobalColor.darkBlue
Qt.transparent = Qt.GlobalColor.transparent
QIODevice.ReadWrite = QIODevice.OpenModeFlag.ReadWrite
QEvent.MouseButtonPress = QEvent.Type.MouseButtonPress
QEvent.KeyPress = QEvent.Type.KeyPress
QEvent.KeyRelease = QEvent.Type.KeyRelease
QEvent.Move = QEvent.Type.Move
QEvent.Resize = QEvent.Type.Resize
QEvent.WindowDeactivate = QEvent.Type.WindowDeactivate
QEventLoop.AllEvents = QEventLoop.ProcessEventsFlag.AllEvents
QEventLoop.ExcludeUserInputEvents = QEventLoop.ProcessEventsFlag.ExcludeUserInputEvents
QItemSelectionModel.Select = QItemSelectionModel.SelectionFlag.Select
QItemSelectionModel.Current = QItemSelectionModel.SelectionFlag.Current
QItemSelectionModel.Rows = QItemSelectionModel.SelectionFlag.Rows
QItemSelectionModel.SelectCurrent = QItemSelectionModel.SelectionFlag.SelectCurrent
QLocale.UnitedStates = QLocale.Country.UnitedStates
QLocale.English = QLocale.Language.English

# QtGui

QColor.HexRgb = QColor.NameFormat.HexRgb
QClipboard.Clipboard = QClipboard.Mode.Clipboard
QFont.StyleNormal = QFont.Style.StyleNormal
QFont.Thin = QFont.Weight.Thin
QFont.Normal = QFont.Weight.Normal
QFont.Bold = QFont.Weight.Bold
QFont.TypeWriter = QFont.StyleHint.TypeWriter
QFont.Monospace = QFont.StyleHint.Monospace
QIcon.On = QIcon.State.On
QIcon.Off = QIcon.State.Off
QIcon.Normal = QIcon.Mode.Normal
QIcon.Disabled = QIcon.Mode.Disabled
QIcon.Active = QIcon.Mode.Active
QIcon.Selected = QIcon.Mode.Selected
QImage.Format_Mono = QImage.Format.Format_Mono
QImage.Format_Indexed8 = QImage.Format.Format_Indexed8
QImage.Format_RGB32 = QImage.Format.Format_RGB32
QImage.Format_ARGB32 = QImage.Format.Format_ARGB32
QKeySequence.NativeText = QKeySequence.SequenceFormat.NativeText
QKeySequence.PortableText = QKeySequence.SequenceFormat.PortableText
QPainter.Antialiasing = QPainter.Antialiasing = QPainter.RenderHint.Antialiasing
QPalette.WindowText = QPalette.ColorRole.WindowText
QPalette.Button = QPalette.ColorRole.Button
QPalette.Text = QPalette.ColorRole.Text
QPalette.BrightText = QPalette.ColorRole.BrightText
QPalette.ButtonText = QPalette.ColorRole.ButtonText
QPalette.Base = QPalette.ColorRole.Base
QPalette.Window = QPalette.ColorRole.Window
QPalette.Highlight = QPalette.ColorRole.Highlight
QPalette.HighlightedText = QPalette.ColorRole.HighlightedText
QPalette.Link = QPalette.ColorRole.Link
QPalette.AlternateBase = QPalette.ColorRole.AlternateBase
QPalette.ToolTipBase = QPalette.ColorRole.ToolTipBase
QPalette.ToolTipText = QPalette.ColorRole.ToolTipText
QPalette.Active = QPalette.ColorGroup.Active
QPalette.Disabled = QPalette.ColorGroup.Disabled
QTextFormat.FullWidthSelection = QTextFormat.Property.FullWidthSelection
QTextOption.NoWrap = QTextOption.WrapMode.NoWrap

# QtWebEngineCore

QWebEnginePage.FindBackward = (
    QWebEnginePage.FindBackward
) = QWebEnginePage.FindFlag.FindBackward
QWebEngineSettings.PluginsEnabled = QWebEngineSettings.WebAttribute.PluginsEnabled
QWebEngineSettings.PdfViewerEnabled = QWebEngineSettings.WebAttribute.PdfViewerEnabled

# QtWidgets

QFrame.NoFrame = QFrame.Shape.NoFrame
QFrame.Panel = QFrame.Shape.Panel
QFrame.HLine = QFrame.Shape.HLine
QFrame.VLine = QFrame.Shape.VLine
QFrame.StyledPanel = QFrame.Shape.StyledPanel
QFrame.Plain = QFrame.Shadow.Plain
QFrame.Raised = QFrame.Shadow.Raised
QFrame.Sunken = QFrame.Shadow.Sunken
QAbstractItemView.OnItem = QAbstractItemView.DropIndicatorPosition.OnItem
QAbstractItemView.AboveItem = QAbstractItemView.DropIndicatorPosition.AboveItem
QAbstractItemView.BelowItem = QAbstractItemView.DropIndicatorPosition.BelowItem
QAbstractItemView.OnViewport = QAbstractItemView.DropIndicatorPosition.OnViewport
QAbstractItemView.MoveUp = QAbstractItemView.CursorAction.MoveUp
QAbstractItemView.MoveDown = QAbstractItemView.CursorAction.MoveDown
QAbstractItemView.SingleSelection = QAbstractItemView.SelectionMode.SingleSelection
QAbstractItemView.ExtendedSelection = QAbstractItemView.SelectionMode.ExtendedSelection
QAbstractItemView.SelectItems = QAbstractItemView.SelectionBehavior.SelectItems
QAbstractItemView.SelectRows = QAbstractItemView.SelectionBehavior.SelectRows
QAbstractItemView.ScrollPerPixel = QAbstractItemView.ScrollMode.ScrollPerPixel
QAbstractItemView.PositionAtCenter = QAbstractItemView.ScrollHint.PositionAtCenter
QAbstractItemView.NoEditTriggers = QAbstractItemView.EditTrigger.NoEditTriggers
QAbstractItemView.InternalMove = QAbstractItemView.DragDropMode.InternalMove
QAbstractSpinBox.NoButtons = QAbstractSpinBox.ButtonSymbols.NoButtons
QLayout.SetDefaultConstraint = QLayout.SizeConstraint.SetDefaultConstraint
QLayout.SetFixedSize = QLayout.SizeConstraint.SetFixedSize
QBoxLayout.LeftToRight = QBoxLayout.Direction.LeftToRight
QBoxLayout.RightToLeft = QBoxLayout.Direction.RightToLeft
QBoxLayout.TopToBottom = QBoxLayout.Direction.TopToBottom
QDialog.Accepted = QDialog.DialogCode.Accepted
QStyle.SP_TrashIcon = QStyle.StandardPixmap.SP_TrashIcon
QStyle.SP_DirLinkIcon = QStyle.StandardPixmap.SP_DirLinkIcon
QStyle.SP_DialogCancelButton = QStyle.StandardPixmap.SP_DialogCancelButton
QStyle.SP_DialogApplyButton = QStyle.StandardPixmap.SP_DialogApplyButton
QStyle.SP_ArrowDown = QStyle.StandardPixmap.SP_ArrowDown
QStyle.SE_ItemViewItemText = QStyle.SubElement.SE_ItemViewItemText
QStyle.CE_ItemViewItem = QStyle.ControlElement.CE_ItemViewItem
QStyle.State_Selected = QStyle.StateFlag.State_Selected
QCompleter.UnfilteredPopupCompletion = (
    QCompleter.CompletionMode.UnfilteredPopupCompletion
)
QDialogButtonBox.Ok = QDialogButtonBox.StandardButton.Ok
QDialogButtonBox.Save = QDialogButtonBox.StandardButton.Save
QDialogButtonBox.Close = QDialogButtonBox.StandardButton.Close
QDialogButtonBox.Cancel = QDialogButtonBox.StandardButton.Cancel
QDialogButtonBox.Help = QDialogButtonBox.StandardButton.Help
QDialogButtonBox.Reset = QDialogButtonBox.StandardButton.Reset
QDialogButtonBox.RestoreDefaults = QDialogButtonBox.StandardButton.RestoreDefaults
QDialogButtonBox.RejectRole = QDialogButtonBox.ButtonRole.RejectRole
QDialogButtonBox.DestructiveRole = QDialogButtonBox.ButtonRole.DestructiveRole
QDialogButtonBox.ActionRole = QDialogButtonBox.ButtonRole.ActionRole
QDialogButtonBox.ResetRole = QDialogButtonBox.ButtonRole.ResetRole
QDockWidget.DockWidgetClosable = QDockWidget.DockWidgetFeature.DockWidgetClosable
QDockWidget.DockWidgetVerticalTitleBar = (
    QDockWidget.DockWidgetFeature.DockWidgetVerticalTitleBar
)
QDockWidget.NoDockWidgetFeatures = QDockWidget.DockWidgetFeature.NoDockWidgetFeatures
QFileDialog.ShowDirsOnly = QFileDialog.Option.ShowDirsOnly
QGraphicsItem.ItemIsMovable = QGraphicsItem.GraphicsItemFlag.ItemIsMovable
QGraphicsItem.ItemIsSelectable = QGraphicsItem.GraphicsItemFlag.ItemIsSelectable
QGraphicsItem.ItemIsFocusable = QGraphicsItem.GraphicsItemFlag.ItemIsFocusable
QGraphicsItem.ItemSendsGeometryChanges = (
    QGraphicsItem.GraphicsItemFlag.ItemSendsGeometryChanges
)
QGraphicsView.NoDrag = QGraphicsView.DragMode.NoDrag
QGraphicsView.ScrollHandDrag = QGraphicsView.DragMode.ScrollHandDrag
QGraphicsView.RubberBandDrag = QGraphicsView.DragMode.RubberBandDrag
QHeaderView.Interactive = QHeaderView.ResizeMode.Interactive
QHeaderView.Fixed = QHeaderView.ResizeMode.Fixed
QHeaderView.Stretch = QHeaderView.ResizeMode.Stretch
QLineEdit.Normal = QLineEdit.EchoMode.Normal
QListView.IconMode = QListView.ViewMode.IconMode
QListView.Fixed = QListView.ResizeMode.Fixed
QListView.Adjust = QListView.ResizeMode.Adjust
QListView.LeftToRight = QListView.Flow.LeftToRight
QMessageBox.Ok = QMessageBox.StandardButton.Ok
QMessageBox.Yes = QMessageBox.StandardButton.Yes
QMessageBox.No = QMessageBox.StandardButton.No
QMessageBox.Help = QMessageBox.StandardButton.Help
QMessageBox.Information = QMessageBox.Icon.Information
QMessageBox.Warning = QMessageBox.Icon.Warning
QMessageBox.Critical = QMessageBox.Icon.Critical
QPlainTextEdit.NoWrap = QPlainTextEdit.LineWrapMode.NoWrap
QRubberBand.Rectangle = QRubberBand.Rectangle = QRubberBand.Shape.Rectangle
QSizePolicy.Fixed = QSizePolicy.Policy.Fixed
QSizePolicy.Minimum = QSizePolicy.Policy.Minimum
QSizePolicy.Preferred = QSizePolicy.Policy.Preferred
QSizePolicy.MinimumExpanding = QSizePolicy.Policy.MinimumExpanding
QSizePolicy.Expanding = QSizePolicy.Policy.Expanding
QTabWidget.Rounded = QTabWidget.TabShape.Rounded
QTabWidget.North = QTabWidget.TabPosition.North
QToolButton.InstantPopup = QToolButton.ToolButtonPopupMode.InstantPopup
QWizard.NoBackButtonOnLastPage = QWizard.WizardOption.NoBackButtonOnLastPage
