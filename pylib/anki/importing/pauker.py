# Copyright: Andreas Klauer <Andreas.Klauer@metamorpher.de>
# License: BSD-3

import gzip
import html
import math
import random
import time
import xml.etree.ElementTree as ET

from anki.importing.noteimp import ForeignCard, ForeignNote, NoteImporter
from anki.stdmodels import addForwardReverse

ONE_DAY = 60 * 60 * 24


class PaukerImporter(NoteImporter):
    """Import Pauker 1.8 Lesson (*.pau.gz)"""

    needMapper = False
    allowHTML = True

    def run(self):
        model = addForwardReverse(self.col)
        model["name"] = "Pauker"
        self.col.models.save(model, updateReqs=False)
        self.col.models.set_current(model)
        self.model = model
        self.initMapping()
        NoteImporter.run(self)

    def fields(self):
        """Pauker is Front/Back"""
        return 2

    def foreignNotes(self):
        """Build and return a list of notes."""
        notes = []

        try:
            f = gzip.open(self.file)
            tree = ET.parse(f)  # type: ignore
            lesson = tree.getroot()
            assert lesson.tag == "Lesson"
        finally:
            f.close()

        index = -4

        for batch in lesson.findall("./Batch"):
            index += 1

            for card in batch.findall("./Card"):
                # Create a note for this card.
                front = card.findtext("./FrontSide/Text")
                back = card.findtext("./ReverseSide/Text")
                note = ForeignNote()
                assert front and back
                note.fields = [
                    html.escape(x.strip())
                    .replace("\n", "<br>")
                    .replace("  ", " &nbsp;")
                    for x in [front, back]
                ]
                notes.append(note)

                # Determine due date for cards.
                frontdue = card.find("./FrontSide[@LearnedTimestamp]")
                backdue = card.find("./ReverseSide[@Batch][@LearnedTimestamp]")

                if frontdue is not None:
                    note.cards[0] = self._learnedCard(
                        index, int(frontdue.attrib["LearnedTimestamp"])
                    )

                if backdue is not None:
                    note.cards[1] = self._learnedCard(
                        int(backdue.attrib["Batch"]),
                        int(backdue.attrib["LearnedTimestamp"]),
                    )

        return notes

    def _learnedCard(self, batch, timestamp):
        ivl = math.exp(batch)
        now = time.time()
        due = ivl - (now - timestamp / 1000.0) / ONE_DAY
        fc = ForeignCard()
        fc.due = self.col.sched.today + int(due + 0.5)
        fc.ivl = random.randint(int(ivl * 0.90), int(ivl + 0.5))
        fc.factor = random.randint(1500, 2500)
        return fc
