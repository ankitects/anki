export interface Identifiable {
    id?: string;
}

function normalize<T extends Identifiable>(
    values: T[],
    idOrIndex: string | number
): number {
    const normalizedIndex =
        typeof idOrIndex === "string"
            ? values.findIndex((value) => value.id === idOrIndex)
            : idOrIndex >= 0
            ? idOrIndex
            : values.length + idOrIndex;

    return normalizedIndex >= values.length ? -1 : normalizedIndex;
}

export function search<T extends Identifiable>(
    values: T[],
    idOrIndex: string | number
): T | null {
    const index = normalize(values, idOrIndex);
    return index >= 0 ? values[index] : null;
}

export function insert<T extends Identifiable>(
    values: T[],
    value: T,
    idOrIndex: string | number
): T[] {
    const index = normalize(values, idOrIndex);
    return index >= 0
        ? [...values.slice(0, index), value, ...values.slice(index)]
        : values;
}

export function add<T extends Identifiable>(
    values: T[],
    value: T,
    idOrIndex: string | number
): T[] {
    const index = normalize(values, idOrIndex);
    return index >= 0
        ? [...values.slice(0, index + 1), value, ...values.slice(index + 1)]
        : values;
}
