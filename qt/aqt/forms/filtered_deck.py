from aqt.qt import qtmajor
if qtmajor > 5:
  from .filtered_deck_qt6 import *
else:
  from .filtered_deck_qt5 import *  # type: ignore
