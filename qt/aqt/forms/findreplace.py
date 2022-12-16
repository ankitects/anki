from aqt.qt import qtmajor

if qtmajor > 5:
    from _aqt.forms.findreplace_qt6 import *
else:
    from _aqt.forms.findreplace_qt5 import *  # type: ignore
