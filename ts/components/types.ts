// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type Pane from "./Pane.svelte";

export type Size = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12;
export type Breakpoint = "xs" | "sm" | "md" | "lg" | "xl" | "xxl";

export class ResizablePane {
    resizable = {} as Pane;
    height = 0;
    minHeight = 0;
    maxHeight = Infinity;
}
