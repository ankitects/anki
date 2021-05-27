// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export type Identifier = string | number;

export function findElement(
    collection: HTMLCollection,
    idOrIndex: Identifier
): [number, Element] | null {
    let result: [number, Element] | null = null;

    if (typeof idOrIndex === "string") {
        const element = collection.namedItem(idOrIndex);

        if (element) {
            const index = Array.prototype.indexOf.call(collection, element);
            result = [index, element];
        }
    } else if (idOrIndex < 0) {
        const index = collection.length + idOrIndex;
        const element = collection.item(index);

        if (element) {
            result = [index, element];
        }
    } else {
        const index = idOrIndex;
        const element = collection.item(index);

        if (element) {
            result = [index, element];
        }
    }

    return result;
}

export function insertElement(
    element: Element,
    collection: Element,
    idOrIndex: Identifier
): number {
    const match = findElement(collection.children, idOrIndex);

    if (match) {
        const [index, reference] = match;
        collection.insertBefore(element, reference[0]);

        return index;
    }

    return -1;
}

export function appendElement(
    element: Element,
    collection: Element,
    idOrIndex: Identifier
): number {
    const match = findElement(collection.children, idOrIndex);

    if (match) {
        const [index, before] = match;
        const reference = before.nextElementSibling ?? null;
        collection.insertBefore(element, reference);

        return index + 1;
    }

    return -1;
}

export function updateElement(
    f: (element: Element) => void,
    collection: Element,
    idOrIndex: Identifier
): number {
    const match = findElement(collection.children, idOrIndex);

    if (match) {
        const [index, element] = match;
        f(element[0]);

        return index;
    }

    return -1;
}
