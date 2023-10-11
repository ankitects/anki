# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

import os
import sys

project = 'Anki'
copyright = '2023, dae'
author = 'dae'

# https://stackoverflow.com/a/44980548/803801
sys.path.insert(0, os.path.abspath('../../pylib'))
sys.path.insert(0, os.path.abspath('../../pylib/anki'))
sys.path.insert(0, os.path.abspath('../../pylib/anki/importing'))
sys.path.insert(0, os.path.abspath('../../qt/'))
sys.path.insert(0, os.path.abspath('../../qt/aqt'))
sys.path.insert(0, os.path.abspath('../../out/pylib'))
sys.path.insert(0, os.path.abspath('../../out/qt'))
sys.path.insert(0, os.path.abspath('../../out/qt/_aqt'))

extensions = ['sphinx.ext.autodoc', 'sphinx_rtd_theme']
html_theme = 'sphinx_rtd_theme'
