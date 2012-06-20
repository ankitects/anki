# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
from anki.hooks import addHook

def hint(txt, extra, context, tag, fullname):
    if not txt.strip():
        return ""
    # random id
    domid = "hint%d" % id(txt)
    return """
<a href="#"
onclick="this.style.display='none';document.getElementById('%s').style.display='block';return false;">
%s</a><div id="%s" style="display: none">%s</div>
""" % (domid, _("Show %s") % tag, domid, txt)

def install():
    addHook('fmod_hint', hint)
