from aqt.qt import qtmajor
if qtmajor > 5:
  from .editaddon_qt6 import *
else:
  from .editaddon_qt5 import *  # type: ignore
