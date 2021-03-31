export function lazyLoaded(
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
