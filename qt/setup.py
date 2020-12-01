#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

import setuptools


def package_files(directory):
    entries = []
    for (path, directories, filenames) in os.walk(directory):
        entries.append((path, [os.path.join(path, f) for f in filenames]))
    return entries


# just the Python files for type hints?
pyonly = os.getenv("PYFILESONLY")

if pyonly:
    extra_files = []
else:
    extra_files = package_files("aqt_data")

install_requires = [
    "beautifulsoup4",
    "requests",
    "send2trash",
    "markdown",
    "jsonschema",
    # "pyaudio", # https://anki.tenderapp.com/discussions/add-ons/44009-problems-with-code-completion
    # "pyqtwebengine", # https://github.com/ankitects/anki/pull/530 - Set to checks.yml install and import anki wheels
    "flask",
    "flask_cors",
    "waitress",
    "pyqt5>=5.9",
    'psutil; sys.platform == "win32"',
    'pywin32; sys.platform == "win32"',
    "anki==2.1.35",  # automatically updated 1
    "bintrees"
]


setuptools.setup(
    name="aqt",
    version="2.1.35",  # automatically updated 2
    author="Ankitects Pty Ltd",
    description="Anki's Qt GUI code",
    long_description="Anki's QT GUI code",
    long_description_content_type="text/markdown",
    url="https://apps.ankiweb.net",
    packages=setuptools.find_packages(".", exclude=["tests"]),
    data_files=extra_files,
    license="License :: OSI Approved :: GNU Affero General Public License v3 or later (AGPLv3+)",
    classifiers=[],
    python_requires=">=3.7",
    package_data={"aqt": ["py.typed"]},
    install_requires=install_requires,
)
