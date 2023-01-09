from aqt.qt import qtmajor

if qtmajor > 5:
    from _aqt.forms.customstudy_qt6 import *
else:
    from _aqt.forms.customstudy_qt5 import *  # type: ignore
