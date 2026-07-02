// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// Expose the Svelte runtime bundled with Anki, so that add-ons can require() it.
// If they were to bundle their own runtime, things like bindings and contexts
// would not work.

import { registerPackageRaw } from "@tslib/runtime-require";
import * as svelteRuntime from "svelte";
// import * as svelteInternal from "svelte/internal";
// import * as svelteDiscloseVersion from "svelte/internal/disclose-version";
import * as svelteStore from "svelte/store";

registerPackageRaw("svelte", svelteRuntime);
registerPackageRaw("svelte/store", svelteStore);
// registerPackageRaw("svelte/internal", svelteInternal);
// registerPackageRaw("svelte/internal/disclose-version", svelteDiscloseVersion);
