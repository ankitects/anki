# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Version helper for wheel builds."""

import pathlib

# Read version from .version file in project root
_version_file = pathlib.Path(__file__).parent.parent / ".version"
__version__ = _version_file.read_text().strip()
