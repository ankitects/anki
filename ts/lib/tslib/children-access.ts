// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export type Identifier = Element | string | number;

function findElement<T extends Element>(
    collection: HTMLCollection,
    identifier: Identifier,
): [T, number] | null {
    let element: T;
    let index: number;

    if (identifier instanceof Element) {
        element = identifier as T;
        index = Array.prototype.indexOf.call(collection, element);
        if (index < 0) {
            return null;
        }
    } else if (typeof identifier === "string") {
        const item = collection.namedItem(identifier);
        if (!item) {
            return null;
        }
        element = item as T;
        index = Array.prototype.indexOf.call(collection, element);
        if (index < 0) {
            return null;
        }
    } else if (identifier < 0) {
        index = collection.length + identifier;
        const item = collection.item(index);
        if (!item) {
            return null;
        }
        element = item as T;
    } else {
        index = identifier;
        const item = collection.item(index);
        if (!item) {
            return null;
        }
        element = item as T;
    }

    return [element, index];
}

/**
 * Creates a convenient access API for the children
 * of an element via identifiers. Identifiers can be:
 * - integers: signify the position
 * - negative integers: signify the offset from the end (-1 being the last element)
 * - strings: signify the id of an element
 * - the child directly
 */
class ChildrenAccess<T extends Element> {
    parent: T;

    constructor(parent: T) {
        this.parent = parent;
    }

    insertElement(element: Element, identifier: Identifier): number {
        const match = findElement(this.parent.children, identifier);

        if (!match) {
            return -1;
        }

        const [reference, index] = match;
        this.parent.insertBefore(element, reference[0]);

        return index;
    }

    appendElement(element: Element, identifier: Identifier): number {
        const match = findElement(this.parent.children, identifier);

        if (!match) {
            return -1;
        }

        const [before, index] = match;
        const reference = before.nextElementSibling ?? null;
        this.parent.insertBefore(element, reference);

        return index + 1;
    }

    updateElement(
        f: (element: T, index: number) => void,
        identifier: Identifier,
    ): boolean {
        const match = findElement<T>(this.parent.children, identifier);

        if (!match) {
            return false;
        }

        f(...match);
        return true;
    }
}

function childrenAccess<T extends Element>(parent: T): ChildrenAccess<T> {
    return new ChildrenAccess<T>(parent);
}

export default childrenAccess;
export type { ChildrenAccess };
