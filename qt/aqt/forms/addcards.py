from aqt.qt import qtmajor
if qtmajor > 5:
  from .addcards_qt6 import *
else:
  from .addcards_qt5 import *  # type: ignore
