# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import sys
from typing import List

import stringcase

strings_json, outfile = sys.argv[1:]
modules = json.load(open(strings_json))


def legacy_enum() -> str:
    out = ["export enum LegacyEnum {"]
    for module in modules:
        for translation in module["translations"]:
            key = stringcase.constcase(translation["key"])
            value = module["index"] * 1000 + translation["index"]
            out.append(f"    {key} = {value},")

    out.append("}")
    return "\n".join(out) + "\n"


def methods() -> str:
    out = [
        "class AnkiTranslations:",
        "    def _translate(self, module: int, translation: int, args: Dict) -> str:",
        "        raise Exception('not implemented')",
    ]
    for module in modules:
        for translation in module["translations"]:
            key = translation["key"].replace("-", "_")
            arg_types = get_arg_types(translation["variables"])
            args = get_args(translation["variables"])
            doc = translation["text"]
            out.append(
                f"""
    def {key}(self, {arg_types}) -> str:
        r''' {doc} '''
        return self._translate({module["index"]}, {translation["index"]}, {{{args}}})
"""
            )

    return "\n".join(out) + "\n"


def get_arg_types(args: List[str]) -> str:
    return ", ".join([f"{stringcase.snakecase(arg)}: FluentVariable" for arg in args])


def get_args(args: List[str]) -> str:
    return ", ".join([f'"{arg}": {stringcase.snakecase(arg)}' for arg in args])


out = ""

out += legacy_enum()
# out += methods()


open(outfile, "wb").write(
    (
        """// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
        + out
    ).encode("utf8")
)
