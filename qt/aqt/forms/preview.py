from aqt.qt import qtmajor
if qtmajor > 5:
  from .preview_qt6 import *
else:
  from .preview_qt5 import *  # type: ignore
