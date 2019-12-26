import contextlib
import io
import json
import sys
import re

from anki import template as template_module
from pythonfuzz.main import PythonFuzz


def fuzz_template_render(string):
    """Checks that template constructor does not crash on given string."""
    context = {}
    if "|" in string:
        try:
            j, _, string = string.partition('|')
            context = json.loads(j)
        except ValueError:
            return

    template_module.render(string, context)


@PythonFuzz
def template_render_fuzz(buf):
    try:
        fuzz_template_render(buf.decode("ascii"))
    except UnicodeDecodeError:
        return

if __name__ == '__main__':
    template_render_fuzz()
