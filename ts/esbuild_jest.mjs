// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { transformSync } from "esbuild";

export default {
    createTransformer(userOptions) {
        return {
            canInstrument: true,
            process(sourceText, sourcePath) {
                const options = {
                    format: "cjs",
                    sourcemap: "both",
                    target: `node18`,
                    loader: "ts",
                    ...userOptions,
                    sourcefile: sourcePath,
                };
                return transformSync(sourceText, options);
            },
        };
    },
};
