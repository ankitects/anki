from aqt.qt import qtmajor
if qtmajor > 5:
  from .finddupes_qt6 import *
else:
  from .finddupes_qt5 import *  # type: ignore
