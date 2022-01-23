// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { setContext, getContext, hasContext } from "svelte";

interface ContextProperty<T> {
    setContextProperty(value: T): void;
    // this typing is a lie insofar as calling `get` outside
    // of the component's context will return undefined
    get(): T;
    has(): boolean;
}

function contextProperty<T>(key: symbol): ContextProperty<T> {
    function set(context: T): void {
        setContext(key, context);
    }

    function get(): T {
        return getContext(key);
    }

    function has(): boolean {
        return hasContext(key);
    }

    return {
        setContextProperty: set,
        get,
        has,
    };
}

export default contextProperty;
