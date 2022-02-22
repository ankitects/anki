// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Writable } from "svelte/store";
import { writable } from "svelte/store";

export interface Trigger<T> {
    add(value: T): void;
    remove(): void;
    active: Writable<boolean>;
}

export type Managed<T> = Pick<Trigger<T>, "remove"> & { value: T };

function trigger<T>(list: Managed<T>[]) {
    return function getTrigger(): Trigger<T> {
        const index = list.length++;
        const active = writable(false);

        function remove() {
            delete list[index];
            active.set(false);
        }

        function add(value: T): void {
            list[index] = { value, remove };
            active.set(true);
        }

        return {
            add,
            remove,
            active,
        };
    };
}

export default trigger;
