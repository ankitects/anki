# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, cast

from anki import Collection
from anki.collection import SearchNode
from aqt import colors
from aqt.theme import ColoredIcon
from aqt.utils import tr


@dataclass
class Flag:
    """A container class for flag related data.

    index -- The integer by which the flag is represented internally (1-7).
    label -- The text by which the flag is described in the GUI.
    icon -- The icon by which the flag is represented in the GUI.
    search_node -- The node to build a search string for finding cards with the flag.
    action -- The name of the action to assign the flag in the browser form.
    """

    index: int
    label: str
    icon: ColoredIcon
    search_node: SearchNode
    action: str


def load_flags(col: Collection) -> List[Flag]:
    """Return a list of all flags, reloading labels from the config."""

    labels = cast(Dict[str, str], col.get_config("flagLabels", {}))
    icon = ColoredIcon(path=":/icons/flag.svg", color=colors.DISABLED)

    return [
        Flag(
            1,
            labels["1"] if "1" in labels else tr.actions_red_flag(),
            icon.with_color(colors.FLAG1_FG),
            SearchNode(flag=SearchNode.FLAG_RED),
            "actionRed_Flag",
        ),
        Flag(
            2,
            labels["2"] if "2" in labels else tr.actions_orange_flag(),
            icon.with_color(colors.FLAG2_FG),
            SearchNode(flag=SearchNode.FLAG_ORANGE),
            "actionOrange_Flag",
        ),
        Flag(
            3,
            labels["3"] if "3" in labels else tr.actions_green_flag(),
            icon.with_color(colors.FLAG3_FG),
            SearchNode(flag=SearchNode.FLAG_GREEN),
            "actionGreen_Flag",
        ),
        Flag(
            4,
            labels["4"] if "4" in labels else tr.actions_blue_flag(),
            icon.with_color(colors.FLAG4_FG),
            SearchNode(flag=SearchNode.FLAG_BLUE),
            "actionBlue_Flag",
        ),
    ]
