# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

import os

project = 'Anki'
copyright = '2023, Ankitects Pty Ltd and contributors'
author = 'Ankitects Pty Ltd and contributors'

REPO_ROOT = os.environ["REPO_ROOT"]

extensions = ['sphinx_rtd_theme', 'autoapi.extension']
html_theme = 'sphinx_rtd_theme'
autoapi_python_use_implicit_namespaces = True
autoapi_dirs = [os.path.join(REPO_ROOT, x) for x in ["pylib/anki", "out/pylib/anki", "qt/aqt", "out/qt/_aqt"]]
