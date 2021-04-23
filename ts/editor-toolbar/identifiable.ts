// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export interface Identifiable {
    id?: string;
}

interface IterableIdentifiable<T extends Identifiable> extends Identifiable {
    items: T[];
}

export type Identifier = string | number;

function normalize<T extends Identifiable>(
    iterable: IterableIdentifiable<T>,
    idOrIndex: Identifier
): number {
    let normalizedIndex: number;

    if (typeof idOrIndex === "string") {
        normalizedIndex = iterable.items.findIndex((value) => value.id === idOrIndex);
    } else if (idOrIndex < 0) {
        normalizedIndex = iterable.items.length + idOrIndex;
    } else {
        normalizedIndex = idOrIndex;
    }

    return normalizedIndex >= iterable.items.length ? -1 : normalizedIndex;
}

function search<T extends Identifiable>(values: T[], index: number): T | null {
    return index >= 0 ? values[index] : null;
}

export function insert<T extends Identifiable>(
    iterable: IterableIdentifiable<T> & T,
    value: T,
    idOrIndex: Identifier
): IterableIdentifiable<T> & T {
    const index = normalize(iterable, idOrIndex);

    if (index >= 0) {
        iterable.items = [
            ...iterable.items.slice(0, index),
            value,
            ...iterable.items.slice(index),
        ];
    }

    return iterable;
}

export function add<T extends Identifiable>(
    iterable: IterableIdentifiable<T> & T,
    value: T,
    idOrIndex: Identifier
): IterableIdentifiable<T> & T {
    const index = normalize(iterable, idOrIndex);

    if (index >= 0) {
        iterable.items = [
            ...iterable.items.slice(0, index + 1),
            value,
            ...iterable.items.slice(index + 1),
        ];
    }

    return iterable;
}

function isRecursive<T>(component: Identifiable): component is IterableIdentifiable<T> {
    return Boolean(Object.prototype.hasOwnProperty.call(component, "items"));
}

export function updateRecursive<T extends Identifiable>(
    update: (component: T) => T,
    component: T,
    ...identifiers: Identifier[]
): T {
    if (identifiers.length === 0) {
        return update(component);
    } else if (isRecursive<T>(component)) {
        const [identifier, ...restIdentifiers] = identifiers;
        const normalizedIndex = normalize(component, identifier);
        const foundComponent = search(component.items, normalizedIndex);

        if (foundComponent) {
            component.items[normalizedIndex] = updateRecursive(
                update,
                foundComponent as T,
                ...restIdentifiers
            );
        }

        return component;
    }

    return component;
}
