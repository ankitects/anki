# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# a quick script to compare methods in the two schedulers

import inspect
import sys
from difflib import SequenceMatcher, unified_diff

from anki.sched import Scheduler as S1
from anki.schedv2 import Scheduler as S2

s1map = {}
for k, v in S1.__dict__.items():
    if not callable(v):
        continue
    s1map[k] = v

s2map = {}
for k, v in S2.__dict__.items():
    if not callable(v):
        continue
    s2map[k] = v

for k, v in s1map.items():
    if k not in s2map:
        continue

    s1b = inspect.getsource(v)
    s2b = inspect.getsource(s2map[k])
    ratio = SequenceMatcher(None, s1b, s2b).ratio()

    if ratio >= 0.90:
        print("*" * 80)
        print(k, "%d%%" % (ratio * 100))
        sys.stdout.writelines(
            "\n".join(unified_diff(s1b.splitlines(), s2b.splitlines(), lineterm=""))
        )
        print()
