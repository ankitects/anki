# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
import ankiqt.forms

save = QMessageBox.Save
discard = QMessageBox.Discard
cancel = QMessageBox.Cancel
def ask(parent):
    return QMessageBox.question(
        parent, "Anki",
        _("""<h1>Unsaved changes</h1>There are unsaved
        changes. Would you like to save them, discard your
        changes, or cancel?"""),
        save | discard | cancel)
