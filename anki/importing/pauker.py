# -*- coding: utf-8 -*-
# Copyright: Andreas Klauer <Andreas.Klauer@metamorpher.de>
# License: BSD-3

import gzip, math, random
import xml.etree.ElementTree as ET
from anki.importing.noteimp import NoteImporter, ForeignNote, ForeignCard
from anki.stdmodels import addBasicModel

class PaukerImporter(NoteImporter):
    '''Import Pauker 1.8 Lesson (*.pau.gz)'''

    def fields(self):
        '''Pauker is Front/Back'''
        print "pauker", "fields", self
        return 2

    def foreignNotes(self):
        '''Build and return a list of notes.'''
        print "pauker", "foreignNotes", self
        notes = []

        try:
            f = gzip.open(self.file)
            tree = ET.parse(f)
            lesson = tree.getroot()
            assert lesson.tag == "Lesson"
        finally:
            f.close()

        index = -4
        ivlmax = -1

        for batch in lesson.findall('./Batch'):
            index += 1

            if index >= 0:
                ivlmin = ivlmax+1
                ivlmax = int(math.exp(index))

            for card in batch.findall('./Card'):
                front = card.findtext('./FrontSide/Text')
                back = card.findtext('./ReverseSide/Text')
                note = ForeignNote()
                note.fields = [x.strip().replace('\n','<br>') for x in [front, back]]
                note.tags.append("Pauker%+d" % (index,))
                notes.append(note)

                if index == -3:
                    # new cards
                    continue

                for tmpl in self.model['tmpls']:
                    fc = ForeignCard()
                    if index >= 0:
                        # due cards
                        fc.ivl = random.randint(ivlmin,ivlmax)
                        fc.due = self.col.sched.today+random.randint(ivlmin,ivlmax)
                        fc.factor = 2500 * fc.ivl / ivlmax
                        fc.reps = index * fc.ivl / ivlmax
                    else:
                        # shortterm cards
                        fc.ivl = 0
                        fc.due = self.col.sched.today
                        fc.factor = random.randint(2000, 2500)
                        fc.reps = random.randint(0,3)
                    note.cards[tmpl['ord']] = fc

        return notes
