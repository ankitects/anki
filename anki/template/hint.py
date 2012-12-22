# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.hooks import addHook
from anki.lang import _

def hint(txt, extra, context, tag, fullname):
    if not txt.strip():
        return ""
    # random id
    domid = "hint%d" % id(txt)
    return """
<a class=hint href="#"
onclick="this.style.display='none';document.getElementById('%s').style.display='block';return false;">
%s</a><div id="%s" class=hint style="display: none">%s</div>
""" % (domid, _("Show %s") % tag, domid, txt)

def install():
    addHook('fmod_hint', hint)
