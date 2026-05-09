// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Writable } from "svelte/store";
import { writable } from "svelte/store";

export interface Toggleable extends Writable<boolean> {
    toggle: () => void;
    on: () => void;
    off: () => void;
}

function toggleable(defaultValue: boolean): Toggleable {
    const store = writable(defaultValue) as Toggleable;

    function toggle(): void {
        store.update((value) => !value);
    }

    store.toggle = toggle;

    function on(): void {
        store.set(true);
    }

    store.on = on;

    function off(): void {
        store.set(false);
    }

    store.off = off;

    return store;
}

export default toggleable;
