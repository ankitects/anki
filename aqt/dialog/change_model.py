import aqt
import anki.hooks
from anki.notes import Note
from aqt.qt import QDialog, Qt, QHBoxLayout, QWidget, QGridLayout, QComboBox, QLabel

import aqt.utils


class ChangeModelDialog(QDialog):
    """
    Dialog that allows user to create field and template maps from one note model to another
    """

    def __init__(self, collection, note_id_list, parent=None):
        QDialog.__init__(self, parent)
        self.collection = collection
        self.note_id_list = note_id_list
        first_note = Note(collection, id=note_id_list[0])
        self.old_model = first_note.model()

        self.form = aqt.forms.changemodel.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowModality(Qt.WindowModal)
        self.setup()

        aqt.utils.restoreGeom(self, "changeModel")
        anki.hooks.addHook("reset", self.on_reset)
        anki.hooks.addHook("currentModelChanged", self.on_reset)
        self.exec_()

    def setup(self):
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
        self.form.oldModelLabel.setText(self.old_model['name'])
        self.modelChooser = aqt.modelchooser.ModelChooser(
            aqt.mw, self.form.modelChooserWidget, label=False)
        self.modelChooser.models.setFocus()
        self.form.buttonBox.helpRequested.connect(self.on_help)
        self.model_changed(self.collection.models.current())
        self.pauseUpdate = False

    def on_reset(self):
        self.model_changed(self.collection.models.current())

    def model_changed(self, model):
        self.targetModel = model
        self.rebuildTemplateMap()
        self.rebuildFieldMap()

    def rebuildTemplateMap(self, key=None, attr=None):
        if not key:
            key = "t"
            attr = "tmpls"
        model_map = getattr(self, key + "widg")
        lay = getattr(self, key + "layout")
        src = self.old_model[attr]
        dst = self.targetModel[attr]
        if model_map:
            lay.removeWidget(model_map)
            model_map.deleteLater()
            setattr(self, key + "MapWidget", None)
        model_map = QWidget()
        l = QGridLayout()
        combos = []
        targets = [x['name'] for x in dst] + [_("Nothing")]
        indices = {}
        for i, x in enumerate(src):
            l.addWidget(QLabel(_("Change %s to:") % x['name']), i, 0)
            cb = QComboBox()
            cb.addItems(targets)
            idx = min(i, len(targets) - 1)
            cb.setCurrentIndex(idx)
            indices[cb] = idx
            cb.currentIndexChanged.connect(
                lambda entry_id: self.onComboChanged(entry_id, cb, key))
            combos.append(cb)
            l.addWidget(cb, i, 1)
        model_map.setLayout(l)
        lay.addWidget(model_map)
        setattr(self, key + "widg", model_map)
        setattr(self, key + "layout", lay)
        setattr(self, key + "combos", combos)
        setattr(self, key + "indices", indices)

    def rebuildFieldMap(self):
        return self.rebuildTemplateMap(key="f", attr="flds")

    def onComboChanged(self, i, cb, key):
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

    def get_template_map(self, old=None, combos=None, new=None):
        if not old:
            old = self.old_model['tmpls']
            combos = self.tcombos
            new = self.targetModel['tmpls']
        model_map = {}
        for i, f in enumerate(old):
            idx = combos[i].currentIndex()
            if idx == len(new):
                # ignore
                model_map[f['ord']] = None
            else:
                f2 = new[idx]
                model_map[f['ord']] = f2['ord']
        return model_map

    def get_field_map(self):
        return self.get_template_map(
            old=self.old_model['flds'],
            combos=self.fcombos,
            new=self.targetModel['flds'])

    def cleanup(self):
        anki.hooks.remHook("reset", self.on_reset)
        anki.hooks.remHook("currentModelChanged", self.on_reset)
        self.modelChooser.cleanup()
        aqt.utils.saveGeom(self, "changeModel")

    def reject(self):
        self.cleanup()
        return QDialog.reject(self)

    def accept(self):
        # check maps
        field_map = self.get_field_map()
        templates_map = self.get_template_map()
        if any(True for template in list(templates_map.values()) if template is None):
            if not aqt.utils.askUser(_(
                    "Any cards mapped to nothing will be deleted. "
                    "If a note has no remaining cards, it will be lost. "
                    "Are you sure you want to continue?")):
                return

        QDialog.accept(self)

        self.collection.models.change(self.old_model, self.note_id_list, self.targetModel, field_map, templates_map)

        self.cleanup()

    @staticmethod
    def on_help():
        aqt.utils.openHelp("browsermisc")
