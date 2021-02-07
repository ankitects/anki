import re
import sys
import io
from PyQt5.uic import compileUi

ui_file = sys.argv[1]
py_file = sys.argv[2]
buf = io.StringIO()
compileUi(open(ui_file), buf, from_imports=True)

outdata = buf.getvalue()
outdata = outdata.replace(
    "# -*- coding: utf-8 -*-", "# -*- coding: utf-8 -*-\nfrom aqt.utils import tr, TR\n"
)
outdata = re.sub(
    r'(?:QtGui\.QApplication\.)?_?translate\(".*?", "(.*?)"', "tr(TR.\\1", outdata
)

with open(py_file, "w") as file:
    file.write(outdata)
