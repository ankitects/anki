#!/bin/bash
#
# Install runtime requirements into a venv and extract their licenses.
# As Windows currently uses extra deps, running this on Windows should
# capture all packages.
# Run with 'bash licenses.sh' to update 'license.json'

set -e

# setup venv
python -m venv venv

# build wheels
../bazel.bat --output_base=/c/bazel/anki/base build //pylib/anki:wheel //qt/aqt:wheel

# install wheels, bound to constrained versions
venv/tools/pip install -c requirements.txt ../bazel-bin/pylib/anki/*.whl ../bazel-bin/qt/aqt/*.whl pip-licenses

# dump licenses - ptable is a pip-licenses dep
venv/tools/pip-licenses --format=json --ignore-packages anki aqt pip-license PTable > licenses.json

# clean up
rm -rf venv
