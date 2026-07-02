// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Writable } from "svelte/store";
import { writable } from "svelte/store";

/** Automatically saves to the backend on modification. */
export type PreferenceStore<T> = Writable<T>;

/** Creates a store out of a preference getter, calling the setter when
 * changes are made. */
export async function autoSavingPrefs<T>(
    getter: () => Promise<T>,
    setter: (msg: T) => Promise<unknown>,
): Promise<PreferenceStore<T>> {
    let currentValue = await getter() as T;
    const { subscribe, set: origSet } = writable(currentValue);

    function set(value: T): void {
        currentValue = value;
        origSet(value);
        setter(value);
    }

    function update(updater: (value: T) => T): void {
        set(updater(currentValue));
    }

    return {
        subscribe,
        set,
        update,
    };
}
