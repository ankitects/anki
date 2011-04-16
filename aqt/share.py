# to be moved into libanki

    def _copyToTmpDeck(self, name="cram.anki", tags="", ids=[]):
        # fixme: use namedtmp
        ndir = tempfile.mkdtemp(prefix="anki")
        path = os.path.join(ndir, name)
        from anki.exporting import AnkiExporter
        e = AnkiExporter(self.deck)
        e.includeMedia = False
        if tags:
            e.limitTags = parseTags(tags)
        if ids:
            e.limitCardIds = ids
        path = unicode(path, sys.getfilesystemencoding())
        e.exportInto(path)
        return (e, path)

    def onShare(self, tags):
        pwd = os.getcwd()
        # open tmp deck
        (e, path) = self._copyToTmpDeck(name="shared.anki", tags=tags)
        if not e.exportedCards:
            showInfo(_("No cards matched the provided tags."))
            return
        self.deck.startProgress()
        self.deck.updateProgress()
        d = DeckStorage.Deck(path, backup=False)
        # reset scheduling to defaults
        d.newCardsPerDay = 20
        d.delay0 = 600
        d.delay1 = 0
        d.delay2 = 0
        d.hardIntervalMin = 1.0
        d.hardIntervalMax = 1.1
        d.midIntervalMin = 3.0
        d.midIntervalMax = 5.0
        d.easyIntervalMin = 7.0
        d.easyIntervalMax = 9.0
        d.syncName = None
        d.setVar("newActive", u"")
        d.setVar("newInactive", u"")
        d.setVar("revActive", u"")
        d.setVar("revInactive", u"")
        self.deck.updateProgress()
        # unsuspend cards
        d.unsuspendCards(d.s.column0("select id from cards where type < 0"))
        self.deck.updateProgress()
        d.utcOffset = -2
        d.flushMod()
        d.save()
        self.deck.updateProgress()
        # media
        d.s.statement("update deckVars set value = '' where key = 'mediaURL'")
        self.deck.updateProgress()
        d.s.statement("vacuum")
        self.deck.updateProgress()
        nfacts = d.factCount
        mdir = self.deck.mediaDir()
        d.close()
        dir = os.path.dirname(path)
        zippath = os.path.join(dir, "shared-%d.zip" % time.time())
        # zip it up
        zip = zipfile.ZipFile(zippath, "w", zipfile.ZIP_DEFLATED)
        zip.writestr("facts", str(nfacts))
        zip.writestr("version", str(2))
        readmep = os.path.join(dir, "README.html")
        readme = open(readmep, "w")
        readme.write('''\
<html><body>
This is an exported packaged deck created by Anki.<p>

To share this deck with other people, upload it to
<a href="http://ankiweb.net/file/upload">
http://ankiweb.net/file/upload</a>, or email
it to your friends.
</body></html>''')
        readme.close()
        zip.write(readmep, "README.html")
        zip.write(path, "shared.anki")
        if mdir:
            for f in os.listdir(mdir):
                zip.write(os.path.join(mdir, f),
                          os.path.join("shared.media/", f))
            os.chdir(pwd)
        os.chdir(pwd)
        self.deck.updateProgress()
        zip.close()
        os.unlink(path)
        self.deck.finishProgress()
        self.onOpenPluginFolder(dir)
