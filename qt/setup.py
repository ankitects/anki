#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

import setuptools

with open("../meta/version") as fh:
    version = fh.read().strip()


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

setuptools.setup(
    name="aqt",
    version=version,
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
    install_requires=[
        "beautifulsoup4",
        "requests",
        "send2trash",
        "pyaudio",
        "markdown",
        "jsonschema",
        'psutil; sys.platform == "win32"',
        'pywin32; sys.platform == "win32"',
        'darkdetect; sys.platform == "darwin"',
    ],
)
