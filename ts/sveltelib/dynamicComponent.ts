import type { SvelteComponentDev } from "svelte/internal";

export interface DynamicSvelteComponent<
    T extends typeof SvelteComponentDev = typeof SvelteComponentDev
> {
    component: T;
    [k: string]: unknown;
}

export const dynamicComponent = <
    Comp extends typeof SvelteComponentDev,
    DefaultProps = NonNullable<ConstructorParameters<Comp>[0]["props"]>
>(
    component: Comp
) => <Props = DefaultProps>(props: Props): DynamicSvelteComponent<Comp> & Props => {
    return { component, ...props };
};
