// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getContext, hasContext, setContext } from "svelte";

type SetContextPropertyAction<T> = (value: T) => void;

export interface ContextProperty<T> {
    /**
     * Retrieves the component's context
     *
     * @remarks
     * The typing of the return value is a lie insofar as calling `get` outside
     * of the component's context will return `undefined`.
     * If you are uncertain if your component is actually within the context
     * of this component, you should check with `available` first.
     *
     * @returns The component's context
     */
    get(): T;
    /**
     * Checks whether the component's context is available
     */
    available(): boolean;
}

function contextProperty<T>(
    key: symbol,
): [ContextProperty<T>, SetContextPropertyAction<T>] {
    function set(context: T): void {
        setContext(key, context);
    }

    const context = {
        get(): T {
            return getContext(key);
        },
        available(): boolean {
            return hasContext(key);
        },
    };

    return [context, set];
}

export default contextProperty;
