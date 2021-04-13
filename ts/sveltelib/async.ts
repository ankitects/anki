// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Readable, readable } from "svelte/store";

interface AsyncData<T, E> {
    value: Readable<T | null>;
    error: Readable<E | null>;
    loading: Readable<boolean>;
}

function useAsync<T, E = unknown>(asyncFunction: () => Promise<T>): AsyncData<T, E> {
    const promise = asyncFunction();

    const value = readable(null, (set: (value: T) => void) => {
        promise.then((value: T) => set(value));
    });

    const error = readable(null, (set: (value: E) => void) => {
        promise.catch((value: E) => set(value));
    });

    const loading = readable(true, (set: (value: boolean) => void) => {
        promise.finally(() => set(false));
    });

    return { value, error, loading };
}

export default useAsync;
