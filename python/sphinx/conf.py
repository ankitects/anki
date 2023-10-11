# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

import os
import sys

project = 'Anki'
copyright = '2023, Ankitects Pty Ltd and contributors'
author = 'Ankitects Pty Ltd and contributors'

REPO_ROOT = os.path.abspath('../../')

# Autodoc requires us to add the modules to the Python path, see
# https://stackoverflow.com/a/44980548/803801
paths = [
    'pylib',
    'pylib/anki',
    'pylib/anki/importing',
    'qt',
    'qt/aqt',
    'out/pylib',
    'out/qt',
    'out/qt/_aqt',
]

for path in paths:
    sys.path.insert(0, os.path.join(REPO_ROOT, path))

extensions = ['sphinx.ext.autodoc', 'sphinx_rtd_theme']
html_theme = 'sphinx_rtd_theme'
