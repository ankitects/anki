// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { removeItem } from "./helpers";
import type { Callback } from "./typing";

type HookCallback<T> = (input: T) => void | Promise<void>;

export interface HooksAPI<T> {
    hook(callback: HookCallback<T>): Callback;
}

type RunHooks<T> = (input: T) => Promise<void>;

export function hooks<T>(): [HooksAPI<T>, RunHooks<T>] {
    const hooksList: HookCallback<T>[] = [];

    function hook(callback: HookCallback<T>): Callback {
        hooksList.push(callback);
        return () => removeItem(hooksList, callback);
    }

    async function run(input: T): Promise<void> {
        const promises: (void | Promise<void>)[] = [];

        for (const hook of hooksList) {
            try {
                const result = hook(input);
                promises.push(result);
            } catch (error) {
                console.log("Hook failed: ", error);
            }
        }

        await Promise.allSettled(promises);
    }

    const hooksApi = {
        hook,
    };

    return [hooksApi, run];
}
