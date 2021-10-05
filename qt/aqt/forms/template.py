from aqt.qt import qtmajor
if qtmajor > 5:
  from .template_qt6 import *
else:
  from .template_qt5 import *  # type: ignore
