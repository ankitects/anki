# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import sys
from typing import List, Literal, TypedDict

import stringcase

strings_json, outfile = sys.argv[1:]
modules = json.load(open(strings_json, encoding="utf8"))


class Variable(TypedDict):
    name: str
    kind: Literal["Any", "Int", "String", "Float"]


def methods() -> str:
    out = [
        'import { i18n } from "./i18n_helpers";',
        'export { i18n, setupI18n } from "./i18n_helpers";',
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
export function {key}({arg_types}): string {{
    return i18n.translate("{translation["key"]}"{args})
}}
"""
            )

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


def map_args_to_real_names(args: List[Variable]) -> str:
    return (
        "{"
        + ", ".join(
            [f'"{arg["name"]}": args.{typescript_arg_name(arg)}' for arg in args]
        )
        + "}"
    )


def get_args(args: List[Variable]) -> str:
    if not args:
        return ""
    else:
        for arg in args:
            if typescript_arg_name(arg) != arg["name"]:
                # we'll need to map variables to their fluent equiv
                return ", " + map_args_to_real_names(args)

        # variable names match, reference object instead
        return ", args"


def typescript_arg_name(arg: Variable) -> str:
    name = stringcase.camelcase(arg["name"].replace("-", "_"))
    if name == "new":
        return "new_"
    else:
        return name


def module_names() -> str:
    buf = "export enum ModuleName {\n"
    for module in modules:
        name = module["name"]
        upper = name.upper()
        buf += f'    {upper} = "{name}",\n'
    buf += "}\n"
    return buf


out = ""

out += methods()
out += module_names()

open(outfile, "wb").write(
    (
        """// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
        + out
    ).encode("utf8")
)
