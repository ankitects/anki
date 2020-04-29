#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import setuptools

install_requires = [
    "beautifulsoup4",
    "requests",
    "decorator",
    "protobuf",
    'orjson; platform_machine == "x86_64"',
    'psutil; sys_platform == "win32"',
    'distro; sys_platform != "darwin" and sys_platform != "win32"',
]


setuptools.setup(
    name="anki",
    version="2.1.25",  # automatically updated
    author="Ankitects Pty Ltd",
    description="Anki's library code",
    long_description="Anki's library code",
    long_description_content_type="text/markdown",
    url="https://apps.ankiweb.net",
    packages=setuptools.find_packages(".", exclude=["tests"]),
    license="License :: OSI Approved :: GNU Affero General Public License v3 or later (AGPLv3+)",
    package_data={"anki": ["py.typed"]},
    classifiers=[],
    python_requires=">=3.7",
    install_requires=install_requires,
)
