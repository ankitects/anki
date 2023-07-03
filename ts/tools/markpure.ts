// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as fs from "fs";
import * as path from "path";

const root = process.argv[2];
const typeRe = /(make(Enum|MessageType))\(\n\s+".*",/g;

fs.readdirSync(root, { withFileTypes: true }).forEach(dirEnt => {
    const dirPath = path.join(root, dirEnt.name);

    if (dirEnt.isDirectory()) {
        fs.readdirSync(dirPath).forEach(fileName => {
            if (fileName.endsWith(".js")) {
                const file = path.join(dirPath, fileName);
                let contents = fs.readFileSync(file, "utf8");

                // allow tree shaking on proto messages
                contents = contents.replace(
                    "= proto3.make",
                    "= /* @__PURE__ */ proto3.make",
                );

                // strip out typeName info, which appears to only be required for
                // certain JSON functionality (though this only saves a few hundred
                // bytes)
                contents = contents.replace(typeRe, "$1(\"\",");

                fs.writeFileSync(file, contents, "utf8");
            }
        });
    }
});
