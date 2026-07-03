// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// extend the global namespace with our exports - not sure if there's a better way with esbuild
import * as globals from "./index";

for (const key in globals) {
    window[key] = globals[key];
}
