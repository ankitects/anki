# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/copyleft/agpl.html

import os, time, simplejson, re, datetime, shutil
from anki.lang import _
from anki.utils import intTime, tmpfile, ids2str, splitFields
from anki.db import DB
from anki.deck import _Deck
from anki.consts import *
from anki.storage import _addSchema, _getDeckVars, _addDeckVars, \
    _updateIndices

#
# Upgrading is the first step in migrating to 2.0. The ids are temporary and
# may not be unique across multiple decks. After each of a user's v1.2 decks
# are upgraded, they need to be merged via the import code.
#
# Caller should have called check() on path before calling upgrade().
#

class Upgrader(object):

    def __init__(self):
        pass

    # Upgrading
    ######################################################################

    def upgrade(self, path):
        self.path = path
        self._openDB(path)
        self._upgradeSchema()
        self._openDeck()
        self._upgradeDeck()
        return self.deck

    # Integrity checking
    ######################################################################

    def check(self, path):
        "True if deck looks ok."
        with DB(path) as db:
            return self._check(db)

    def _check(self, db):
        # corrupt?
        try:
            if db.scalar("pragma integrity_check") != "ok":
                return
        except:
            return
        # old version?
        if db.scalar("select version from decks") != 65:
            return
        # ensure we have indices for checks below
        db.executescript("""
create index if not exists ix_cards_factId on cards (factId);
create index if not exists ix_fields_factId on fieldModels (factId);
analyze;""")
        # fields missing a field model?
        if db.list("""
    select id from fields where fieldModelId not in (
    select distinct id from fieldModels)"""):
            return
        # facts missing a field?
        if db.list("""
    select distinct facts.id from facts, fieldModels where
    facts.modelId = fieldModels.modelId and fieldModels.id not in
    (select fieldModelId from fields where factId = facts.id)"""):
            return
        # cards missing a fact?
        if db.list("""
    select id from cards where factId not in (select id from facts)"""):
            return
        # cards missing a card model?
        if db.list("""
    select id from cards where cardModelId not in
    (select id from cardModels)"""):
            return
        # cards with a card model from the wrong model?
        if db.list("""
    select id from cards where cardModelId not in (select cm.id from
    cardModels cm, facts f where cm.modelId = f.modelId and
    f.id = cards.factId)"""):
            return
        # facts missing a card?
        if db.list("""
    select facts.id from facts
    where facts.id not in (select distinct factId from cards)"""):
            return
        # dangling fields?
        if db.list("""
    select id from fields where factId not in (select id from facts)"""):
            return
        # fields without matching interval
        if db.list("""
    select id from fields where ordinal != (select ordinal from fieldModels
    where id = fieldModelId)"""):
            return
        # incorrect types
        if db.list("""
    select id from cards where relativeDelay != (case
    when successive then 1 when reps then 0 else 2 end)"""):
            return
        if db.list("""
    select id from cards where type != (case
    when type >= 0 then relativeDelay else relativeDelay - 3 end)"""):
            return
        return True

    # DB/Deck opening
    ######################################################################

    def _openDB(self, path):
        self.tmppath = tmpfile(suffix=".anki2")
        shutil.copy(path, self.tmppath)
        self.db = DB(self.tmppath)

    def _openDeck(self):
        self.deck = _Deck(self.db)

    # Schema upgrade
    ######################################################################

    def _upgradeSchema(self):
        "Alter tables prior to ORM initialization."
        db = self.db
        # speed up the upgrade
        db.execute("pragma temp_store = memory")
        db.execute("pragma cache_size = 10000")
        # these weren't always correctly set
        db.execute("pragma page_size = 4096")
        db.execute("pragma legacy_file_format = 0")

        # facts
        ###########
        # tags should have a leading and trailing space if not empty, and not
        # use commas
        db.execute("""
update facts set tags = (case
when trim(tags) == "" then ""
else " " || replace(replace(trim(tags), ",", " "), "  ", " ") || " "
end)
""")
        # pull facts into memory, so we can merge them with fields efficiently
        facts = db.all("""
select id, id, modelId, 1, cast(created*1000 as int), cast(modified as int),
0, tags from facts order by created""")
        # build field hash
        fields = {}
        for (fid, ord, val) in db.execute(
            "select factId, ordinal, value from fields order by factId, ordinal"):
            if fid not in fields:
                fields[fid] = []
            fields[fid].append((ord, val))
        # build insert data and transform ids, and minimize qt's
        # bold/italics/underline cruft.
        map = {}
        data = []
        factidmap = {}
        times = {}
        from anki.utils import minimizeHTML
        for c, row in enumerate(facts):
            oldid = row[0]
            row = list(row)
            # get rid of old created column and update id
            while row[4] in times:
                row[4] += 1000
            times[row[4]] = True
            factidmap[row[0]] = row[4]
            row[0] = row[4]
            del row[4]
            map[oldid] = row[0]
            row.append(minimizeHTML("\x1f".join([x[1] for x in sorted(fields[oldid])])))
            data.append(row)
        # and put the facts into the new table
        db.execute("drop table facts")
        _addSchema(db, False)
        db.executemany("insert into facts values (?,?,?,?,?,?,?,?,'',0,'')", data)
        db.execute("drop table fields")

        # cards
        ###########
        # we need to pull this into memory, to rewrite the creation time if
        # it's not unique and update the fact id
        times = {}
        rows = []
        cardidmap = {}
        for row in db.execute("""
select id, cast(created*1000 as int), factId, ordinal,
cast(modified as int), 0,
(case relativeDelay
when 0 then 1
when 1 then 2
when 2 then 0 end),
(case type
when 0 then 1
when 1 then 2
when 2 then 0
else type end),
cast(due as int), cast(interval as int),
cast(factor*1000 as int), reps, noCount from cards
order by created"""):
            # find an unused time
            row = list(row)
            while row[1] in times:
                row[1] += 1000
            times[row[1]] = True
            # rewrite fact id
            row[2] = factidmap[row[2]]
            # note id change and save all but old id
            cardidmap[row[0]] = row[1]
            rows.append(row[1:])
        # drop old table and rewrite
        db.execute("drop table cards")
        _addSchema(db, False)
        db.executemany("""
insert into cards values (?,?,1,?,?,?,?,?,?,?,?,?,?,0,0,0,"")""",
                       rows)

        # reviewHistory -> revlog
        ###########
        # fetch the data so we can rewrite ids quickly
        r = []
        for row in db.execute("""
select
cast(time*1000 as int), cardId, 0, ease,
cast(nextInterval as int), cast(lastInterval as int),
cast(nextFactor*1000 as int), cast(min(thinkingTime, 60)*1000 as int),
yesCount from reviewHistory"""):
            row = list(row)
            # new card ids
            try:
                row[1] = cardidmap[row[1]]
            except:
                # id doesn't exist
                continue
            # no ease 0 anymore
            row[2] = row[2] or 1
            # determine type, overwriting yesCount
            newInt = row[3]
            oldInt = row[4]
            yesCnt = row[7]
            # yesCnt included the current answer
            if row[2] > 1:
                yesCnt -= 1
            if oldInt < 1:
                # new or failed
                if yesCnt:
                    # type=relrn
                    row[7] = 2
                else:
                    # type=lrn
                    row[7] = 0
            else:
                # type=rev
                row[7] = 1
            r.append(row)
        db.executemany(
            "insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)", r)
        db.execute("drop table reviewHistory")

        # deck
        ###########
        self._migrateDeckTbl()

        # tags
        ###########
        tags = {}
        for t in db.list("select tag from tags"):
            tags[t] = intTime()
        db.execute("update deck set tags = ?", simplejson.dumps(tags))
        db.execute("drop table tags")
        db.execute("drop table cardTags")

        # the rest
        ###########
        db.execute("drop table media")
        db.execute("drop table sources")
        self._migrateModels()
        _updateIndices(db)

    def _migrateDeckTbl(self):
        import anki.deck
        db = self.db
        db.execute("delete from deck")
        db.execute("""
insert or replace into deck select id, cast(created as int), :t,
:t, 99, 0, 0, cast(lastSync as int),
"", "", "", "", "" from decks""", t=intTime())
        # prepare a group to store the old deck options
        g, gc, conf = _getDeckVars(db)
        # delete old selective study settings, which we can't auto-upgrade easily
        keys = ("newActive", "newInactive", "revActive", "revInactive")
        for k in keys:
            db.execute("delete from deckVars where key=:k", k=k)
        # copy other settings, ignoring deck order as there's a new default
        g['newSpread'] = db.scalar("select newCardSpacing from decks")
        g['newPerDay'] = db.scalar("select newCardsPerDay from decks")
        g['repLim'] = db.scalar("select sessionRepLimit from decks")
        g['timeLim'] = db.scalar("select sessionTimeLimit from decks")
        # this needs to be placed in the model later on
        conf['oldNewOrder'] = db.scalar("select newCardOrder from decks")
        # no reverse option anymore
        conf['oldNewOrder'] = min(1, conf['oldNewOrder'])
        # add any deck vars and save
        dkeys = ("hexCache", "cssCache")
        for (k, v) in db.execute("select * from deckVars").fetchall():
            if k in dkeys:
                pass
            else:
                conf[k] = v
        _addDeckVars(db, g, gc, conf)
        # clean up
        db.execute("drop table decks")
        db.execute("drop table deckVars")

    def _migrateModels(self):
        import anki.models
        db = self.db
        times = {}
        mods = {}
        for row in db.all(
            "select id, name from models"):
            while 1:
                t = intTime(1000)
                if t not in times:
                    times[t] = True
                    break
            m = anki.models.defaultModel.copy()
            m['id'] = t
            m['name'] = row[1]
            m['mod'] = intTime()
            m['tags'] = []
            m['flds'] = self._fieldsForModel(row[0])
            m['tmpls'] = self._templatesForModel(row[0], m['flds'])
            mods[m['id']] = m
            db.execute("update facts set mid = ? where mid = ?", t, row[0])
        # save and clean up
        db.execute("update deck set models = ?", simplejson.dumps(mods))
        db.execute("drop table fieldModels")
        db.execute("drop table cardModels")
        db.execute("drop table models")

    def _fieldsForModel(self, mid):
        import anki.models
        db = self.db
        dconf = anki.models.defaultField
        flds = []
        for c, row in enumerate(db.all("""
select name, features, required, "unique",
quizFontFamily, quizFontSize, quizFontColour, editFontSize from fieldModels
where modelId = ?
order by ordinal""", mid)):
            conf = dconf.copy()
            (conf['name'],
             conf['rtl'],
             conf['req'],
             conf['uniq'],
             conf['font'],
             conf['qsize'],
             conf['qcol'],
             conf['esize']) = row
            conf['ord'] = c
            # ensure data is good
            conf['rtl'] = not not conf['rtl']
            conf['pre'] = True
            conf['font'] = conf['font'] or "Arial"
            conf['qcol'] = conf['qcol'] or "#000"
            conf['qsize'] = conf['qsize'] or 20
            conf['esize'] = conf['esize'] or 20
            flds.append(conf)
        return flds

    def _templatesForModel(self, mid, flds):
        import anki.models
        db = self.db
        dconf = anki.models.defaultTemplate
        tmpls = []
        for c, row in enumerate(db.all("""
select name, active, qformat, aformat, questionInAnswer,
questionAlign, lastFontColour, typeAnswer from cardModels
where modelId = ?
order by ordinal""", mid)):
            conf = dconf.copy()
            (conf['name'],
             conf['actv'],
             conf['qfmt'],
             conf['afmt'],
             conf['hideQ'],
             conf['align'],
             conf['bg'],
             conf['typeAns']) = row
            conf['ord'] = c
            # convert the field name to an ordinal
            ordN = None
            for (ord, fm) in enumerate(flds):
                if fm['name'] == conf['typeAns']:
                    ordN = ord
                    break
            if ordN is not None:
                conf['typeAns'] = ordN
            else:
                conf['typeAns'] = None
            for type in ("qfmt", "afmt"):
                # ensure the new style field format
                conf[type] = re.sub("%\((.+?)\)s", "{{\\1}}", conf[type])
                # some special names have changed
                conf[type] = re.sub(
                    "(?i){{tags}}", "{{Tags}}", conf[type])
                conf[type] = re.sub(
                    "(?i){{cardModel}}", "{{Template}}", conf[type])
                conf[type] = re.sub(
                    "(?i){{modelTags}}", "{{Model}}", conf[type])
            tmpls.append(conf)
        return tmpls

    # Template upgrading
    ######################################################################
    # {{field}} no longer inserts an implicit span, so we make the span
    # explicit on upgrade.
    def _upgradeTemplates(self):
        d = self.deck
        for m in d.models.all():
            # cache field styles
            styles = {}
            for f in m['flds']:
                attrs = [
                    "font-family:%s" % f['font'],
                    "font-size:%spx" % f['qsize'],
                    "color:%s" % f['qcol']]
                if f['rtl']:
                    attrs.append("direction:rtl;unicode-bidi:embed")
                if f['pre']:
                    attrs.append("white-space:pre-wrap")
                styles[f['name']] = '<span style="%s">\n{{%s}}\n</span>' % (
                    ";".join(attrs), f['name'])
            # then for each template
            for t in m['tmpls']:
                def repl(match):
                    field = match.group(1)
                    if field in styles:
                        return styles[field]
                    # special or non-existant field; leave alone
                    return match.group(0)
                for k in 'qfmt', 'afmt':
                    t[k] = re.sub("(?:^|[^{]){{([^{}]+)?}}", repl, t[k])
            # save model
            d.models.save(m)

    # Media references
    ######################################################################
    # In 2.0 we drop support for media and latex references in the template,
    # since they require generating card templates to see what media a fact
    # uses, and are confusing for shared deck users. To ease the upgrade
    # process, we automatically convert the references to new fields.

    def _rewriteMediaRefs(self):
        deck = self.deck
        def rewriteRef(key):
            all, fname = match
            if all in state['mflds']:
                # we've converted this field before
                new = state['mflds'][all]
            else:
                # get field name and any prefix/suffix
                m2 = re.match(
                    "([^{]*)\{\{\{?(?:text:)?([^}]+)\}\}\}?(.*)",
                    fname)
                # not a field reference?
                if not m2:
                    return
                pre, ofld, suf = m2.groups()
                # get index of field name
                try:
                    idx = deck.models.fieldMap(m)[ofld][0]
                except:
                    # invalid field or tag reference; don't rewrite
                    return
                # find a free field name
                while 1:
                    state['fields'] += 1
                    fld = "Media %d" % state['fields']
                    if fld not in deck.models.fieldMap(m).keys():
                        break
                # add the new field
                f = deck.models.newField(fld)
                deck.models.addField(m, f)
                # loop through facts and write reference into new field
                data = []
                for id, flds in self.deck.db.execute(
                    "select id, flds from facts where id in "+
                    ids2str(deck.models.fids(m))):
                    sflds = splitFields(flds)
                    ref = all.replace(fname, pre+sflds[idx]+suf)
                    data.append((flds+ref, id))
                # update facts
                deck.db.executemany("update facts set flds=? where id=?",
                                    data)
                # note field for future
                state['mflds'][fname] = fld
                new = fld
            # rewrite reference in template
            t[key] = t[key].replace(all, "{{{%s}}}" % new)
        regexps = deck.media.regexps + (
            r"(\[latex\](.+?)\[/latex\])",
            r"(\[\$\](.+?)\[/\$\])",
            r"(\[\$\$\](.+?)\[/\$\$\])")
        # process each model
        for m in deck.models.all():
            state = dict(mflds={}, fields=0)
            for t in m['tmpls']:
                for r in regexps:
                    for match in re.findall(r, t['qfmt']):
                        rewriteRef('qfmt')
                    for match in re.findall(r, t['afmt']):
                        rewriteRef('afmt')
            if state['fields']:
                deck.models.save(m)

    # Inactive templates
    ######################################################################
    # Templates can't be declared as inactive anymore. Remove any that are
    # marked inactive and have no dependent cards.

    def _removeInactive(self):
        d = self.deck
        for m in d.models.all():
            remove = []
            for t in m['tmpls']:
                if not t['actv']:
                    if not d.db.scalar("""
select 1 from cards where fid in (select id from facts where mid = ?)
and ord = ? limit 1""", m['id'], t['ord']):
                        remove.append(t)
                del t['actv']
            for r in remove:
                m['tmpls'].remove(t)
            d.models.save(m)

    # Upgrading deck
    ######################################################################

    def _upgradeDeck(self):
        "Handle the rest of the upgrade to 2.0."
        import anki.deck
        deck = self.deck
        # make sure we have a current model id
        deck.models.setCurrent(deck.models.models.values()[0])
        # remove unused templates that were marked inactive
        self._removeInactive()
        # rewrite media references in card template
        self._rewriteMediaRefs()
        # template handling has changed
        self._upgradeTemplates()
        # regenerate css, and set new card order
        for m in deck.models.all():
            m['newOrder'] = deck.conf['oldNewOrder']
            deck.models.save(m)
        del deck.conf['oldNewOrder']
        # fix creation time
        deck.sched._updateCutoff()
        d = datetime.datetime.today()
        d -= datetime.timedelta(hours=4)
        d = datetime.datetime(d.year, d.month, d.day)
        d += datetime.timedelta(hours=4)
        d -= datetime.timedelta(days=1+int((time.time()-deck.crt)/86400))
        deck.crt = int(time.mktime(d.timetuple()))
        deck.sched._updateCutoff()
        # update uniq cache
        deck.updateFieldCache(deck.db.list("select id from facts"))
        # remove old views
        for v in ("failedCards", "revCardsOld", "revCardsNew",
                  "revCardsDue", "revCardsRandom", "acqCardsRandom",
                  "acqCardsOld", "acqCardsNew"):
            deck.db.execute("drop view if exists %s" % v)
        # remove stats, as it's all in the revlog now
        deck.db.execute("drop table if exists stats")
        # suspended cards don't use ranges anymore
        deck.db.execute("update cards set queue=-1 where queue between -3 and -1")
        deck.db.execute("update cards set queue=-2 where queue between 3 and 5")
        deck.db.execute("update cards set queue=-3 where queue between 6 and 8")
        # remove old deleted tables
        for t in ("cards", "facts", "models", "media"):
            deck.db.execute("drop table if exists %sDeleted" % t)
        # rewrite due times for new cards
        deck.db.execute("""
update cards set due = fid where type=0""")
        # and failed cards
        left = len(deck.groups.conf(1)['new']['delays'])
        deck.db.execute("update cards set edue = ?, left=? where type = 1",
                        deck.sched.today+1, left)
        # and due cards
        deck.db.execute("""
update cards set due = cast(
(case when due < :stamp then 0 else 1 end) +
((due-:stamp)/86400) as int)+:today where type = 2
""", stamp=deck.sched.dayCutoff, today=deck.sched.today)
        # possibly re-randomize
        if deck.models.randomNew():
            deck.sched.randomizeCards()
        # update insertion id
        deck.conf['nextPos'] = deck.db.scalar("select max(id) from facts")+1
        deck.save()

        # optimize and finish
        deck.db.commit()
        deck.db.execute("vacuum")
        deck.db.execute("analyze")
        deck.db.execute("update deck set ver = ?", SCHEMA_VERSION)
        deck.save()
