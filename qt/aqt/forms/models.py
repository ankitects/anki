from aqt.qt import qtmajor
if qtmajor > 5:
  from .models_qt6 import *
else:
  from .models_qt5 import *  # type: ignore
