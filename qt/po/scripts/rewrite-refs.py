#!/usr/bin/env python

import glob, re, json, stringcase

files = (
    # glob.glob("../../pylib/**/*.py", recursive=True) +
    glob.glob("../../qt/**/*.py", recursive=True)
)
string_re = re.compile(r'_\(\s*(".*?")\s*\)')

map = json.load(open("keys_by_text.json"))

# unused or missing strings
blacklist = {
    "Label1",
    "After pressing OK, you can choose which tags to include.",
    "Filter/Cram",
    # previewer.py needs updating to fix these
    "Shortcut key: R",
    "Shortcut key: B",
}


def repl(m):
    # the argument may consistent of multiple strings that need merging together
    text = eval(m.group(1))

    if text in blacklist:
        return m.group(0)

    (module, key) = map[text]
    screaming = stringcase.constcase(key)
    print(screaming)

    if "%d" in text or "%s" in text:
        # replace { $val } with %s for compat with old code
        return f'tr(TR.{screaming}, val="%s")'

    return f"tr(TR.{screaming})"


for file in files:
    if file.endswith("stats.py"):
        continue
    buf = open(file).read()
    buf2 = string_re.sub(repl, buf)
    if buf != buf2:
        lines = buf2.split("\n")
        lines.insert(3, "from aqt.utils import tr, TR")
        buf2 = "\n".join(lines)
        open(file, "w").write(buf2)
