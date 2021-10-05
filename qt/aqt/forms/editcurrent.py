from aqt.qt import qtmajor
if qtmajor > 5:
  from .editcurrent_qt6 import *
else:
  from .editcurrent_qt5 import *  # type: ignore
