// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Readable, Subscriber, Unsubscriber } from "svelte/store";

/**
 * In some cases, we only care for updates, and not the initial
 * value of a store, e.g. when the store wraps events.
 * This also means, we can not use the special store syntax.
 */
function subscribeToUpdates<T>(
    store: Readable<T>,
    subscription: Subscriber<T>,
): Unsubscriber {
    let first = true;

    return store.subscribe((value: T): void => {
        if (first) {
            first = false;
        } else {
            subscription(value);
        }
    });
}

export default subscribeToUpdates;
