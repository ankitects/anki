// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { setContext, getContext, hasContext } from "svelte";

type ContextProperty<T> = [
    (value: T) => T,
    // this typing is a lie insofar that calling get
    // outside of the component's context will return undefined
    () => T,
    () => boolean,
];

function contextProperty<T>(key: symbol): ContextProperty<T> {
    function set(context: T): T {
        setContext(key, context);
        return context;
    }

    function get(): T {
        return getContext(key);
    }

    function has(): boolean {
        return hasContext(key);
    }

    return [set, get, has];
}

export default contextProperty;
