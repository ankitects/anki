// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export type Callback = () => void;

export function removeItem<T>(items: T[], item: T): void {
    const index = items.findIndex((i: T): boolean => i === item);

    if (index >= 0) {
        items.splice(index, 1);
    }
}
