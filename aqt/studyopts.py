
    def _studyScreenState(self, oldState):
        self.currentCard = None
        # if self.deck.finishScheduler:
        #     self.deck.finishScheduler()
        self.disableCardMenuItems()
        self.showStudyScreen()

    # Study screen
    ##########################################################################

    def setupStudyScreen(self):
        return
        self.form.buttonStack.hide()
        self.form.newCardOrder.insertItems(
            0, QStringList(anki.consts.newCardOrderLabels().values()))
        self.form.newCardScheduling.insertItems(
            0, QStringList(anki.consts.newCardSchedulingLabels().values()))
        self.form.revCardOrder.insertItems(
            0, QStringList(anki.consts.revCardOrderLabels().values()))
        self.connect(self.form.optionsHelpButton,
                     SIGNAL("clicked()"),
                     lambda: QDesktopServices.openUrl(QUrl(
            aqt.appWiki + "StudyOptions")))
        self.connect(self.form.minuteLimit,
                     SIGNAL("textChanged(QString)"), self.onMinuteLimitChanged)
        self.connect(self.form.questionLimit,
                     SIGNAL("textChanged(QString)"), self.onQuestionLimitChanged)
        self.connect(self.form.newPerDay,
                     SIGNAL("textChanged(QString)"), self.onNewLimitChanged)
        self.connect(self.form.startReviewingButton,
                     SIGNAL("clicked()"),
                     self.onStartReview)
        self.connect(self.form.newCardOrder,
                     SIGNAL("activated(int)"), self.onNewCardOrderChanged)
        self.connect(self.form.failedCardMax,
                     SIGNAL("editingFinished()"),
                     self.onFailedMaxChanged)
        self.connect(self.form.newCategories,
                     SIGNAL("clicked()"), self.onNewCategoriesClicked)
        self.connect(self.form.revCategories,
                     SIGNAL("clicked()"), self.onRevCategoriesClicked)
        self.form.tabWidget.setCurrentIndex(self.config['studyOptionsTab'])

    def onNewCategoriesClicked(self):
        aqt.activetags.show(self, "new")

    def onRevCategoriesClicked(self):
        aqt.activetags.show(self, "rev")

    def onFailedMaxChanged(self):
        try:
            v = int(self.form.failedCardMax.text())
            if v == 1 or v < 0:
                v = 2
            self.deck.failedCardMax = v
        except ValueError:
            pass
        self.form.failedCardMax.setText(str(self.deck.failedCardMax))
        self.deck.flushMod()

    def onMinuteLimitChanged(self, qstr):
        try:
            val = float(self.form.minuteLimit.text()) * 60
            if self.deck.sessionTimeLimit == val:
                return
            self.deck.sessionTimeLimit = val
        except ValueError:
            pass
        self.deck.flushMod()
        self.updateStudyStats()

    def onQuestionLimitChanged(self, qstr):
        try:
            val = int(self.form.questionLimit.text())
            if self.deck.sessionRepLimit == val:
                return
            self.deck.sessionRepLimit = val
        except ValueError:
            pass
        self.deck.flushMod()
        self.updateStudyStats()

    def onNewLimitChanged(self, qstr):
        try:
            val = int(self.form.newPerDay.text())
            if self.deck.newCardsPerDay == val:
                return
            self.deck.newCardsPerDay = val
        except ValueError:
            pass
        self.deck.flushMod()
        self.deck.reset()
        self.statusView.redraw()
        self.updateStudyStats()

    def onNewCardOrderChanged(self, ncOrd):
        def uf(obj, field, value):
            if getattr(obj, field) != value:
                setattr(obj, field, value)
                self.deck.flushMod()
        if ncOrd != 0:
            if self.deck.newCardOrder == 0:
                # need to put back in order
                self.mw.startProgress()
                self.mw.updateProgress(_("Ordering..."))
                self.deck.orderNewCards()
                self.deck.finishProgress()
            uf(self.deck, 'newCardOrder', ncOrd)
        elif ncOrd == 0:
            # (re-)randomize
            self.deck.startProgress()
            self.deck.updateProgress(_("Randomizing..."))
            self.deck.randomizeNewCards()
            self.deck.finishProgress()
            uf(self.deck, 'newCardOrder', ncOrd)

    def updateActives(self):
        labels = [
            _("Show All Due Cards"),
            _("Show Chosen Categories")
            ]
        if self.deck.getVar("newActive") or self.deck.getVar("newInactive"):
            new = labels[1]
        else:
            new = labels[0]
        self.form.newCategoryLabel.setText(new)
        if self.deck.getVar("revActive") or self.deck.getVar("revInactive"):
            rev = labels[1]
        else:
            rev = labels[0]
        self.form.revCategoryLabel.setText(rev)

    def updateStudyStats(self):
        self.form.buttonStack.hide()
        self.deck.reset()
        self.updateActives()
        wasReached = self.deck.timeboxReached()
        sessionColour = '<font color=#0000ff>%s</font>'
        cardColour = '<font color=#0000ff>%s</font>'
        # top label
        h = {}
        h['ret'] = cardColour % (self.deck.revCount+self.deck.failedSoonCount)
        h['new'] = cardColour % self.deck.newCount
        h['newof'] = str(self.deck.newCountAll())
        # counts & time for today
        todayStart = self.deck.failedCutoff - 86400
        sql = "select count(), sum(userTime) from revlog"
        (reps, time_) = self.deck.db.first(
            sql + " where time > :start", start=todayStart)
        h['timeToday'] = sessionColour % (
            anki.utils.fmtTimeSpan(time_ or 0, short=True, point=1))
        h['repsToday'] = sessionColour % reps
        # and yesterday
        yestStart = todayStart - 86400
        (reps, time_) = self.deck.db.first(
            sql + " where time > :start and time <= :end",
            start=yestStart, end=todayStart)
        h['timeTodayChg'] = str(
            anki.utils.fmtTimeSpan(time_ or 0, short=True, point=1))
        h['repsTodayChg'] = str(reps)
        # session counts
        limit = self.deck.sessionTimeLimit
        start = self.deck.sessionStartTime or time.time() - limit
        start2 = self.deck.lastSessionStart or start - limit
        last10 = self.deck.db.scalar(
            "select count(*) from revlog where time >= :t",
            t=start)
        last20 = self.deck.db.scalar(
            "select count(*) from revlog where "
            "time >= :t and time < :t2",
            t=start2, t2=start)
        h['repsInSes'] = sessionColour % last10
        h['repsInSesChg'] = str(last20)
        h['cs_header'] = "<b>" + _("Cards/session:") + "</b>"
        h['cd_header'] = "<b>" + _("Cards/day:") + "</b>"
        h['td_header'] = "<b>" + _("Time/day:") + "</b>"
        h['rd_header'] = "<b>" + _("Reviews due:") + "</b>"
        h['ntod_header'] = "<b>" + _("New today:") + "</b>"
        h['ntot_header'] = "<b>" + _("New total:") + "</b>"
        stats1 = ("""\
<table>
<tr><td width=150>%(cs_header)s</td><td width=50><b>%(repsInSesChg)s</b></td>
<td><b>%(repsInSes)s</b></td></tr></table>
<hr>
<table>
<tr><td width=150>
%(cd_header)s</td><td width=50><b>%(repsTodayChg)s</b></td>
<td><b>%(repsToday)s</b></td></tr>
<tr><td>%(td_header)s</td><td><b>%(timeTodayChg)s</b></td>
<td><b>%(timeToday)s</b></td></tr>
</table>""") % h

        stats2 = ("""\
<table>
<tr><td width=180>%(rd_header)s</td><td align=right><b>%(ret)s</b></td></tr>
<tr><td>%(ntod_header)s</td><td align=right><b>%(new)s</b></td></tr>
<tr><td>%(ntot_header)s</td><td align=right>%(newof)s</td></tr>
</table>""") % h
        self.form.optionsLabel.setText("""\
<p><table><tr>
%s
</tr><tr>
<td><hr>%s<hr></td></tr></table>""" % (stats1, stats2))
        h['tt_header'] = _("Session Statistics")
        h['cs_tip'] = _("The number of cards you studied in the current \
session (blue) and previous session (black)")
        h['cd_tip'] = _("The number of cards you studied today (blue) and \
yesterday (black)")
        h['td_tip'] = _("The number of minutes you studied today (blue) and \
yesterday (black)")
        h['rd_tip'] = _("The number of cards that are waiting to be reviewed \
today")
        h['ntod_tip'] = _("The number of new cards that are waiting to be \
learnt today")
        h['ntot_tip'] = _("The total number of new cards in the deck")
        statToolTip = ("""<h1>%(tt_header)s</h1>
<dl><dt><b>%(cs_header)s</b></dt><dd>%(cs_tip)s</dd></dl>
<dl><dt><b>%(cd_header)s</b></dt><dd>%(cd_tip)s</dd></dl>
<dl><dt><b>%(td_header)s</b></dt><dd>%(td_tip)s</dd></dl>
<dl><dt><b>%(rd_header)s</b></dt><dd>%(rd_tip)s</dd></dl>
<dl><dt><b>%(ntod_header)s</b></dt><dd>%(ntod_tip)s</dd></dl>
<dl><dt><b>%(ntot_header)s</b></dt><dd>%(ntot_tip)s<</dd></dl>""") % h

        self.form.optionsLabel.setToolTip(statToolTip)

    def showStudyScreen(self):
        # forget last card
        self.lastCard = None
        self.switchToStudyScreen()
        self.updateStudyStats()
        self.form.startReviewingButton.setFocus()
        self.setupStudyOptions()
        self.form.studyOptionsFrame.setMaximumWidth(500)
        self.form.studyOptionsFrame.show()

    def setupStudyOptions(self):
        self.form.newPerDay.setText(str(self.deck.newCardsPerDay))
        lim = self.deck.sessionTimeLimit/60
        if int(lim) == lim:
            lim = int(lim)
        self.form.minuteLimit.setText(str(lim))
        self.form.questionLimit.setText(str(self.deck.sessionRepLimit))
        self.form.newCardOrder.setCurrentIndex(self.deck.newCardOrder)
        self.form.newCardScheduling.setCurrentIndex(self.deck.newCardSpacing)
        self.form.revCardOrder.setCurrentIndex(self.deck.revCardOrder)
        self.form.failedCardsOption.clear()
        if self.deck.getFailedCardPolicy() == 5:
            labels = failedCardOptionLabels().values()
        else:
            labels = failedCardOptionLabels().values()[0:-1]
        self.form.failedCardsOption.insertItems(0, labels)
        self.form.failedCardsOption.setCurrentIndex(self.deck.getFailedCardPolicy())
        self.form.failedCardMax.setText(unicode(self.deck.failedCardMax))

    def onStartReview(self):
        def uf(obj, field, value):
            if getattr(obj, field) != value:
                setattr(obj, field, value)
                self.deck.flushMod()
        self.form.studyOptionsFrame.hide()
        # make sure the size is updated before button stack shown
        self.app.processEvents()
        uf(self.deck, 'newCardSpacing',
           self.form.newCardScheduling.currentIndex())
        uf(self.deck, 'revCardOrder',
           self.form.revCardOrder.currentIndex())
        pol = self.deck.getFailedCardPolicy()
        if (pol != 5 and pol !=
            self.form.failedCardsOption.currentIndex()):
            self.deck.setFailedCardPolicy(
                self.form.failedCardsOption.currentIndex())
            self.deck.flushMod()
        self.deck.reset()
        if not self.deck.finishScheduler:
            self.deck.startTimebox()
        self.config['studyOptionsTab'] = self.form.tabWidget.currentIndex()
        self.moveToState("getQuestion")

    def onStudyOptions(self):
        if self.state == "studyScreen":
            pass
        else:
            self.moveToState("studyScreen")
