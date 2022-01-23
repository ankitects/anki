// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { onMount as svelteOnMount, onDestroy as svelteOnDestroy } from "svelte";

type Callback = () => void;
type ComponentAPIMount<T> = (api: T) => Callback | void;
type ComponentAPIDestroy<T> = (api: T) => void;

interface ComponentHook<T> {
    setupComponentHook(api: T): void;
    onMount(callback: ComponentAPIMount<T>): Callback | void;
    onDestroy(callback: ComponentAPIDestroy<T>): Callback;
}

function componentHook<T>(): ComponentHook<T> {
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

            return () => {
                for (const cleanup of cleanups) {
                    cleanup();
                }
            };
        });

        svelteOnDestroy(() => {
            for (const callback of destroyCallbacks) {
                callback(api);
            }
        });
    }

    function onMount(callback: ComponentAPIMount<T>): Callback | void {
        mountCallbacks.push(callback);

        return () => {
            const index = mountCallbacks.findIndex(
                (c: ComponentAPIMount<T>): boolean => c === callback,
            );

            if (index >= 0) {
                mountCallbacks.splice(index, 1);
            }
        };
    }

    function onDestroy(callback: ComponentAPIDestroy<T>): Callback {
        destroyCallbacks.push(callback);

        return () => {
            const index = destroyCallbacks.findIndex(
                (c: ComponentAPIDestroy<T>): boolean => c === callback,
            );

            if (index >= 0) {
                mountCallbacks.splice(index, 1);
            }
        };
    }

    return {
        setupComponentHook: setup,
        onMount,
        onDestroy,
    };
}

export default componentHook;
