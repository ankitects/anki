import os, sys
from setuptools import setup

setup(
    name="anki",
    version="2.1.0a8", # Taken from __init__.py
    author="Damien Elmes",
    description=("Anki for desktop computers"),
    install_requires=[
        "beautifulsoup4",
        "send2trash",
        "httplib2",
        "pyaudio",
        "requests"
    ],
    packages=["anki"]
)
