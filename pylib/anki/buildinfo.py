# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from importlib.resources import open_text


def _get_build_info() -> dict[str, str]:
    info = {}
    with open_text("anki", "buildinfo.txt") as file:
        for line in file.readlines():
            elems = line.split()
            if len(elems) == 2:
                key, val = elems
                info[key] = val

    return info


_buildinfo = _get_build_info()
buildhash = _buildinfo["STABLE_BUILDHASH"]
version = _buildinfo["STABLE_VERSION"]
