# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# type: ignore
# pylint: disable=unused-import

"""
Patches and aliases that provide a PyQt5 → PyQt6 compatibility shim for add-ons
"""

import sys
import types
import typing

import PyQt6.QtCore
import PyQt6.QtDBus
import PyQt6.QtGui
import PyQt6.QtNetwork
import PyQt6.QtPrintSupport
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
        "Use QDir.addSearchPath() or mw.addonManager.setWebExports() instead."
    )


PyQt6.QtCore.qRegisterResourceData = qt_resource_system_call
PyQt6.QtCore.qUnregisterResourceData = qt_resource_system_call

# Patch unscoped enums back in, aliasing them to scoped enums
# ########################################################################

PyQt6.QtWidgets.QDockWidget.AllDockWidgetFeatures = (
    PyQt6.QtWidgets.QDockWidget.DockWidgetFeature.DockWidgetClosable
    | PyQt6.QtWidgets.QDockWidget.DockWidgetFeature.DockWidgetMovable
    | PyQt6.QtWidgets.QDockWidget.DockWidgetFeature.DockWidgetFloatable
)

# when we subclass QIcon, icons fail to show when returned by getData()
# in a tableview/treeview, so we need to manually alias these
PyQt6.QtGui.QIcon.Active = PyQt6.QtGui.QIcon.Mode.Active
PyQt6.QtGui.QIcon.Disabled = PyQt6.QtGui.QIcon.Mode.Disabled
PyQt6.QtGui.QIcon.Normal = PyQt6.QtGui.QIcon.Mode.Normal
PyQt6.QtGui.QIcon.Selected = PyQt6.QtGui.QIcon.Mode.Selected
PyQt6.QtGui.QIcon.Off = PyQt6.QtGui.QIcon.State.Off
PyQt6.QtGui.QIcon.On = PyQt6.QtGui.QIcon.State.On

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

# (module, [(type_name, enums)])
_enum_map = (
    (
        PyQt6.QtCore,
        [
            ("QEvent", ("Type",)),
            ("QEventLoop", ("ProcessEventsFlag",)),
            ("QIODevice", ("OpenModeFlag",)),
            ("QItemSelectionModel", ("SelectionFlag",)),
            ("QLocale", ("Country", "Language")),
            ("QMetaType", ("Type",)),
            ("QProcess", ("ProcessState", "ProcessChannel")),
            ("QStandardPaths", ("StandardLocation",)),
            (
                "Qt",
                (
                    "AlignmentFlag",
                    "ApplicationAttribute",
                    "ArrowType",
                    "AspectRatioMode",
                    "BrushStyle",
                    "CaseSensitivity",
                    "CheckState",
                    "ConnectionType",
                    "ContextMenuPolicy",
                    "CursorShape",
                    "DateFormat",
                    "DayOfWeek",
                    "DockWidgetArea",
                    "FindChildOption",
                    "FocusPolicy",
                    "FocusReason",
                    "GlobalColor",
                    "HighDpiScaleFactorRoundingPolicy",
                    "ImageConversionFlag",
                    "InputMethodHint",
                    "ItemDataRole",
                    "ItemFlag",
                    "KeyboardModifier",
                    "LayoutDirection",
                    "MatchFlag",
                    "Modifier",
                    "MouseButton",
                    "Orientation",
                    "PenCapStyle",
                    "PenJoinStyle",
                    "PenStyle",
                    "ScrollBarPolicy",
                    "ShortcutContext",
                    "SortOrder",
                    "TextElideMode",
                    "TextFlag",
                    "TextFormat",
                    "TextInteractionFlag",
                    "ToolBarArea",
                    "ToolButtonStyle",
                    "TransformationMode",
                    "WidgetAttribute",
                    "WindowModality",
                    "WindowState",
                    "WindowType",
                    "Key",
                ),
            ),
            ("QThread", ("Priority",)),
        ],
    ),
    (PyQt6.QtDBus, [("QDBus", ("CallMode",))]),
    (
        PyQt6.QtGui,
        [
            ("QAction", ("MenuRole", "ActionEvent")),
            ("QClipboard", ("Mode",)),
            ("QColor", ("NameFormat",)),
            ("QFont", ("Style", "Weight", "StyleHint")),
            ("QFontDatabase", ("WritingSystem", "SystemFont")),
            ("QImage", ("Format",)),
            ("QKeySequence", ("SequenceFormat", "StandardKey")),
            ("QMovie", ("CacheMode",)),
            ("QPageLayout", ("Orientation",)),
            ("QPageSize", ("PageSizeId",)),
            ("QPainter", ("RenderHint",)),
            ("QPalette", ("ColorRole", "ColorGroup")),
            ("QTextCharFormat", ("UnderlineStyle",)),
            ("QTextCursor", ("MoveOperation", "MoveMode", "SelectionType")),
            ("QTextFormat", ("Property",)),
            ("QTextOption", ("WrapMode",)),
            ("QValidator", ("State",)),
        ],
    ),
    (PyQt6.QtNetwork, [("QHostAddress", ("SpecialAddress",))]),
    (PyQt6.QtPrintSupport, [("QPrinter", ("Unit",))]),
    (
        PyQt6.QtWebEngineCore,
        [
            ("QWebEnginePage", ("WebWindowType", "FindFlag", "WebAction")),
            ("QWebEngineProfile", ("PersistentCookiesPolicy", "HttpCacheType")),
            ("QWebEngineScript", ("ScriptWorldId", "InjectionPoint")),
            ("QWebEngineSettings", ("FontSize", "WebAttribute")),
        ],
    ),
    (
        PyQt6.QtWidgets,
        [
            (
                "QAbstractItemView",
                (
                    "CursorAction",
                    "DropIndicatorPosition",
                    "ScrollMode",
                    "EditTrigger",
                    "SelectionMode",
                    "SelectionBehavior",
                    "DragDropMode",
                    "ScrollHint",
                ),
            ),
            ("QAbstractScrollArea", ("SizeAdjustPolicy",)),
            ("QAbstractSpinBox", ("ButtonSymbols",)),
            ("QBoxLayout", ("Direction",)),
            ("QColorDialog", ("ColorDialogOption",)),
            ("QComboBox", ("SizeAdjustPolicy", "InsertPolicy")),
            ("QCompleter", ("CompletionMode",)),
            ("QDateTimeEdit", ("Section",)),
            ("QDialog", ("DialogCode",)),
            ("QDialogButtonBox", ("StandardButton", "ButtonRole")),
            ("QDockWidget", ("DockWidgetFeature",)),
            ("QFileDialog", ("Option", "FileMode", "AcceptMode", "DialogLabel")),
            ("QFormLayout", ("FieldGrowthPolicy", "ItemRole")),
            ("QFrame", ("Shape", "Shadow")),
            ("QGraphicsItem", ("GraphicsItemFlag",)),
            ("QGraphicsPixmapItem", ("ShapeMode",)),
            ("QGraphicsView", ("ViewportAnchor", "DragMode")),
            ("QHeaderView", ("ResizeMode",)),
            ("QLayout", ("SizeConstraint",)),
            ("QLineEdit", ("EchoMode",)),
            (
                "QListView",
                ("Flow", "BrowserLayout", "ResizeMode", "Movement", "ViewMode"),
            ),
            ("QListWidgetItem", ("ItemType",)),
            ("QMessageBox", ("StandardButton", "Icon", "ButtonRole")),
            ("QPlainTextEdit", ("LineWrapMode",)),
            ("QProgressBar", ("Direction",)),
            ("QRubberBand", ("Shape",)),
            ("QSizePolicy", ("ControlType", "Policy")),
            ("QSlider", ("TickPosition",)),
            (
                "QStyle",
                (
                    "SubElement",
                    "ComplexControl",
                    "StandardPixmap",
                    "ControlElement",
                    "PixelMetric",
                    "StateFlag",
                    "SubControl",
                ),
            ),
            ("QSystemTrayIcon", ("MessageIcon", "ActivationReason")),
            ("QTabBar", ("ButtonPosition",)),
            ("QTabWidget", ("TabShape", "TabPosition")),
            ("QTextEdit", ("LineWrapMode",)),
            ("QToolButton", ("ToolButtonPopupMode",)),
            ("QWizard", ("WizardStyle", "WizardOption")),
        ],
    ),
)

_renamed_enum_cases = {
    "QComboBox": {
        "AdjustToMinimumContentsLength": "AdjustToMinimumContentsLengthWithIcon"
    },
    "QDialogButtonBox": {"No": "NoButton"},
    "QPainter": {"HighQualityAntialiasing": "Antialiasing"},
    "QPalette": {"Background": "Window", "Foreground": "WindowText"},
    "Qt": {"MatchRegExp": "MatchRegularExpression", "MidButton": "MiddleButton"},
}


# This works by wrapping each enum-containing Qt class (eg QAction) in a proxy.
# When an attribute is missing from the underlying Qt class, __getattr__ is
# called, and we try fetching the attribute from each of the declared enums
# for that module. If a match is found, a deprecation warning is printed.
#
# Looping through enumerations is not particularly efficient on a large type like
# Qt, but we only pay the cost when an attribute is not found. In the worst case,
# it's about 50ms per 1000 failed lookups on the Qt module.


def _instrument_type(
    module: types.ModuleType, type_name: str, enums: list[str]
) -> None:
    type = getattr(module, type_name)
    renamed_attrs = _renamed_enum_cases.get(type_name, {})

    class QtClassProxyType(type.__class__):
        def __getattr__(cls, provided_name):  # pylint: disable=no-self-argument
            # we know this is not an enum
            if provided_name == "__pyqtSignature__":
                raise AttributeError

            name = renamed_attrs.get(provided_name) or provided_name

            for enum_name in enums:
                enum = getattr(type, enum_name)
                try:
                    val = getattr(enum, name)
                except AttributeError:
                    continue

                print_deprecation_warning(
                    f"'{type_name}.{provided_name}' will stop working. Please use '{type_name}.{enum_name}.{name}' instead."
                )
                return val

            return getattr(type, name)

    class QtClassProxy(
        type, metaclass=QtClassProxyType
    ):  # pylint: disable=invalid-metaclass
        @staticmethod
        def _without_compat_wrapper():
            return type

    setattr(module, type_name, QtClassProxy)


for module, type_to_enum_list in _enum_map:
    for type_name, enums in type_to_enum_list:
        _instrument_type(module, type_name, enums)

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


# Mock the removed PyQt5.Qt module
##########################################################################

sys.modules["PyQt5.Qt"] = sys.modules["aqt.qt"]
# support 'from PyQt5 import Qt', as it's an alias to PyQt6
PyQt6.Qt = sys.modules["aqt.qt"]
