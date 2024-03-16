// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as fs from "fs";
import * as path from "path";

function allFilesInDir(directory): string[] {
    let results: string[] = [];
    const list = fs.readdirSync(directory);

    list.forEach(function(file) {
        file = path.join(directory, file);
        const stat = fs.statSync(file);

        if (stat && stat.isDirectory()) {
            results = results.concat(allFilesInDir(file));
        } else {
            results.push(file);
        }
    });

    return results;
}

function adjustFiles() {
    const root = process.argv[2];
    const typeRe = /(make(Enum|MessageType))\(\n\s+".*",/g;

    const jsFiles = allFilesInDir(root).filter(f => f.endsWith(".js"));
    for (const file of jsFiles) {
        const contents = fs.readFileSync(file, "utf8");

        // strip out typeName info, which appears to only be required for
        // certain JSON functionality (though this only saves a few hundred
        // bytes)
        const newContents = contents.replace(typeRe, "$1(\"\",");

        if (contents != newContents) {
            fs.writeFileSync(file, newContents, "utf8");
        }
    }
}

adjustFiles();
