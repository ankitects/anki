export function lazyProperties(
    object: Record<string, unknown>,
    properties: Record<string, () => unknown>
): void {
    const propertyDescriptorMap = Object.entries(properties)
        .map(([name, getter]: [string, () => unknown]): [
            string,
            PropertyDescriptor
        ] => [
            name,
            {
                get: getter,
                enumerable: true,
            },
        ])
        .reduce(
            (
                accumulator: PropertyDescriptorMap,
                [name, property]
            ): PropertyDescriptorMap => ((accumulator[name] = property), accumulator),
            {}
        );

    Object.defineProperties(object, propertyDescriptorMap);
}

export function withLazyProperties(
    object: Record<string, unknown>,
    properties: Record<string, () => unknown>
): Record<string, unknown> {
    const propertyDescriptorMap = Object.entries(properties)
        .map(([name, getter]: [string, () => unknown]): [
            string,
            PropertyDescriptor
        ] => [
            name,
            {
                get: getter,
                enumerable: true,
            },
        ])
        .reduce(
            (
                accumulator: PropertyDescriptorMap,
                [name, property]
            ): PropertyDescriptorMap => ((accumulator[name] = property), accumulator),
            {}
        );

    return Object.defineProperties(object, propertyDescriptorMap);
}
