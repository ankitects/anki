# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Features - extensible features like auto-reading generation
===============================================================================

Features allow the deck to define specific features that are required, but
that can be resolved in real time. This includes things like automatic reading
generation, language-specific dictionary entries, etc.
"""

from anki.lang import _
from anki.errors import *
from anki.utils import findTag, parseTags

class Feature(object):

    def __init__(self, tags=None, name="", description=""):
        if not tags:
            tags = []
        self.tags = tags
        self.name = name
        self.description = description

    def onSubmit(self, fact):
        "Apply any last-minute modifications to FACT before addition."
        pass

    def onKeyPress(self, fact):
        "Apply any changes to fact as it's being edited for the first time."
        pass

    def run(self, cmd, *args):
        "Run CMD."
        attr = getattr(self, cmd, None)
        if attr:
            attr(*args)

class FeatureManager(object):

    features = {}

    def add(feature):
        "Add a feature."
        FeatureManager.features[feature.name] = feature
    add = staticmethod(add)

    def run(tagstr, cmd, *args):
        "Run CMD on all matching features in DLIST."
        tags = parseTags(tagstr)
        for (name, feature) in FeatureManager.features.items():
            for tag in tags:
                if findTag(tag, feature.tags):
                    feature.run(cmd, *args)
                    break
    run = staticmethod(run)

# Add bundled features
import japanese
FeatureManager.add(japanese.FuriganaGenerator())
import chinese
FeatureManager.add(chinese.CantoneseGenerator())
FeatureManager.add(chinese.MandarinGenerator())
