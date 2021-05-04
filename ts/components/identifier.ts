// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export type Identifier = string | number;

function find(collection: HTMLCollection, idOrIndex: Identifier): Element | null {
    let element: Element | null = null;

    if (typeof idOrIndex === "string") {
        element = collection.namedItem(idOrIndex);
    } else if (idOrIndex < 0) {
        const normalizedIndex = collection.length + idOrIndex;
        element = collection.item(normalizedIndex);
    } else {
        element = collection.item(idOrIndex);
    }

    return element;
}

export function insert(
    element: Element,
    collection: Element,
    idOrIndex: Identifier
): void {
    const reference = find(collection.children, idOrIndex);

    if (reference) {
        collection.insertBefore(element, reference);
    }
}

export function add(
    element: Element,
    collection: Element,
    idOrIndex: Identifier
): void {
    const before = find(collection.children, idOrIndex);

    if (before) {
        const reference = before.nextElementSibling ?? null;
        collection.insertBefore(element, reference);
    }
}

export function update(
    f: (element: Element) => void,
    collection: Element,
    idOrIndex: Identifier
): void {
    const element = find(collection.children, idOrIndex);

    if (element) {
        f(element);
    }
}
