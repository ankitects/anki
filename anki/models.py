# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson, copy, re
from anki.utils import intTime, hexifyID, joinFields, splitFields, ids2str, \
    timestampID, fieldChecksum
from anki.lang import _
from anki.consts import *

# Models
##########################################################################

# - careful not to add any lists/dicts/etc here, as they aren't deep copied

defaultModel = {
    'sortf': 0,
    'gid': 1,
    'clozectx': False,
    'newOrder': NEW_CARDS_DUE,
    'latexPre': """\
\\documentclass[12pt]{article}
\\special{papersize=3in,5in}
% \\usepackage[utf8x]{inputenc} % uncomment this for foreign characters
\\usepackage{amssymb,amsmath}
\\pagestyle{empty}
\\setlength{\\parindent}{0in}
\\begin{document}
""",
    'latexPost': "\\end{document}",
    'mod': 0,
    'usn': 0,
    'vers': [],
}

defaultField = {
    'name': "",
    'ord': None,
    'req': False,
    'uniq': False,
    'sticky': False,
    # the following alter editing, and are used as defaults for the
    # template wizard
    'rtl': False,
    'font': "Arial",
    'size': 20,
}

defaultTemplate = {
    'name': "",
    'ord': None,
    'qfmt': "",
    'afmt': "",
    'typeAns': None,
    'gid': None,
}

class ModelManager(object):

    # Saving/loading registry
    #############################################################

    def __init__(self, col):
        self.col = col

    def load(self, json):
        "Load registry from JSON."
        self.changed = False
        self.models = simplejson.loads(json)

    def save(self, m=None, gencards=False):
        "Mark M modified if provided, and schedule registry flush."
        if m:
            m['mod'] = intTime()
            m['usn'] = self.col.usn()
            self._updateRequired(m)
            if gencards:
                self.col.genCards(self.nids(m))
        self.changed = True

    def flush(self):
        "Flush the registry if any models were changed."
        if self.changed:
            self.col.db.execute("update col set models = ?",
                                 simplejson.dumps(self.models))

    # Retrieving and creating models
    #############################################################

    def current(self):
        "Get current model."
        try:
            m = self.get(self.col.groups.top()['curModel'])
            assert m
            return m
        except:
            return self.models.values()[0]

    def setCurrent(self, m):
        t = self.col.groups.top()
        t['curModel'] = m['id']
        self.col.groups.save(t)

    def get(self, id):
        "Get model with ID, or None."
        id = str(id)
        if id in self.models:
            return self.models[id]

    def all(self):
        "Get all models."
        return self.models.values()

    def byName(self, name):
        "Get model with NAME."
        for m in self.models.values():
            if m['name'].lower() == name.lower():
                return m

    def new(self, name):
        "Create a new model, save it in the registry, and return it."
        # caller should call save() after modifying
        m = defaultModel.copy()
        m['name'] = name
        m['mod'] = intTime()
        m['flds'] = []
        m['tmpls'] = []
        m['tags'] = []
        return self._add(m)

    def rem(self, m):
        "Delete model, and all its cards/notes."
        self.col.modSchema()
        current = self.current()['id'] == m['id']
        # delete notes/cards
        self.col.remCards(self.col.db.list("""
select id from cards where nid in (select id from notes where mid = ?)""",
                                      m['id']))
        # then the model
        del self.models[str(m['id'])]
        self.save()
        # GUI should ensure last model is not deleted
        if current:
            self.setCurrent(self.models.values()[0])

    def _add(self, m):
        self._setID(m)
        self.update(m)
        self.setCurrent(m)
        return m

    def update(self, m):
        "Add or update an existing model. Used for syncing and merging."
        self.models[str(m['id'])] = m
        # mark registry changed, but don't bump mod time
        self.save()

    def _setID(self, m):
        while 1:
            id = str(intTime(1000))
            if id not in self.models:
                break
        m['id'] = id

    def have(self, id):
        return str(id) in self.models

    # Tools
    ##################################################

    def nids(self, m):
        "Note ids for M."
        return self.col.db.list(
            "select id from notes where mid = ?", m['id'])

    def useCount(self, m):
        "Number of note using M."
        return self.col.db.scalar(
            "select count() from notes where mid = ?", m['id'])

    def randomNew(self):
        return self.current()['newOrder'] == NEW_CARDS_RANDOM

    # Copying
    ##################################################

    def copy(self, m):
        "Copy, save and return."
        m2 = copy.deepcopy(m)
        m2['name'] = _("%s copy") % m2['name']
        return self._add(m2)

    # Fields
    ##################################################

    def newField(self, name):
        f = defaultField.copy()
        f['name'] = name
        return f

    def fieldMap(self, m):
        "Mapping of field name -> (ord, field)."
        return dict((f['name'], (f['ord'], f)) for f in m['flds'])

    def fieldNames(self, m):
        return [f['name'] for f in m['flds']]

    def sortIdx(self, m):
        return m['sortf']

    def setSortIdx(self, m, idx):
        assert idx >= 0 and idx < len(m['flds'])
        self.col.modSchema()
        m['sortf'] = idx
        self.col.updateFieldCache(self.nids(m), csum=False)
        self.save(m)

    def addField(self, m, field):
        m['flds'].append(field)
        self._updateFieldOrds(m)
        self.save(m)
        def add(fields):
            fields.append("")
            return fields
        self._transformFields(m, add)

    def remField(self, m, field):
        idx = m['flds'].index(field)
        m['flds'].remove(field)
        self._updateFieldOrds(m)
        def delete(fields):
            del fields[idx]
            return fields
        self._transformFields(m, delete)
        if idx == self.sortIdx(m):
            # need to rebuild
            self.col.updateFieldCache(self.nids(m), csum=False)
        # saves
        self.renameField(m, field, None)

    def moveField(self, m, field, idx):
        oldidx = m['flds'].index(field)
        if oldidx == idx:
            return
        m['flds'].remove(field)
        m['flds'].insert(idx, field)
        self._updateFieldOrds(m)
        self.save(m)
        def move(fields, oldidx=oldidx):
            val = fields[oldidx]
            del fields[oldidx]
            fields.insert(idx, val)
            return fields
        self._transformFields(m, move)

    def renameField(self, m, field, newName):
        self.col.modSchema()
        for t in m['tmpls']:
            types = ("{{%s}}", "{{text:%s}}", "{{#%s}}",
                     "{{^%s}}", "{{/%s}}")
            for type in types:
                for fmt in ('qfmt', 'afmt'):
                    if newName:
                        repl = type%newName
                    else:
                        repl = ""
                    t[fmt] = t[fmt].replace(type%field['name'], repl)
        field['name'] = newName
        self.save(m)

    def _updateFieldOrds(self, m):
        for c, f in enumerate(m['flds']):
            f['ord'] = c

    def _transformFields(self, m, fn):
        self.col.modSchema()
        r = []
        for (id, flds) in self.col.db.execute(
            "select id, flds from notes where mid = ?", m['id']):
            r.append((joinFields(fn(splitFields(flds))),
                      intTime(), self.col.usn(), id))
        self.col.db.executemany(
            "update notes set flds=?,mod=?,usn=? where id = ?", r)

    # Templates
    ##################################################

    def newTemplate(self, name):
        t = defaultTemplate.copy()
        t['name'] = name
        return t

    def addTemplate(self, m, template):
        "Note: should col.genCards() afterwards."
        self.col.modSchema()
        m['tmpls'].append(template)
        self._updateTemplOrds(m)
        self.save(m)

    def remTemplate(self, m, template):
        "False if removing template would leave orphan notes."
        # find cards using this template
        ord = m['tmpls'].index(template)
        cids = self.col.db.list("""
select c.id from cards c, notes f where c.nid=f.id and mid = ? and ord = ?""",
                                 m['id'], ord)
        # all notes with this template must have at least two cards, or we
        # could end up creating orphaned notes
        if self.col.db.scalar("""
select nid, count() from cards where
nid in (select nid from cards where id in %s)
group by nid
having count() < 2
limit 1""" % ids2str(cids)):
            return False
        # ok to proceed; remove cards
        self.col.modSchema()
        self.col.remCards(cids)
        # shift ordinals
        self.col.db.execute("""
update cards set ord = ord - 1, usn = ?, mod = ?
 where nid in (select id from notes where mid = ?) and ord > ?""",
                             self.col.usn(), intTime(), m['id'], ord)
        m['tmpls'].remove(template)
        self._updateTemplOrds(m)
        self.save(m)
        return True

    def _updateTemplOrds(self, m):
        for c, t in enumerate(m['tmpls']):
            t['ord'] = c

    def moveTemplate(self, m, template, idx):
        oldidx = m['tmpls'].index(template)
        if oldidx == idx:
            return
        oldidxs = dict((id(t), t['ord']) for t in m['tmpls'])
        m['tmpls'].remove(template)
        m['tmpls'].insert(idx, template)
        self._updateTemplOrds(m)
        # generate change map
        map = []
        for t in m['tmpls']:
            map.append("when ord = %d then %d" % (oldidxs[id(t)], t['ord']))
        # apply
        self.save(m)
        self.col.db.execute("""
update cards set ord = (case %s end),usn=?,mod=? where nid in (
select id from notes where mid = ?)""" % " ".join(map),
                             self.col.usn(), intTime(), m['id'])

    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be self if model is not changing

    def change(self, m, nids, newModel, fmap, cmap):
        self.col.modSchema()
        assert newModel['id'] == m['id'] or (fmap and cmap)
        if fmap:
            self._changeNotes(nids, newModel, fmap)
        if cmap:
            self._changeCards(nids, newModel, cmap)

    def _changeNotes(self, nids, newModel, map):
        d = []
        nfields = len(newModel['flds'])
        for (nid, flds) in self.col.db.execute(
            "select id, flds from notes where id in "+ids2str(nids)):
            newflds = {}
            flds = splitFields(flds)
            for old, new in map.items():
                newflds[new] = flds[old]
            flds = []
            for c in range(nfields):
                flds.append(newflds.get(c, ""))
            flds = joinFields(flds)
            d.append(dict(nid=nid, flds=flds, mid=newModel['id'],
                      m=intTime(),u=self.col.usn()))
        self.col.db.executemany(
            "update notes set flds=:flds,mid=:mid,mod=:m,usn=:u where id = :nid", d)
        self.col.updateFieldCache(nids)

    def _changeCards(self, nids, newModel, map):
        d = []
        deleted = []
        for (cid, ord) in self.col.db.execute(
            "select id, ord from cards where nid in "+ids2str(nids)):
            if map[ord] is not None:
                d.append(dict(
                    cid=cid,new=map[ord],u=self.col.usn(),m=intTime()))
            else:
                deleted.append(cid)
        self.col.db.executemany(
            "update cards set ord=:new,usn=:u,mod=:m where id=:cid",
            d)
        self.col.remCards(deleted)

    # Schema hash
    ##########################################################################

    def scmhash(self, m):
        "Return a hash of the schema, to see if models are compatible."
        s = ""
        for f in m['flds']:
            s += f['name']
        return fieldChecksum(s)

    # Required field/text cache
    ##########################################################################

    def _updateRequired(self, m):
        req = []
        flds = [f['name'] for f in m['flds']]
        cloze = False
        for t in m['tmpls']:
            ret = self._reqForTemplate(m, flds, t)
            if ret[1]:
                cloze = True
            req.append((t['ord'], ret[0], ret[1]))
        m['req'] = req
        m['cloze'] = cloze

    def _reqForTemplate(self, m, flds, t):
        a = []
        b = []
        cloze = "cloze" in t['qfmt']
        reqstrs = []
        if cloze:
            # need a cloze-specific filler
            cloze = ""
            nums = re.findall("\{\{#cloze:(\d+):", t['qfmt'])
            for n in nums:
                n = int(n)
                cloze += "{{c%d::foo}}" % n
                # record that we require a specific string for generation
                reqstrs.append("{{c%d::" % n)
        for f in flds:
            a.append(cloze if cloze else "1")
            b.append("")
        data = [1, 1, m['id'], 1, t['ord'], "", joinFields(b)]
        empty = self.col._renderQA(data)['q']
        start = a
        req = []
        for i in range(len(flds)):
            a = start[:]
            a[i] = ""
            # blank out this field
            data[6] = joinFields(a)
            # if the result is same as empty, field is required
            if self.col._renderQA(data)['q'] == empty:
                req.append(i)
        return req, reqstrs

    def availOrds(self, m, flds):
        "Given a joined field string, return available template ordinals."
        have = {}
        for c, f in enumerate(splitFields(flds)):
            have[c] = f.strip()
        avail = []
        for ord, req, reqstrs in m['req']:
            ok = True
            for f in req:
                if not have[f]:
                    # missing and was required
                    ok = False
                    break
            if not ok:
                continue
            for s in reqstrs:
                if s not in flds:
                    # required cloze string was missing
                    ok = False
                    break
            if ok:
                avail.append(ord)
        return avail
