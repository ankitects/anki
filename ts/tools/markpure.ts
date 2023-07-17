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
        let contents = fs.readFileSync(file, "utf8");

        // allow tree shaking on proto messages
        contents = contents.replace(
            /= proto3.make/g,
            "= /* @__PURE__ */ proto3.make",
        );

        // strip out typeName info, which appears to only be required for
        // certain JSON functionality (though this only saves a few hundred
        // bytes)
        contents = contents.replace(typeRe, "$1(\"\",");

        fs.writeFileSync(file, contents, "utf8");
    }
}

adjustFiles();
