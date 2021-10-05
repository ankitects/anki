from aqt.qt import qtmajor
if qtmajor > 5:
  from .addmodel_qt6 import *
else:
  from .addmodel_qt5 import *  # type: ignore
