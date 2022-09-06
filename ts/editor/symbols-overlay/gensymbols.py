# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import sys
import urllib.request

[symbols_out] = sys.argv[1:]

def load_github_symbols():
    output = []

    with urllib.request.urlopen('https://raw.githubusercontent.com/github/gemoji/master/db/emoji.json') as response:
        github_symbols = json.load(response)

        for entry in github_symbols:
            output.append({
                              'names': entry["aliases"],
                              'symbol': entry["emoji"],
                          })

    return output

def load_html_entities():
    output = []

    with urllib.request.urlopen('https://html.spec.whatwg.org/entities.json') as response:
        html_entities = json.load(response)

        for symbol_name_full in html_entities:
            if not symbol_name_full.endswith(';'):
                # these appear twice in the list
                continue

            symbol_name = symbol_name_full.removeprefix("&").removesuffix(";")

            if symbol_name in ["LT", "GT", "amp"]:
                continue

            symbol = html_entities[symbol_name_full]['characters']

            try:
                duplicate = next(item for item in output if item["symbol"] == symbol)
                duplicate['names'].append(symbol_name)
            except StopIteration:
                output.append({
                                  'names': [symbol_name],
                                  'symbol': symbol,
                              })

    return output

def write(outfile, out) -> None:
    open(outfile, "wb").write(
        (
            f"""// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export default {out};
"""
        ).encode("utf8")
    )

write(symbols_out, json.dumps(load_github_symbols() + load_html_entities()))
