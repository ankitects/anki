import type { SvelteComponentDev } from "svelte/internal";

export interface DynamicSvelteComponent<
    T extends typeof SvelteComponentDev = typeof SvelteComponentDev
> {
    component: T;
}

export const dynamicComponent = <T extends typeof SvelteComponentDev>(component: T) => <
    U extends NonNullable<ConstructorParameters<T>[0]["props"]>,
    V extends string = never
>(
    props: Omit<U, V>,
    lazyProps: { [Property in keyof Pick<U, V>]: () => Pick<U, V>[Property] }
): DynamicSvelteComponent<T> & U => {
    const dynamicComponent = { component, ...props };

    for (const property in lazyProps) {
        const get = lazyProps[property];
        const propertyDescriptor: TypedPropertyDescriptor<
            Pick<U, V>[Extract<keyof Pick<U, V>, string>]
        > = { get, enumerable: true };
        Object.defineProperty(dynamicComponent, property, propertyDescriptor);
    }

    return dynamicComponent as DynamicSvelteComponent<T> & U;
};
