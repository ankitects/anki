import { Readable, derived } from "svelte/store";

interface AsyncReativeData<T, E> {
    value: Readable<T | null>;
    error: Readable<E | null>;
    loading: Readable<boolean>;
    success: Readable<boolean>;
}

function useAsyncReactive<T, E>(
    asyncFunction: () => Promise<T>,
    dependencies: [Readable<unknown>, ...Readable<unknown>[]]
): AsyncReativeData<T, E> {
    const promise = derived(dependencies, (_, set) => set(asyncFunction()), asyncFunction());

    const value = derived(
        promise,
        ($promise, set: (value: T) => void) => {
            $promise.then((value: T) => set(value));
        },
        null
    );

    const error = derived(
        promise,
        ($promise, set: (error: E | null) => void) => {
            $promise.catch((error: E) => set(error));
            return () => set(null);
        },
        null
    );

    const loading = derived(
        [value, error],
        (_, set: (value: boolean) => void) => {
            set(false);
            return () => set(true);
        },
        true
    );

    const success = derived(
        [value],
        (_, set: (value: boolean) => void) => {
            set(true);
            return () => set(false);
        },
        false
    );

    return { value, error, loading, success };
}

export default useAsyncReactive;
