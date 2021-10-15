from aqt.qt import qtmajor
if qtmajor > 5:
  from .edithtml_qt6 import *
else:
  from .edithtml_qt5 import *  # type: ignore
