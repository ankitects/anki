from aqt.qt import qtmajor
if qtmajor > 5:
  from .changemodel_qt6 import *
else:
  from .changemodel_qt5 import *  # type: ignore
