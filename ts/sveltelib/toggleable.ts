// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Writable } from "svelte/store";

export interface Toggleable {
    toggle: () => void;
    on: () => void;
    off: () => void;
}

function toggleable(store: Writable<boolean>): Toggleable {
    function toggle(): void {
        store.update((value) => !value);
    }

    function on(): void {
        store.set(true);
    }

    function off(): void {
        store.set(false);
    }

    return { toggle, on, off };
}

export default toggleable;
