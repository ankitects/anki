# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import time
from typing import cast

from anki.db import DB
from anki.importing.noteimp import ForeignCard, ForeignNote, NoteImporter
from anki.rsbackend import TR
from anki.stdmodels import addBasicModel, addClozeModel


class MnemosyneImporter(NoteImporter):

    needMapper = False
    update = False
    allowHTML = True

    def run(self):
        db = DB(self.file)
        ver = db.scalar("select value from global_variables where key='version'")
        if not ver.startswith("Mnemosyne SQL 1") and ver not in ("2", "3"):
            self.log.append(
                self.col.tr(TR.IMPORTING_FILE_VERSION_UNKNOWN_TRYING_IMPORT_ANYWAY)
            )
        # gather facts into temp objects
        curid = None
        notes = {}
        note = None
        for _id, id, k, v in db.execute(
            """
select _id, id, key, value from facts f, data_for_fact d where
f._id=d._fact_id"""
        ):
            if id != curid:
                if note:
                    # pylint: disable=unsubscriptable-object
                    notes[note["_id"]] = note
                note = {"_id": _id}
                curid = id
            assert note
            note[k] = v
        if note:
            notes[note["_id"]] = note
        # gather cards
        front = []
        frontback = []
        vocabulary = []
        cloze = {}
        for row in db.execute(
            """
select _fact_id, fact_view_id, tags, next_rep, last_rep, easiness,
acq_reps+ret_reps, lapses, card_type_id from cards"""
        ):
            # categorize note
            note = notes[row[0]]
            if row[1].endswith(".1"):
                if row[1].startswith("1.") or row[1].startswith("1::"):
                    front.append(note)
                elif row[1].startswith("2.") or row[1].startswith("2::"):
                    frontback.append(note)
                elif row[1].startswith("3.") or row[1].startswith("3::"):
                    vocabulary.append(note)
                elif row[1].startswith("5.1"):
                    cloze[row[0]] = note
            # check for None to fix issue where import can error out
            rawTags = row[2]
            if rawTags is None:
                rawTags = ""
            # merge tags into note
            tags = rawTags.replace(", ", "\x1f").replace(" ", "_")
            tags = tags.replace("\x1f", " ")
            if "tags" not in note:
                note["tags"] = []
            note["tags"] += self.col.tags.split(tags)
            # if it's a new card we can go with the defaults
            if row[3] == -1:
                continue
            # add the card
            c = ForeignCard()
            c.factor = int(row[5] * 1000)
            c.reps = row[6]
            c.lapses = row[7]
            # ivl is inferred in mnemosyne
            next, prev = row[3:5]
            c.ivl = max(1, (next - prev) // 86400)
            # work out how long we've got left
            rem = int((next - time.time()) / 86400)
            c.due = self.col.sched.today + rem
            # get ord
            m = re.search(r".(\d+)$", row[1])
            assert m
            ord = int(m.group(1)) - 1
            if "cards" not in note:
                note["cards"] = {}
            note["cards"][ord] = c
        self._addFronts(front)
        total = self.total
        self._addFrontBacks(frontback)
        total += self.total
        self._addVocabulary(vocabulary)
        self.total += total
        self._addCloze(cloze)
        self.total += total
        self.log.append(self.col.tr(TR.IMPORTING_NOTE_IMPORTED, count=self.total))

    def fields(self):
        return self._fields

    def _mungeField(self, fld):
        # \n -> br
        fld = re.sub("\r?\n", "<br>", fld)
        # latex differences
        fld = re.sub(r"(?i)<(/?(\$|\$\$|latex))>", "[\\1]", fld)
        # audio differences
        fld = re.sub('<audio src="(.+?)">(</audio>)?', "[sound:\\1]", fld)
        return fld

    def _addFronts(self, notes, model=None, fields=("f", "b")):
        data = []
        for orig in notes:
            # create a foreign note object
            n = ForeignNote()
            n.fields = []
            for f in fields:
                fld = self._mungeField(orig.get(f, ""))
                n.fields.append(fld)
            n.tags = orig["tags"]
            n.cards = orig.get("cards", {})
            data.append(n)
        # add a basic model
        if not model:
            model = addBasicModel(self.col)
            model["name"] = "Mnemosyne-FrontOnly"
        mm = self.col.models
        mm.save(model)
        mm.setCurrent(model)
        self.model = model
        self._fields = len(model["flds"])
        self.initMapping()
        # import
        self.importNotes(data)

    def _addFrontBacks(self, notes):
        m = addBasicModel(self.col)
        m["name"] = "Mnemosyne-FrontBack"
        mm = self.col.models
        t = mm.newTemplate("Back")
        t["qfmt"] = "{{Back}}"
        t["afmt"] = t["qfmt"] + "\n\n<hr id=answer>\n\n{{Front}}"  # type: ignore
        mm.addTemplate(m, t)
        self._addFronts(notes, m)

    def _addVocabulary(self, notes):
        mm = self.col.models
        m = mm.new("Mnemosyne-Vocabulary")
        for f in "Expression", "Pronunciation", "Meaning", "Notes":
            fm = mm.newField(f)
            mm.addField(m, fm)
        t = mm.newTemplate("Recognition")
        t["qfmt"] = "{{Expression}}"
        t["afmt"] = (
            cast(str, t["qfmt"])
            + """\n\n<hr id=answer>\n\n\
{{Pronunciation}}<br>\n{{Meaning}}<br>\n{{Notes}}"""
        )
        mm.addTemplate(m, t)
        t = mm.newTemplate("Production")
        t["qfmt"] = "{{Meaning}}"
        t["afmt"] = (
            cast(str, t["qfmt"])
            + """\n\n<hr id=answer>\n\n\
{{Expression}}<br>\n{{Pronunciation}}<br>\n{{Notes}}"""
        )
        mm.addTemplate(m, t)
        mm.add(m)
        self._addFronts(notes, m, fields=("f", "p_1", "m_1", "n"))

    def _addCloze(self, notes):
        data = []
        notes = list(notes.values())
        for orig in notes:
            # create a foreign note object
            n = ForeignNote()
            n.fields = []
            fld = orig.get("text", "")
            fld = re.sub("\r?\n", "<br>", fld)
            state = dict(n=1)

            def repl(match):
                # pylint: disable=cell-var-from-loop
                # replace [...] with cloze refs
                res = "{{c%d::%s}}" % (state["n"], match.group(1))
                state["n"] += 1
                return res

            fld = re.sub(r"\[(.+?)\]", repl, fld)
            fld = self._mungeField(fld)
            n.fields.append(fld)
            n.fields.append("")  # extra
            n.tags = orig["tags"]
            n.cards = orig.get("cards", {})
            data.append(n)
        # add cloze model
        model = addClozeModel(self.col)
        model["name"] = "Mnemosyne-Cloze"
        mm = self.col.models
        mm.save(model)
        mm.setCurrent(model)
        self.model = model
        self._fields = len(model["flds"])
        self.initMapping()
        self.importNotes(data)
