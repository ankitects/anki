// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sqlFormatter from "@sqltools/formatter";
import { createPatch } from "diff";
import { readFileSync, writeFileSync } from "fs";
import { argv } from "process";

function formatText(text: string): string {
    let newText: string = sqlFormatter.format(text, {
        indent: "  ",
        reservedWordCase: "upper",
    });
    // downcase some keywords that Anki uses in tables/columns
    for (const keyword of ["type", "fields"]) {
        newText = newText.replace(
            new RegExp(`\\b${keyword.toUpperCase()}\\b`, "g"),
            keyword,
        );
    }
    return newText;
}

const [_tsx, _script, mode, ...files] = argv;
const wantFix = mode == "fix";
let errorFound = false;
for (const path of files) {
    const orig = readFileSync(path).toString();
    const formatted = formatText(orig);
    if (orig !== formatted) {
        if (wantFix) {
            writeFileSync(path, formatted);
            console.log(`Fixed ${path}`);
        } else {
            if (!errorFound) {
                errorFound = true;
                console.log("SQL formatting issues found:");
            }
            console.log(createPatch(path, orig, formatted));
        }
    }
}
if (errorFound) {
    process.exit(1);
}
