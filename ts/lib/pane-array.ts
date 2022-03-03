// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { randomUUID } from "./uuid";

interface PaneType<T> {
    id: string;
    data: T | PaneType<T>[];
}

function find<T>(array: PaneType<T>[], searchId: string): T | null {
    for (const { id, data } of array) {
        if (Array.isArray(data)) {
            const found = find(data, searchId);

            if (found) {
                return found;
            }
        } else if (id === searchId) {
            return data;
        }
    }

    return null;
}

/**
 * @returns Whether it was replaced.
 */
function replace<T>(array: PaneType<T>[], searchId: string, replaced: T): boolean {
    for (const [index, { id, data }] of array.entries()) {
        if (Array.isArray(data) && replace(data, searchId, replaced)) {
            return true;
        } else if (id === searchId) {
            array[index].data = replaced;
            return true;
        }
    }

    return false;
}

/**
 * @param correctLevel: Whether the current level is in horizontal orientation.
 * Otherwise it has a vertical orientation.
 * @returns Whether the pane was added.
 */
function splitDirectional<T>(
    array: PaneType<T>[],
    searchId: string,
    splitPane: PaneType<T>,
    inlineDirection: boolean,
): boolean {
    for (const [index, { id, data }] of array.entries()) {
        if (
            Array.isArray(data) &&
            splitDirectional(data, searchId, splitPane, !inlineDirection)
        ) {
            return true;
        } else if (id === searchId) {
            if (inlineDirection) {
                array.splice(index + 1, 0, splitPane);
            } else {
                array.splice(index, 1, {
                    id: randomUUID(),
                    data: [array[index], splitPane],
                });
            }
            return true;
        }
    }

    return false;
}

export class PaneArray<T> extends Array<PaneType<T>> {
    constructor(private horizontal: boolean = false) {
        super();
    }

    findData(id: string): T | null {
        return find(this, id);
    }

    replace(id: string, data: T): boolean {
        return replace(this, id, data);
    }

    splitHorizontal(id: string, pane: PaneType<T>): boolean {
        return splitDirectional(this, id, pane, this.horizontal);
    }

    splitVertical(id: string, pane: PaneType<T>): boolean {
        return splitDirectional(this, id, pane, !this.horizontal);
    }

    makeOnly(pane: PaneType<T>): void {
        this.splice(0, Infinity, pane);
    }
}
