// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// Expose the Svelte runtime bundled with Anki, so that add-ons can require() it.
// If they were to bundle their own runtime, things like bindings and contexts
// would not work.

import { runtimeLibraries } from "../lib/runtime-require";
import * as svelteRuntime from "svelte/internal";

runtimeLibraries["svelte/internal"] = svelteRuntime;
