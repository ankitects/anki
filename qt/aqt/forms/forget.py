from aqt.qt import qtmajor

if qtmajor > 5:
    from .forget_qt6 import *
else:
    from .forget_qt5 import *  # type: ignore
