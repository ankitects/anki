// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { promiseWithResolver } from "./promise";
import { registerPackage } from "./runtime-require";

const [loaded, uiResolve] = promiseWithResolver();

registerPackage("anki/ui", {
    loaded,
});

export { uiResolve };
