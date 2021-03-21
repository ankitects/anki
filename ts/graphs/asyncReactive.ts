import { Readable, readable, derived } from "svelte/store";

interface AsyncReativeData<T, E> {
    value: Readable<T | null>;
    error: Readable<E | null>;
    pending: Readable<boolean>;
    success: Readable<boolean>;
    loading: Readable<boolean>;
}

function useAsyncReactive<T, E>(
    asyncFunction: () => Promise<T>,
    dependencies: [Readable<unknown>, ...Readable<unknown>[]]
): AsyncReativeData<T, E> {
    const initial = asyncFunction();
    const promise = derived(dependencies, (_, set) => set(asyncFunction()), initial);

    const value = derived(
        promise,
        ($promise, set: (value: T | null) => void) => {
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

    const pending = readable(true, (set: (value: boolean) => void) => {
        initial.finally(() => set(false));
    });

    const loading = derived(
        [value, error],
        (_, set) => {
            set(false);
            return () => set(true);
        },
        true
    );

    const success = derived(
        [value],
        (_, set) => {
            set(true);
            return () => set(false);
        },
        false
    );

    return { value, error, pending, loading, success };
}

export default useAsyncReactive;
