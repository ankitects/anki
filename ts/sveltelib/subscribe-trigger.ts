// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Readable, Writable } from "svelte/store";

import { Callback, singleCallback } from "../lib/typing";
import subscribeToUpdates from "./subscribe-updates";

/**
 * The goal of this action is to turn itself inactive.
 * Once `active` is `true`, it will unsubscribe from `store`.
 *
 * @param active: If `active` is `true`, all stores will be subscribed to.
 * @param stores: If any `store` updates to a true value, active will be set to false.
 */
function subscribeTrigger(
    active: Writable<boolean>,
    ...stores: Readable<unknown>[]
): Callback {
    function shouldUnset(): void {
        active.set(false);
    }

    let destroy: Callback | null;

    function doDestroy(): void {
        destroy?.();
        destroy = null;
    }

    active.subscribe((value: boolean): void => {
        if (value && !destroy) {
            destroy = singleCallback(
                ...stores.map((store) => subscribeToUpdates(store, shouldUnset)),
            );
        } else if (!value) {
            doDestroy();
        }
    });

    return doDestroy;
}

export default subscribeTrigger;
