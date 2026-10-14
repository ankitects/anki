# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""https://developers.openai.com/api/docs/guides/tools-computer-use#use-code-execution"""

import contextlib
import sys
import time

import pyautogui
from mcp.server.fastmcp import FastMCP, Image
from mcp.types import ImageContent

mcp = FastMCP("anki-desktop")
namespace = {"pyautogui": pyautogui, "time": time}


@mcp.tool(structured_output=False)
def exec_py(code: str) -> list[ImageContent]:
    """Run persistent Python with PyAutoGUI; return the 1280×900 desktop screenshot."""
    with contextlib.redirect_stdout(sys.stderr):
        exec(code, namespace)
    time.sleep(0.3)
    pyautogui.screenshot().save("/artifacts/screenshot.png")
    screenshot = Image(path="/artifacts/screenshot.png").to_image_content()
    screenshot.meta = {"codex/imageDetail": "original"}
    return [screenshot]


mcp.run()
