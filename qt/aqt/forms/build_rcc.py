import sys
import os
from PyQt5.pyrcc_main import processResourceFile

icons_qrc = sys.argv[1]
py_file = os.path.abspath(sys.argv[2])

# make paths relative for pyrcc
os.chdir(os.path.dirname(icons_qrc))
icons_qrc = os.path.basename(icons_qrc)

processResourceFile([icons_qrc], py_file, False)
