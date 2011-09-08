# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson, copy
from anki.utils import intTime, hexifyID, joinFields, splitFields, ids2str, \
    timestampID
from anki.lang import _
from anki.consts import *

# Models
##########################################################################

# careful not to add any lists/dicts/etc here, as they aren't deep copied
defaultModel = {
    'css': "",
    'sortf': 0,
    'gid': 1,
    'clozectx': False,
    'newOrder': NEW_CARDS_DUE,
    'latexPre': """\
\\documentclass[12pt]{article}
\\special{papersize=3in,5in}
\\usepackage[utf8x]{inputenc}
\\usepackage{amssymb,amsmath}
\\pagestyle{empty}
\\setlength{\\parindent}{0in}
\\begin{document}
""",
    'latexPost': "\\end{document}",
}

defaultField = {
    'name': "",
    'ord': None,
    'rtl': False,
    'req': False,
    'uniq': False,
    'font': "Arial",
    'qsize': 20,
    'esize': 20,
    'qcol': "#000",
    'pre': True,
    'sticky': False,
}

defaultTemplate = {
    'name': "",
    'ord': None,
    'actv': True,
    'qfmt': "",
    'afmt': "",
    'hideQ': False,
    'align': 0,
    'bg': "#fff",
    'emptyAns': True,
    'typeAns': None,
    'gid': None,
}

class ModelManager(object):

    # Saving/loading registry
    #############################################################

    def __init__(self, deck):
        self.deck = deck

    def load(self, json):
        "Load registry from JSON."
        self.changed = False
        self.models = simplejson.loads(json)

    def save(self, m=None):
        "Mark M modified if provided, and schedule registry flush."
        if m:
            m['mod'] = intTime()
            m['css'] = self._css(m)
        self.changed = True

    def flush(self):
        "Flush the registry if any models were changed."
        if self.changed:
            self.deck.db.execute("update deck set models = ?",
                                 simplejson.dumps(self.models))

    # Retrieving and creating models
    #############################################################

    def current(self):
        "Get current model."
        try:
            return self.get(self.deck.groups.top()['curModel'])
        except:
            return self.models.values()[0]

    def setCurrent(self, m):
        self.deck.groups.top()['curModel'] = m['id']

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
        "Delete model, and all its cards/facts."
        self.deck.modSchema()
        current = self.current()['id'] == m['id']
        # delete facts/cards
        self.deck.remCards(self.deck.db.list("""
select id from cards where fid in (select id from facts where mid = ?)""",
                                      m['id']))
        # then the model
        del self.models[m['id']]
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

    # Tools
    ##################################################

    def fids(self, m):
        "Fact ids for M."
        return self.deck.db.list(
            "select id from facts where mid = ?", m['id'])

    def useCount(self, m):
        "Number of fact using M."
        return self.deck.db.scalar(
            "select count() from facts where mid = ?", m['id'])

    def randomNew(self):
        return self.current()['newOrder'] == NEW_CARDS_RANDOM

    # Copying
    ##################################################

    def copy(self, m):
        "Copy, save and return."
        m2 = copy.deepcopy(m)
        m2['name'] = _("%s copy") % m2['name']
        return self._add(m2)

    # CSS generation
    ##################################################

    def css(self):
        "CSS for all models."
        return "\n".join([m['css'] for m in self.all()])

    def _css(self, m):
        # fields
        css = "".join(self._fieldCSS(
            ".fm%s-%s" % (hexifyID(m['id']), hexifyID(f['ord'])),
            (f['font'], f['qsize'], f['qcol'], f['rtl'], f['pre']))
            for f in m['flds'])
        # templates
        css += "".join(".cm%s-%s {text-align:%s;background:%s}\n" % (
            hexifyID(m['id']), hexifyID(t['ord']),
            ("center", "left", "right")[t['align']], t['bg'])
                for t in m['tmpls'])
        return css

    def _rewriteFont(self, font):
        "Convert a platform font to a multiplatform list."
        font = font.lower()
        for family in self.deck.conf['fontFamilies']:
            for font2 in family:
                if font == font2.lower():
                    return ",".join(family)
        return font

    def _fieldCSS(self, prefix, row):
        (fam, siz, col, rtl, pre) = row
        t = 'font-family:"%s";' % self._rewriteFont(fam)
        t += 'font-size:%dpx;' % siz
        t += 'color:%s;' % col
        if rtl:
            t += "direction:rtl;unicode-bidi:embed;"
        if pre:
            t += "white-space:pre-wrap;"
        t = "%s {%s}\n" % (prefix, t)
        return t

    # Fields
    ##################################################

    def newField(self, name):
        f = defaultField.copy()
        f['name'] = name
        return f

    def fieldMap(self, m):
        "Mapping of field name -> (ord, field)."
        return dict((f['name'], (f['ord'], f)) for f in m['flds'])

    def sortIdx(self, m):
        return m['sortf']

    def setSortIdx(self, m, idx):
        assert idx >= 0 and idx < len(m['flds'])
        self.deck.modSchema()
        m['sortf'] = idx
        self.deck.updateFieldCache(self.fids(m), csum=False)
        self.save(m)

    def addField(self, m, field):
        m['flds'].append(field)
        self._updateFieldOrds(m)
        self.save(m)
        def add(fields):
            fields.append("")
            return fields
        self._transformFields(m, add)

    def delField(self, m, field):
        idx = m['flds'].index(field)
        m['flds'].remove(field)
        self._updateFieldOrds(m)
        def delete(fields):
            del fields[idx]
            return fields
        self._transformFields(m, delete)
        if idx == self.sortIdx(m):
            # need to rebuild
            self.deck.updateFieldCache(self.fids(m), csum=False)
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
        self.deck.modSchema()
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
        self.deck.modSchema()
        r = []
        for (id, flds) in self.deck.db.execute(
            "select id, flds from facts where mid = ?", m['id']):
            r.append((joinFields(fn(splitFields(flds))), id))
        self.deck.db.executemany("update facts set flds = ? where id = ?", r)

    # Templates
    ##################################################

    def newTemplate(self, name):
        t = defaultTemplate.copy()
        t['name'] = name
        return t

    def addTemplate(self, m, template):
        self.deck.modSchema()
        m['tmpls'].append(template)
        self._updateTemplOrds(m)
        self.save(m)

    def delTemplate(self, m, template):
        self.deck.modSchema()
        ord = m['tmpls'].index(template)
        cids = self.deck.db.list("""
select c.id from cards c, facts f where c.fid=f.id and mid = ? and ord = ?""",
                                 m['id'], ord)
        self.deck.remCards(cids)
        # shift ordinals
        self.deck.db.execute("""
update cards set ord = ord - 1 where fid in (select id from facts
where mid = ?) and ord > ?""", m['id'], ord)
        m['tmpls'].remove(template)
        self._updateTemplOrds(m)
        self.save(m)

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
        self.deck.db.execute("""
update cards set ord = (case %s end) where fid in (
select id from facts where mid = ?)""" % " ".join(map), m['id'])

    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be self if model is not changing

    def change(self, m, fids, newModel, fmap, cmap):
        self.deck.modSchema()
        assert newModel['id'] == m['id'] or (fmap and cmap)
        if fmap:
            self._changeFacts(fids, newModel, fmap)
        if cmap:
            self._changeCards(fids, newModel, cmap)

    def _changeFacts(self, fids, newModel, map):
        d = []
        nfields = len(newModel['flds'])
        for (fid, flds) in self.deck.db.execute(
            "select id, flds from facts where id in "+ids2str(fids)):
            newflds = {}
            flds = splitFields(flds)
            for old, new in map.items():
                newflds[new] = flds[old]
            flds = []
            for c in range(nfields):
                flds.append(newflds.get(c, ""))
            flds = joinFields(flds)
            d.append(dict(fid=fid, flds=flds, mid=newModel['id']))
        self.deck.db.executemany(
            "update facts set flds=:flds, mid=:mid where id = :fid", d)
        self.deck.updateFieldCache(fids)

    def _changeCards(self, fids, newModel, map):
        d = []
        deleted = []
        for (cid, ord) in self.deck.db.execute(
            "select id, ord from cards where fid in "+ids2str(fids)):
            if map[ord] is not None:
                d.append(dict(cid=cid, new=map[ord]))
            else:
                deleted.append(cid)
        self.deck.db.executemany(
            "update cards set ord=:new where id=:cid", d)
        self.deck.remCards(deleted)
