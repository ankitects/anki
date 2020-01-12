# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This file contains the Python portion of the template rendering code.

Templates can have filters applied to field replacements. The Rust template
rendering code will apply any built in filters, and stop at the first
unrecognized filter. The remaining filters are returned to Python,
and applied using the hook system. For example,
{{myfilter:hint:text:Field}} will apply the built in text and hint filters,
and then attempt to apply myfilter. If no add-ons have provided the filter,
the filter is skipped.

Add-ons can register a filter by adding a hook to "fmod_<filter name>".
As standard filters will not be run after a custom filter, it is up to the
add-on to do any further processing that is required.

The hook is called with the arguments
(field_text, filter_args, field_map, field_name, "").
The last argument is no longer used.
If the field name contains a hyphen, it is split on the hyphen, eg
{{foo-bar:baz}} calls fmod_foo with filter_args set to "bar".

A Python implementation of the standard filters is currently available in the
template_legacy.py file.
"""

from __future__ import annotations

import re
from typing import Dict, List, Optional, Tuple

import anki
from anki.hooks import runFilter
from anki.rsbackend import TemplateReplacementList


def render_card(
    col: anki.storage._Collection,
    qfmt: str,
    afmt: str,
    fields: Dict[str, str],
    card_ord: int,
) -> Tuple[str, str]:
    "Renders the provided templates, returning rendered q & a text."

    (qnodes, anodes) = col.backend.render_card(qfmt, afmt, fields, card_ord)

    qtext = apply_custom_filters(qnodes, fields, front_side=None)
    atext = apply_custom_filters(anodes, fields, front_side=qtext)

    return qtext, atext


def apply_custom_filters(
    rendered: TemplateReplacementList, fields: Dict[str, str], front_side: Optional[str]
) -> str:
    "Complete rendering by applying any pending custom filters."
    # template already fully rendered?
    if len(rendered) == 1 and isinstance(rendered[0], str):
        return rendered[0]

    res = ""
    for node in rendered:
        if isinstance(node, str):
            res += node
        else:
            # do we need to inject in FrontSide?
            if node.field_name == "FrontSide" and front_side is not None:
                node.current_text = front_side

            res += apply_field_filters(
                node.field_name, node.current_text, fields, node.filters
            )
    return res


def apply_field_filters(
    field_name: str, field_text: str, fields: Dict[str, str], filters: List[str]
) -> str:
    """Apply filters to field text, returning modified text."""
    for filter in filters:
        if "-" in filter:
            filter_base, filter_args = filter.split("-", maxsplit=1)
        else:
            filter_base = filter
            filter_args = ""

        # the fifth argument is no longer used
        field_text = runFilter(
            "fmod_" + filter_base, field_text, filter_args, fields, field_name, ""
        )
    return field_text


# Cloze handling
##########################################################################

# Matches a {{c123::clozed-out text::hint}} Cloze deletion, case-insensitively.
# The regex should be interpolated with a regex number and creates the following
# named groups:
#   - tag: The lowercase or uppercase 'c' letter opening the Cloze.
#          The c/C difference is only relevant to the legacy code.
#   - content: Clozed-out content.
#   - hint: Cloze hint, if provided.
clozeReg = r"(?si)\{\{(?P<tag>c)%s::(?P<content>.*?)(::(?P<hint>.*?))?\}\}"

# Constants referring to group names within clozeReg.
CLOZE_REGEX_MATCH_GROUP_TAG = "tag"
CLOZE_REGEX_MATCH_GROUP_CONTENT = "content"
CLOZE_REGEX_MATCH_GROUP_HINT = "hint"

# used by the media check functionality
def expand_clozes(string: str) -> List[str]:
    "Render all clozes in string."
    ords = set(re.findall(r"{{c(\d+)::.+?}}", string))
    strings = []

    def qrepl(m):
        if m.group(CLOZE_REGEX_MATCH_GROUP_HINT):
            return "[%s]" % m.group(CLOZE_REGEX_MATCH_GROUP_HINT)
        else:
            return "[...]"

    def arepl(m):
        return m.group(CLOZE_REGEX_MATCH_GROUP_CONTENT)

    for ord in ords:
        s = re.sub(clozeReg % ord, qrepl, string)
        s = re.sub(clozeReg % ".+?", arepl, s)
        strings.append(s)
    strings.append(re.sub(clozeReg % ".+?", arepl, string))

    return strings
