#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

import os
import tempfile

from tests.shared import getEmptyCol


def test_stats():
    d = getEmptyCol()
    note = d.newNote()
    note["Front"] = "foo"
    d.addNote(note)
    c = note.cards()[0]
    # card stats
    assert d.cardStats(c)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 3)
    d.sched.answerCard(c, 2)
    assert d.cardStats(c)


def test_graphs_empty():
    d = getEmptyCol()
    assert d.stats().report()


def test_graphs():
    dir = tempfile.gettempdir()
    d = getEmptyCol()
    g = d.stats()
    rep = g.report()
    with open(os.path.join(dir, "test.html"), "w", encoding="UTF-8") as note:
        note.write(rep)
    return
