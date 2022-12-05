from aqt.qt import qtmajor

if qtmajor > 5:
    from _aqt.forms.finddupes_qt6 import *
else:
    from _aqt.forms.finddupes_qt5 import *  # type: ignore
