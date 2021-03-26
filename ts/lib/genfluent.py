# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import sys
from typing import List
from typing import List, Literal, TypedDict

import stringcase

strings_json, outfile = sys.argv[1:]
modules = json.load(open(strings_json))


class Variable(TypedDict):
    name: str
    kind: Literal["Any", "Int", "String", "Float"]


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
        "export class GeneratedTranslations {",
        "    translate(key: string, args?: Record<string, any>): string { return 'nyi' } ",
    ]
    for module in modules:
        for translation in module["translations"]:
            key = stringcase.camelcase(translation["key"].replace("-", "_"))
            arg_types = get_arg_name_and_types(translation["variables"])
            args = get_args(translation["variables"])
            doc = translation["text"]
            out.append(
                f"""
    /** {doc} */
    {key}({arg_types}): string {{
        return this.translate("{translation["key"]}"{args})
    }}
"""
            )

    out.append("}")

    return "\n".join(out) + "\n"


def get_arg_name_and_types(args: List[Variable]) -> str:
    if not args:
        return ""
    else:
        return (
            "args: {"
            + ", ".join(
                [f"{typescript_arg_name(arg)}: {arg_kind(arg)}" for arg in args]
            )
            + "}"
        )


def arg_kind(arg: Variable) -> str:
    if arg["kind"] in ("Int", "Float"):
        return "number"
    elif arg["kind"] == "Any":
        return "number | string"
    else:
        return "string"


def get_args(args: List[Variable]) -> str:
    if not args:
        return ""
    else:
        return ", args"


def typescript_arg_name(arg: Variable) -> str:
    name = stringcase.camelcase(arg["name"])
    if name == "new":
        return "new_"
    else:
        return name


out = ""

out += legacy_enum()
out += methods()


open(outfile, "wb").write(
    (
        """// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
        + out
    ).encode("utf8")
)
