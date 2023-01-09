// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Callback } from "@tslib/helpers";
import { removeItem } from "@tslib/helpers";
import { onDestroy as svelteOnDestroy, onMount as svelteOnMount } from "svelte";

type ComponentAPIMount<T> = (api: T) => Callback | void;
type ComponentAPIDestroy<T> = (api: T) => void;

type SetLifecycleHooksAction<T> = (api: T) => void;

export interface LifecycleHooks<T> {
    onMount(callback: ComponentAPIMount<T>): Callback;
    onDestroy(callback: ComponentAPIDestroy<T>): Callback;
}

/**
 * Makes the Svelte lifecycle hooks accessible to add-ons.
 * Currently we expose onMount and onDestroy in here, but it is fully
 * thinkable to expose the others as well, given a good use case.
 */
function lifecycleHooks<T>(): [LifecycleHooks<T>, T[], SetLifecycleHooksAction<T>] {
    const instances: T[] = [];
    const mountCallbacks: ComponentAPIMount<T>[] = [];
    const destroyCallbacks: ComponentAPIDestroy<T>[] = [];

    function setup(api: T): void {
        svelteOnMount(() => {
            const cleanups: Callback[] = [];

            for (const callback of mountCallbacks) {
                const cleanup = callback(api);

                if (cleanup) {
                    cleanups.push(cleanup);
                }
            }

            // onMount seems to be called in reverse order
            instances.unshift(api);

            return () => {
                for (const cleanup of cleanups) {
                    cleanup();
                }
            };
        });

        svelteOnDestroy(() => {
            removeItem(instances, api);

            for (const callback of destroyCallbacks) {
                callback(api);
            }
        });
    }

    function onMount(callback: ComponentAPIMount<T>): Callback {
        mountCallbacks.push(callback);
        return () => removeItem(mountCallbacks, callback);
    }

    function onDestroy(callback: ComponentAPIDestroy<T>): Callback {
        destroyCallbacks.push(callback);
        return () => removeItem(mountCallbacks, callback);
    }

    const lifecycle = {
        onMount,
        onDestroy,
    };

    return [lifecycle, instances, setup];
}

export default lifecycleHooks;
