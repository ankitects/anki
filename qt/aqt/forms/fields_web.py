from aqt.qt import qtmajor
if qtmajor > 5:
  from .fields_web_qt6 import *
else:
  from .fields_web_qt5 import *  # type: ignore
