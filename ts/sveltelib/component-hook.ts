// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { onMount as svelteOnMount, onDestroy as svelteOnDestroy } from "svelte";

type Callback = () => void;
type ComponentAPIMount<T> = (api: T) => Callback | void;
type ComponentAPIDestroy<T> = (api: T) => void;

type SetComponentHookAction<T> = (api: T) => void;

export interface ComponentHook<T> {
    instances: T[];
    onMount(callback: ComponentAPIMount<T>): Callback | void;
    onDestroy(callback: ComponentAPIDestroy<T>): Callback;
}

function removeItem<T>(items: T[], item: T) {
    const index = items.findIndex((i: T): boolean => i === item);

    if (index >= 0) {
        items.splice(index, 1);
    }
}

/**
 * Makes the Svelte lifecycle hooks accessible to add-ons.
 * Currently we expose onMount and onDestroy in here, but it is fully
 * thinkable to expose the others as well, given a good use case.
 */
function componentHook<T>(): [ComponentHook<T>, SetComponentHookAction<T>] {
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

            instances.push(api);

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

    function onMount(callback: ComponentAPIMount<T>): Callback | void {
        mountCallbacks.push(callback);
        return () => removeItem(mountCallbacks, callback);
    }

    function onDestroy(callback: ComponentAPIDestroy<T>): Callback {
        destroyCallbacks.push(callback);
        return () => removeItem(mountCallbacks, callback);
    }

    const componentHook = {
        onMount,
        onDestroy,
        instances,
    };

    return [componentHook, setup];
}

export default componentHook;
