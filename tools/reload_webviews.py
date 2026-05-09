#!/usr/bin/env python
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Trigger a reload of Anki's web views using QtWebEngine' Chromium
Remote Debugging interface.
"""

import argparse
import sys

import PyChromeDevTools  # type: ignore[import]

DEFAULT_HOST = "localhost"
DEFAULT_PORT = 8080


def print_error(message: str):
    print(f"Error: {message}", file=sys.stderr)


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

try:
    chrome = PyChromeDevTools.ChromeInterface(host=args.host, port=args.port)
except Exception as e:
    print_error(
        f"Could not establish connection to Chromium remote debugger. Is Anki Open? Exception:\n{e}"
    )
    sys.exit(1)

if chrome.tabs is None:
    print_error("Was unable to get active web views.")
    sys.exit(1)

for tab_index, tab_data in enumerate(chrome.tabs):
    print(f"Reloading page: {tab_data['title']}")
    chrome.connect(tab=tab_index, update_tabs=False)
    chrome.Page.reload()
