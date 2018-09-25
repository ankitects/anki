# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# make sure not to optimize imports on this file
# pylint: disable=unused-import

import os
import sys

from pdb import set_trace, pm
from traceback import format_exception

# Import these for use in other modules
# Separate GUI Object from other objects
from PyQt5.Qt import (  # noqa
    PYQT_VERSION_STR,
    QT_VERSION,
    QT_VERSION_STR,
    qInstallMessageHandler,
)
from PyQt5.Qt import (  # noqa pylint: disable=no-name-in-module
    QAbstractItemView,
    QAbstractTableModel,
    QAction,
    QApplication,
    QBrush,
    QByteArray,
    QCheckBox,
    QClipboard,
    QColor,
    QColorDialog,
    QComboBox,
    QCompleter,
    QCoreApplication,
    QCursor,
    QDesktopServices,
    QDialog,
    QDialogButtonBox,
    QDockWidget,
    QEvent,
    QEventLoop,
    QFile,
    QFileDialog,
    QFont,
    QFontDatabase,
    QFrame,
    QGridLayout,
    QGroupBox,
    QHBoxLayout,
    QHeaderView,
    QIODevice,
    QIcon,
    QImage,
    QItemDelegate,
    QItemSelection,
    QItemSelectionModel,
    QKeySequence,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QLocalServer,
    QLocalSocket,
    QMainWindow,
    QMenu,
    QMessageBox,
    QNativeGestureEvent,
    QNetworkProxy,
    QObject,
    QOffscreenSurface,
    QOpenGLContext,
    QOpenGLVersionProfile,
    QPalette,
    QPixmap,
    QPoint,
    QPushButton,
    QShortcut,
    QSizePolicy,
    QStandardPaths,
    QStringListModel,
    QTextBrowser,
    QTextCursor,
    QTextEdit,
    QThread,
    QTimer,
    QTranslator,
    QTreeWidget,
    QTreeWidgetItem,
    QUrl,
    QVBoxLayout,
    QWebChannel,
    QWidget,
    QSize,
    Qt,
)
from PyQt5.QtCore import (  # noqa pylint: disable=no-name-in-module
    pyqtRemoveInputHook,
    pyqtSignal,
    pyqtSlot,
)

try:
    from PyQt5 import sip  # noqa pylint: disable=no-name-in-module
except ImportError:
    import sip  # noqa pylint: disable=no-name-in-module

# trigger explicit message in case of missing libraries
# instead of silently failing to import
from PyQt5.QtWebEngineWidgets import (  # noqa pylint: disable=no-name-in-module
    QWebEnginePage,
    QWebEngineProfile,
    QWebEngineScript,
    QWebEngineView,
)

# fix buggy ubuntu12.04 display of language selector
os.environ["LIBOVERLAY_SCROLLBAR"] = "0"


def debug():
    pyqtRemoveInputHook()
    set_trace()


if os.environ.get("DEBUG"):
    def info(type, value, tb):
        for line in format_exception(type, value, tb):
            sys.stdout.write(line)
        pyqtRemoveInputHook()
        pm()
    sys.excepthook = info

qtmajor = (QT_VERSION & 0xff0000) >> 16
qtminor = (QT_VERSION & 0x00ff00) >> 8
qtpoint = QT_VERSION & 0xff

if qtmajor != 5 or qtminor < 9 or qtminor == 10:
    raise Exception("Anki does not support your Qt version.")

# GUI code assumes python 3.6+
if sys.version_info[0] < 3 or sys.version_info[1] < 6:
    raise Exception("Anki requires Python 3.6+")
