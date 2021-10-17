from aqt.qt import qtmajor
if qtmajor > 5:
  from .studydeck_qt6 import *
else:
  from .studydeck_qt5 import *  # type: ignore
