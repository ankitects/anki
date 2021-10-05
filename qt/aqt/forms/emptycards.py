from aqt.qt import qtmajor
if qtmajor > 5:
  from .emptycards_qt6 import *
else:
  from .emptycards_qt5 import *  # type: ignore
