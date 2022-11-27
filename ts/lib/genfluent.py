# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import sys
from typing import List, Literal, TypedDict

sys.path.append("pylib/anki/_vendor")

import stringcase

strings_json, ftl_js_path, ftl_dts_path, modules_js_path, modules_dts_path = sys.argv[
    1:
]
with open(strings_json, encoding="utf8") as f:
    modules = json.load(f)


class Variable(TypedDict):
    name: str
    kind: Literal["Any", "Int", "String", "Float"]


def write_methods() -> None:
    js_out = [
        """
// tslib is responsible for injecting getMessage helper in
export const funcs = {};

function translate(key, args = {}) {
    return funcs.getMessage(key, args) ?? `missing key: ${key}`;
}
"""
    ]
    dts_out = ["export declare const funcs: any;"]
    for module in modules:
        for translation in module["translations"]:
            key = stringcase.camelcase(translation["key"].replace("-", "_"))
            arg_types = get_arg_name_and_types(translation["variables"])
            args = get_args(translation["variables"])
            doc = translation["text"]
            dts_out.append(
                f"""
/** {doc} */
export declare function {key}({arg_types}): string;
"""
            )
            js_out.append(
                f"""
export function {key}({"args" if arg_types else ""}) {{
    return translate("{translation["key"]}"{args})
}}
"""
            )

    write(ftl_dts_path, "\n".join(dts_out) + "\n")
    write(ftl_js_path, "\n".join(js_out) + "\n")


def write_modules() -> None:
    dts_buf = "export declare enum ModuleName {\n"
    for module in modules:
        name = module["name"]
        upper = name.upper()
        dts_buf += f'    {upper} = "{name}",\n'
    dts_buf += "}\n"
    js_buf = "export const ModuleName = {};\n"
    for module in modules:
        name = module["name"]
        upper = name.upper()
        js_buf += f'ModuleName["{upper}"] = "{name}";\n'
    write(modules_dts_path, dts_buf)
    write(modules_js_path, js_buf)


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


def write(outfile, out) -> None:
    with open(outfile, "w", encoding="utf8") as f:
        f.write(
            f"""// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
            + out
        )


write_methods()
write_modules()
