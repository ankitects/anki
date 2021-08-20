// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { SvelteComponentDev } from "svelte/internal";

export interface DynamicSvelteComponent<
    T extends typeof SvelteComponentDev = typeof SvelteComponentDev
> {
    component: T;
    props: Record<string, unknown>;
}

export const dynamicComponent =
    <Comp extends typeof SvelteComponentDev>(component: Comp) =>
    (props: Record<string, unknown>): DynamicSvelteComponent<Comp> => {
        return { component, props };
    };
