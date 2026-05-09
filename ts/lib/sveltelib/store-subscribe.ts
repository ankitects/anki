// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Readable, Unsubscriber } from "svelte/store";

interface StoreAccessors {
    subscribe: () => void;
    unsubscribe: () => void;
}

/**
 * Helper function to prevent double (un)subscriptions
 */
function storeSubscribe<T>(
    store: Readable<T>,
    callback: (value: T) => void,
    start = true,
): StoreAccessors {
    function subscribe(): Unsubscriber {
        return store.subscribe(callback);
    }

    let unsubscribe: Unsubscriber | null = start ? subscribe() : null;

    function resubscribe(): void {
        if (!unsubscribe) {
            unsubscribe = subscribe();
        }
    }

    function doUnsubscribe() {
        unsubscribe?.();
        unsubscribe = null;
    }

    return {
        subscribe: resubscribe,
        unsubscribe: doUnsubscribe,
    };
}

export default storeSubscribe;
