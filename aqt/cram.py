# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

    def onCram(self, cardIds=[]):
        te = aqt.tagedit.TagEdit(self)
        te.setDeck(self.deck, "all")
        diag = GetTextDialog(
            self, _("Tags to cram:"), help="CramMode", edit=te)
        l = diag.layout()
        g = QGroupBox(_("Review Mode"))
        l.insertWidget(2, g)
        box = QVBoxLayout()
        g.setLayout(box)
        keep = QRadioButton(_("Show oldest modified first"))
        box.addWidget(keep)
        keep.setChecked(True)
        diag.setTabOrder(diag.l, keep)
        order = QRadioButton(_("Show in order added"))
        box.addWidget(order)
        random = QRadioButton(_("Show in random order"))
        box.addWidget(random)
        # hide tag list if we have ids
        if cardIds:
            diag.l.hide()
            diag.qlabel.hide()
        if diag.exec_():
            if keep.isChecked():
                order = "type, modified"
            elif order.isChecked():
                order = "created"
            else:
                order = "random()"
            if cardIds:
                active = cardIds
            else:
                active = unicode(diag.l.text())
            self.deck.setupCramScheduler(active, order)
            if self.state == "studyScreen":
                self.onStartReview()
            else:
                self.deck.reset()
                self.deck.getCard() # so scheduler will reset if empty
                self.moveToState("initial")
            if not self.deck.finishScheduler:
                showInfo(_("No cards matched the provided tags."))
