from aqt.qt import qtmajor
if qtmajor > 5:
  from .setgroup_qt6 import *
else:
  from .setgroup_qt5 import *  # type: ignore
