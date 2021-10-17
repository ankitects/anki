from aqt.qt import qtmajor
if qtmajor > 5:
  from .fields_qt6 import *
else:
  from .fields_qt5 import *  # type: ignore
