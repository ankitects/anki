// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";
import type { SvelteComponentDev } from "svelte/internal";

export interface ToolbarItem<
    T extends typeof SvelteComponentDev = typeof SvelteComponentDev
> extends DynamicSvelteComponent<T> {
    id?: string;
    hidden?: boolean;
}

export interface IterableToolbarItem<
    T extends typeof SvelteComponentDev = typeof SvelteComponentDev
> extends ToolbarItem<T> {
    items: ToolbarItem[];
}
