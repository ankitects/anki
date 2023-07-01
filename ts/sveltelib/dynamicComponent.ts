// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { SvelteComponent } from "svelte";

export interface DynamicSvelteComponent<
    T extends typeof SvelteComponent = typeof SvelteComponent<any>,
> {
    component: T;
    [k: string]: unknown;
}

export const dynamicComponent = <
    Comp extends typeof SvelteComponent,
    DefaultProps = NonNullable<ConstructorParameters<Comp>[0]["props"]>,
>(
    component: Comp,
) =>
<Props = DefaultProps>(props: Props): DynamicSvelteComponent<Comp> & Props => {
    return { component, ...props };
};
