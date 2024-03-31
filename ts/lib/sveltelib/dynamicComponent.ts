// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { SvelteComponent } from "svelte";

export interface DynamicSvelteComponent<
    T extends typeof SvelteComponent<any> = typeof SvelteComponent<any>,
> {
    component: T;
    [k: string]: unknown;
}

export const dynamicComponent = <
    Comp extends typeof SvelteComponent<any>,
    DefaultProps = NonNullable<ConstructorParameters<Comp>[0]["props"]>,
>(
    component: Comp,
) =>
<Props = DefaultProps>(props: Props): DynamicSvelteComponent<Comp> & Props => {
    return { component, ...props };
};
