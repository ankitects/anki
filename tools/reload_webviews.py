#!/usr/bin/env python
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Trigger a reload of Anki's web views using QtWebEngine' Chromium
Remote Debugging interface.
"""

import argparse

import PyChromeDevTools

DEFAULT_HOST = "localhost"
DEFAULT_PORT = 8080

parser = argparse.ArgumentParser("reload_webviews")
parser.add_argument(
    "--host",
    help=f"Host via which the Chrome session can be reached, e.g. {DEFAULT_HOST}",
    type=str,
    default=DEFAULT_HOST,
    required=False,
)
parser.add_argument(
    "--port",
    help=f"Port via which the Chrome session can be reached, e.g. {DEFAULT_PORT}",
    type=str,
    default=DEFAULT_PORT,
    required=False,
)
args = parser.parse_args()

chrome = PyChromeDevTools.ChromeInterface(host=args.host, port=args.port)

if chrome.tabs is None:
    print("Could not establish connection to Chromium remote debugger")
    exit(1)

for tab_index, tab_data in enumerate(chrome.tabs):
    print(f"Reloading page: {tab_data['title']}")
    chrome.connect(tab=tab_index, update_tabs=False)
    chrome.Page.reload()
