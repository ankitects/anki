from aqt.qt import qtmajor
if qtmajor > 5:
  from .importing_qt6 import *
else:
  from .importing_qt5 import *  # type: ignore
