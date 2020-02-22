#!/usr/bin/env python3
#
# locate any translations that have invalid format strings
#

import os, re, sys
po_dir = "po/desktop"

msg_re = re.compile(r"^(msgid|msgid_plural|msgstr|)(\[[\d]\])? \"(.*)\"$")
cont_re = re.compile(r"^\"(.*)\"$")
pct_re = re.compile(r"%(?:\([^\)]+?\))?[#+\-\d.]*[a-zA-Z]")
path_re = re.compile(r"#: (.+)$")

def check_reps(path, t1, strs):
    allZero = True
    for s in strs:
        if s:
            allZero = False
    if allZero:
        return

    orig = t1 or ""
    for s in strs:
        if not reps_match(orig, s):
            return "{}\n{}\n{}\n".format(path, orig, strs)

def reps_match(t1, t2):
    for char in "{}":
        if t1.count(char) != t2.count(char):
            return False

    t1 = t1.replace("%%", "")
    t2 = t2.replace("%%", "")

    if t1.count("%"):
        matches = set(pct_re.findall(t1))
        matches2 = set(pct_re.findall(t2))
        return matches == matches2

    return True


def fix_po(path):
    last_msgid = None
    last_msgstr = None
    last_path = None
    lines = []
    state = "outside"
    problems = []
    strs = []

    for line in open(path):
        lines.append(line)

        # comment?
        m = path_re.match(line)
        if m:
            last_path = m.group(1)

        # starting new id/str?
        m = msg_re.match(line)
        if m:
            label, num, text = m.group(1), m.group(2), m.group(3)
            if label == "msgid":
                last_msgid = text
                state = "id"
                strs = []
            elif label == "msgid_plural":
                continue
            else:
                state = "str"
                strs.append(text)

            continue

        # continuing previous id/str?
        m = cont_re.match(line)
        if m:
            if state == "id":
                last_msgid += m.group(1)
            elif state == "str":
                strs[-1] += m.group(1)
            else:
                assert 0

            continue

        state = "outside"
        if last_msgid:
            p = check_reps(last_path, last_msgid, strs)
            if p:
                problems.append(p)
#                print("{0}\nProblems in {1}:\n{0}\n{2}".format("*"*60, path, "\n".join(problems)))
#                return 1
            last_msgid = None

    if problems:
        print("{0}\nProblems in {1}:\n{0}\n{2}".format("*"*60, path, "\n".join(problems)))

    return len(problems)

problems = 0
for fname in os.listdir(po_dir):
    path = os.path.join(po_dir, fname)
    if not os.path.isdir(path):
        continue
    path = os.path.join(path, "anki.po")
    problems += fix_po(path)

if problems:
    sys.exit(1)
