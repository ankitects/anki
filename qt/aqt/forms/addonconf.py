from aqt.qt import qtmajor
if qtmajor > 5:
  from .addonconf_qt6 import *
else:
  from .addonconf_qt5 import *  # type: ignore
