// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// Global css classes used by subcomponents

// Graph.svelte
export const oddTickClass = "tick-odd";
export const clickableClass = "graph-element-clickable";

// It would be nice to define these in the svelte file that declares them,
// but currently this trips the tooling up:
// https://github.com/sveltejs/svelte/issues/5817
// export { oddTickClass, clickableClass } from "./Graph.svelte";
