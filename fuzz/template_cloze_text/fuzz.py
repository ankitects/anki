import contextlib
import io
import sys
import re

from anki import template as template_module
from pythonfuzz.main import PythonFuzz


# Matches a <span class=cloze within MathJax display math -- \[...\].
HTML_CLOZE_WITHIN_MATHJAX_DISPLAY = re.compile(
    r"\\\[[^\\]*<span class=cloze[^\\]*\\\]")
# Matches a <span class=cloze within MathJax display math -- \(...\).
HTML_CLOZE_WITHIN_MATHJAX_INLINE = re.compile(
    r"\\\([^\\]*<span class=cloze[^\\]*\\\)")
# Matches the expected input format for fuzz_cloze_string.
CLOZE_INPUT_FORMAT = re.compile("([qa])(\d+)(.*)\Z", flags=re.DOTALL)


def fuzz_cloze_text(string):
    """Checks clozeText's behavior on a given fuzzing input.

    Takes a string in the format q123Hello, where q is a q/a flag, 123 is
    the cloze number and Hello is the template to render, and checks that
    clozeText does not violate checked invariants on the input.
    """
    if len(string) < 3:
        return
    if string[0] not in "qa":
        return
    if string[1] not in "0123456789":
        return
    g = CLOZE_INPUT_FORMAT.match(string)
    type = g.group(1)
    ord = int(g.group(2))
    text = g.group(3)
    # clozeText currently prints to stdout on certain parsing issues. Suppress
    # the output.
    f = io.StringIO()
    with contextlib.redirect_stdout(f):
        result = template_module.Template("").clozeText(text, ord, type)

    # If the template does not contain <'s, then there should be no Cloze
    # tags within MathJax. (If the template contains <'s, then it could
    # decide to spell out '<span class=cloze' inside a MathJax section.
    if '<' not in string:
        assert not HTML_CLOZE_WITHIN_MATHJAX_DISPLAY.search(result)
        assert not HTML_CLOZE_WITHIN_MATHJAX_INLINE.search(result)


@PythonFuzz
def template_cloze_text_fuzz(buf):
    try:
        fuzz_cloze_text(buf.decode("ascii"))
    except UnicodeDecodeError:
        return

if __name__ == '__main__':
    template_cloze_text_fuzz()
