# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Dict, List, Optional, Sequence

import aqt
from anki.consts import *
from anki.models import NotetypeDict
from anki.notes import NoteId
from aqt import QWidget, gui_hooks
from aqt.qt import *
from aqt.utils import (
    HelpPage,
    askUser,
    disable_help_button,
    openHelp,
    qconnect,
    restoreGeom,
    saveGeom,
    tr,
)


class ChangeModel(QDialog):
    def __init__(self, browser: aqt.browser.Browser, nids: Sequence[NoteId]) -> None:
        QDialog.__init__(self, browser)
        self.browser = browser
        self.nids = nids
        self.oldModel = browser.card.note().model()
        self.form = aqt.forms.changemodel.Ui_Dialog()
        self.form.setupUi(self)
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModal)
        # ugh - these are set dynamically by rebuildTemplateMap()
        self.tcombos: List[QComboBox] = []
        self.fcombos: List[QComboBox] = []
        self.setup()
        restoreGeom(self, "changeModel")
        gui_hooks.state_did_reset.append(self.onReset)
        gui_hooks.current_note_type_did_change.append(self.on_note_type_change)
        self.exec_()

    def on_note_type_change(self, notetype: NotetypeDict) -> None:
        self.onReset()

    def setup(self) -> None:
        # maps
        self.flayout = QHBoxLayout()
        self.flayout.setContentsMargins(0, 0, 0, 0)
        self.fwidg = None
        self.form.fieldMap.setLayout(self.flayout)
        self.tlayout = QHBoxLayout()
        self.tlayout.setContentsMargins(0, 0, 0, 0)
        self.twidg = None
        self.form.templateMap.setLayout(self.tlayout)
        if self.style().objectName() == "gtk+":
            # gtk+ requires margins in inner layout
            self.form.verticalLayout_2.setContentsMargins(0, 11, 0, 0)
            self.form.verticalLayout_3.setContentsMargins(0, 11, 0, 0)
        # model chooser
        import aqt.modelchooser

        self.oldModel = self.browser.col.models.get(
            self.browser.col.db.scalar(
                "select mid from notes where id = ?", self.nids[0]
            )
        )
        self.form.oldModelLabel.setText(self.oldModel["name"])
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.browser.mw, self.form.modelChooserWidget, label=False
        )
        self.modelChooser.models.setFocus()
        qconnect(self.form.buttonBox.helpRequested, self.onHelp)
        self.modelChanged(self.browser.mw.col.models.current())
        self.pauseUpdate = False

    def onReset(self) -> None:
        self.modelChanged(self.browser.col.models.current())

    def modelChanged(self, model: Dict[str, Any]) -> None:
        self.targetModel = model
        self.rebuildTemplateMap()
        self.rebuildFieldMap()

    def rebuildTemplateMap(
        self, key: Optional[str] = None, attr: Optional[str] = None
    ) -> None:
        if not key:
            key = "t"
            attr = "tmpls"
        map = getattr(self, key + "widg")
        lay = getattr(self, key + "layout")
        src = self.oldModel[attr]
        dst = self.targetModel[attr]
        if map:
            lay.removeWidget(map)
            map.deleteLater()
            setattr(self, key + "MapWidget", None)
        map = QWidget()
        l = QGridLayout()
        combos = []
        targets = [x["name"] for x in dst] + [tr.browsing_nothing()]
        indices = {}
        for i, x in enumerate(src):
            l.addWidget(QLabel(tr.browsing_change_to(val=x["name"])), i, 0)
            cb = QComboBox()
            cb.addItems(targets)
            idx = min(i, len(targets) - 1)
            cb.setCurrentIndex(idx)
            indices[cb] = idx
            qconnect(
                cb.currentIndexChanged,
                lambda i, cb=cb, key=key: self.onComboChanged(i, cb, key),
            )
            combos.append(cb)
            l.addWidget(cb, i, 1)
        map.setLayout(l)
        lay.addWidget(map)
        setattr(self, key + "widg", map)
        setattr(self, key + "layout", lay)
        setattr(self, key + "combos", combos)
        setattr(self, key + "indices", indices)

    def rebuildFieldMap(self) -> None:
        return self.rebuildTemplateMap(key="f", attr="flds")

    def onComboChanged(self, i: int, cb: QComboBox, key: str) -> None:
        indices = getattr(self, key + "indices")
        if self.pauseUpdate:
            indices[cb] = i
            return
        combos = getattr(self, key + "combos")
        if i == cb.count() - 1:
            # set to 'nothing'
            return
        # find another combo with same index
        for c in combos:
            if c == cb:
                continue
            if c.currentIndex() == i:
                self.pauseUpdate = True
                c.setCurrentIndex(indices[cb])
                self.pauseUpdate = False
                break
        indices[cb] = i

    def getTemplateMap(
        self,
        old: Optional[List[Dict[str, Any]]] = None,
        combos: Optional[List[QComboBox]] = None,
        new: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[int, Optional[int]]:
        if not old:
            old = self.oldModel["tmpls"]
            combos = self.tcombos
            new = self.targetModel["tmpls"]
        template_map: Dict[int, Optional[int]] = {}
        for i, f in enumerate(old):
            idx = combos[i].currentIndex()
            if idx == len(new):
                # ignore
                template_map[f["ord"]] = None
            else:
                f2 = new[idx]
                template_map[f["ord"]] = f2["ord"]
        return template_map

    def getFieldMap(self) -> Dict[int, Optional[int]]:
        return self.getTemplateMap(
            old=self.oldModel["flds"], combos=self.fcombos, new=self.targetModel["flds"]
        )

    def cleanup(self) -> None:
        gui_hooks.state_did_reset.remove(self.onReset)
        gui_hooks.current_note_type_did_change.remove(self.on_note_type_change)
        self.modelChooser.cleanup()
        saveGeom(self, "changeModel")

    def reject(self) -> None:
        self.cleanup()
        return QDialog.reject(self)

    def accept(self) -> None:
        # check maps
        fmap = self.getFieldMap()
        cmap = self.getTemplateMap()
        if any(True for c in list(cmap.values()) if c is None):
            if not askUser(tr.browsing_any_cards_mapped_to_nothing_will()):
                return
        self.browser.mw.checkpoint(tr.browsing_change_note_type())
        b = self.browser
        b.mw.col.modSchema(check=True)
        b.mw.progress.start()
        b.begin_reset()
        mm = b.mw.col.models
        mm.change(self.oldModel, list(self.nids), self.targetModel, fmap, cmap)
        b.search()
        b.end_reset()
        b.mw.progress.finish()
        b.mw.reset()
        self.cleanup()
        QDialog.accept(self)

    def onHelp(self) -> None:
        openHelp(HelpPage.BROWSING_OTHER_MENU_ITEMS)
