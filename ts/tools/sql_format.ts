// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { createPatch } from "diff";
import { readFileSync, writeFileSync } from "fs";
import { argv } from "process";
import { format } from "sql-formatter";

function formatText(text: string): string {
    let newText: string = format(text, {
        language: "sqlite",
        tabWidth: 2,
        keywordCase: "upper",
        paramTypes: {
            numbered: ["?", "$"],
            named: [":", "@", "$"],
        },
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

// Files that are intentionally incomplete SQL fragments, completed in Rust at runtime.
const skipFiles = ["field_names_for_notes.sql"];

const [_tsx, _script, mode, ...files] = argv;
const wantFix = mode == "fix";
let errorFound = false;
for (const path of files) {
    if (skipFiles.some((name) => path.endsWith(name))) {
        continue;
    }
    const orig = readFileSync(path).toString();
    let formatted: string;
    try {
        formatted = formatText(orig);
    } catch (e) {
        console.log(`Could not parse ${path}: ${(e as Error).message.split("\n")[0]}`);
        errorFound = true;
        continue;
    }
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
