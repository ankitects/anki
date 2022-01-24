// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { promiseWithResolver } from "./promise";
import { registerPackage } from "./register-package";

const [uiDidLoad, uiResolve] = promiseWithResolver();

registerPackage("anki/ui", {
    uiDidLoad,
});

export { uiResolve };
