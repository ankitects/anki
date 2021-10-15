from aqt.qt import qtmajor
if qtmajor > 5:
  from .addons_qt6 import *
else:
  from .addons_qt5 import *  # type: ignore
