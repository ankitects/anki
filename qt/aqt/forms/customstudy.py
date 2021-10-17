from aqt.qt import qtmajor
if qtmajor > 5:
  from .customstudy_qt6 import *
else:
  from .customstudy_qt5 import *  # type: ignore
