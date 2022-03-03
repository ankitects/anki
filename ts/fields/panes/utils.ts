// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface PaneInput<T> {
    id: string;
    data: T;
}

export function filterInPlace<T>(components: (T | null | undefined)[]): void {
    const empty: number[] = [];

    for (let i = 0; i < components.length; i++) {
        if (!components[i]) {
            empty.push(i);
        }
    }

    for (const i of empty.reverse()) {
        components.splice(i, 1);
    }
}
