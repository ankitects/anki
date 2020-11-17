#!/usr/bin/env python

import glob, re, json, stringcase

files = (
    # glob.glob("../../pylib/**/*.py", recursive=True)
    #    glob.glob("../../qt/**/*.py", recursive=True)
    glob.glob("../../qt/**/forms/*.ui", recursive=True)
)
string_re = re.compile(r"<string>(.*?)</string>")

map = json.load(open("keys_by_text.json"))

# unused or missing strings
blacklist = {
    "Label1",
    "After pressing OK, you can choose which tags to include.",
    "Filter/Cram",
    "Show %s",
    "~",
    "about:blank",
    # previewer.py needs updating to fix these
    "Shortcut key: R",
    "Shortcut key: B",
}

from html.entities import name2codepoint

reEnts = re.compile(r"&#?\w+;")


def decode_ents(html):
    def fixup(m):
        text = m.group(0)
        if text[:2] == "&#":
            # character reference
            try:
                if text[:3] == "&#x":
                    return chr(int(text[3:-1], 16))
                else:
                    return chr(int(text[2:-1]))
            except ValueError:
                pass
        else:
            # named entity
            try:
                text = chr(name2codepoint[text[1:-1]])
            except KeyError:
                pass
        return text  # leave as is

    return reEnts.sub(fixup, html)


def repl(m):
    text = decode_ents(m.group(1))
    if text in blacklist:
        return m.group(0)

    (module, key) = map[text]
    screaming = stringcase.constcase(key)
    print(screaming)

    return f"<string>{screaming}</string>"


for file in files:
    if file.endswith("stats.py"):
        continue
    buf = open(file).read()
    buf2 = string_re.sub(repl, buf)
    if buf != buf2:
        open(file, "w").write(buf2)
