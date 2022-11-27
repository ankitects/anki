from aqt.qt import qtmajor

if qtmajor > 5:
    from _aqt.forms.taglimit_qt6 import *
else:
    from _aqt.forms.taglimit_qt5 import *  # type: ignore
