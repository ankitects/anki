# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, re
from anki.db import DB
from anki.importing.base import Importer
from anki.importing.noteimp import NoteImporter, ForeignNote, ForeignCard
from anki.utils import checksum, base91
from anki.stdmodels import addBasicModel

class MnemosyneImporter(NoteImporter):

    def run(self):
        db = DB(self.file)
        ver = db.scalar(
            "select value from global_variables where key='version'")
        assert ver.startswith('Mnemosyne SQL 1')
        # gather facts into temp objects
        curid = None
        notes = {}
        note = None
        for _id, id, k, v in db.execute("""
select _id, id, key, value from facts f, data_for_fact d where
f._id=d._fact_id"""):
            if id != curid:
                if note:
                    notes[note['_id']] = note
                note = {'_id': _id}
                curid = id
            note[k] = v
        if note:
            notes[note['_id']] = note
        # gather cards
        front = []
        frontback = []
        vocabulary = []
        for row in db.execute("""
select _fact_id, fact_view_id, tags, next_rep, last_rep, easiness,
acq_reps+ret_reps, lapses from cards"""):
            # categorize note
            note = notes[row[0]]
            if row[1] == "1.1":
                front.append(note)
            elif row[1] == "2.1":
                frontback.append(note)
            elif row[1] == "3.1":
                vocabulary.append(note)
            # merge tags into note
            tags = row[2].replace(", ", "\x1f").replace(" ", "_")
            tags = tags.replace("\x1f", " ")
            if "tags" not in note:
                note['tags'] = []
            note['tags'] += self.col.tags.split(tags)
            note['tags'] = self.col.tags.canonify(note['tags'])
            # if it's a new card we can go with the defaults
            if row[3] == -1:
                continue
            # add the card
            c = ForeignCard()
            c.factor = row[5]
            c.reps = row[6]
            c.lapses = row[7]
            # ivl is inferred in mnemosyne
            next, prev = row[3:5]
            c.ivl = max(1, (next - prev)/86400)
            # work out how long we've got left
            rem = int((next - time.time())/86400)
            c.due = max(0, self.col.sched.today+rem)
            # get ord
            m = re.match("\d+\.(\d+)", row[1])
            ord = int(m.group(1))-1
            if 'cards' not in note:
                note['cards'] = {}
            note['cards'][ord] = c
        self._addFronts(front)
        self._addFrontBacks(frontback)
        self._addVocabulary(vocabulary)

    def fields(self):
        return self._fields

    def _addFronts(self, notes, model=None, fields=("f", "b")):
        data = []
        for orig in notes:
            # create a foreign note object
            n = ForeignNote()
            n.fields = []
            for f in fields:
                n.fields.append(orig.get(f, ''))
            n.tags = orig['tags']
            n.cards = orig.get('cards', {})
            data.append(n)
        # add a basic model
        if not model:
            model = addBasicModel(self.col)
        model['name'] = "Mnemosyne-FrontOnly"
        mm = self.col.models
        mm.save(model)
        mm.setCurrent(model)
        self.model = model
        self._fields = len(model['flds'])
        self.initMapping()
        # import
        self.importNotes(data)

    def _addFrontBacks(self, notes):
        m = addBasicModel(self.col)
        m['name'] = "Mnemosyne-FrontBack"
        mm = self.col.models
        t = mm.newTemplate("Back")
        t['qfmt'] = "{{Back}}"
        t['afmt'] = t['qfmt'] + "\n\n<hr id=answer>\n\n{{Front}}"
        mm.addTemplate(m, t)
        self._addFronts(notes, m)

    def _addVocabulary(self, notes):
        mm = self.col.models
        m = mm.new("Mnemosyne-Vocabulary")
        for f in "Expression", "Pronunciation", "Meaning", "Notes":
            fm = mm.newField(f)
            mm.addField(m, fm)
        t = mm.newTemplate("Recognition")
        t['qfmt'] = "{{Expression}}"
        t['afmt'] = t['qfmt'] + """\n\n<hr id=answer>\n\n\
{{Pronunciation}}<br>\n{{Meaning}}<br>\n{{Notes}}"""
        mm.addTemplate(m, t)
        t = mm.newTemplate("Production")
        t['qfmt'] = "{{Meaning}}"
        t['afmt'] = t['qfmt'] + """\n\n<hr id=answer>\n\n\
{{Expression}}<br>\n{{Pronunciation}}<br>\n{{Notes}}"""
        mm.addTemplate(m, t)
        mm.add(m)
        self._addFronts(notes, m, fields=("f", "p_1", "m_1", "n"))
