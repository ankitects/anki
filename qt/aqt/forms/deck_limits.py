from aqt.qt import qtmajor

if qtmajor > 5:
    from .deck_limits_qt6 import *
else:
    from .deck_limits_qt5 import *  # type: ignore
