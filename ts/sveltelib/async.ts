import { Readable, readable, derived } from "svelte/store";

interface AsyncData<T, E> {
    value: Readable<T | null>;
    error: Readable<E | null>;
    loading: Readable<boolean>;
    success: Readable<boolean>;
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

    const success = derived([value], (_, set) => set(true), false);

    return { value, error, loading, success };
}

export default useAsync;
