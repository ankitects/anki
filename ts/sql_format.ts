// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sqlFormatter from "@sqltools/formatter";
import { createPatch } from "diff";
import process from "process";
import path from "path";
import fs from "fs";

const workspace = process.env.BUILD_WORKSPACE_DIRECTORY;
const wantFix = workspace !== undefined;

function fixFile(relpath: string, newText: string): void {
    const workspacePath = path.join(workspace!, relpath);
    fs.writeFileSync(workspacePath, newText);
}

function formatText(text: string): string {
    const newText: string = sqlFormatter.format(text, {
        indent: "  ",
        reservedWordCase: "upper",
    });
    // 'type' is treated as a reserved word, but Anki uses it in various sql
    // tables, so we don't want it uppercased
    return newText.replace(/\bTYPE\b/g, "type");
}

let errorFound = false;
for (const path of process.argv.slice(2)) {
    const orig = fs.readFileSync(path).toString();
    const formatted = formatText(orig);
    if (orig !== formatted) {
        if (wantFix) {
            fixFile(path, formatted);
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
    console.log("Use 'bazel run //rslib:sql_format' to fix.");
    console.log(process.env.BUILD_WORKSPACE_DIRECTORY);
    process.exit(1);
}
