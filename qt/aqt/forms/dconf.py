from aqt.qt import qtmajor
if qtmajor > 5:
  from .dconf_qt6 import *
else:
  from .dconf_qt5 import *  # type: ignore
