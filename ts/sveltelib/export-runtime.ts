// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// Expose the Svelte runtime bundled with Anki, so that add-ons can require() it.
// If they were to bundle their own runtime, things like bindings and contexts
// would not work.

import { registerPackageRaw } from "@tslib/runtime-require";
import * as svelteRuntime from "svelte/internal";
import * as svelteStore from "svelte/store";

registerPackageRaw("svelte/internal", svelteRuntime);
registerPackageRaw("svelte/store", svelteStore);
