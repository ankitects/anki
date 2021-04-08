import type { SvelteComponentDev } from "svelte/internal";

export interface DynamicSvelteComponent<
    T extends typeof SvelteComponentDev = typeof SvelteComponentDev
> {
    component: T;
}

export const dynamicComponent = <Comp extends typeof SvelteComponentDev>(component: Comp) => <
    Props extends NonNullable<ConstructorParameters<Comp>[0]["props"]>,
    Lazy extends string = never
>(
    props: Omit<Props, Lazy>,
    lazyProps: { [Property in keyof Pick<Props, Lazy>]: () => Pick<Props, Lazy>[Property] }
): DynamicSvelteComponent<Comp> & Props => {
    const dynamicComponent = { component, ...props };

    for (const property in lazyProps) {
        const get = lazyProps[property];
        const propertyDescriptor: TypedPropertyDescriptor<
            Pick<Props, Lazy>[Extract<keyof Pick<Props, Lazy>, string>]
        > = { get, enumerable: true };
        Object.defineProperty(dynamicComponent, property, propertyDescriptor);
    }

    return dynamicComponent as DynamicSvelteComponent<Comp> & Props;
};
