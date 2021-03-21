import { Readable, readable, derived } from "svelte/store";

interface AsyncData<T, E> {
    value: Readable<T | null>,
    error: Readable<E | null>,
    pending: Readable<boolean>,
    successful: Readable<boolean>,
}


function useAsync<T, E = Error>(asyncFunction: () => Promise<T>): AsyncData<T, E> {
    const promise = asyncFunction();

    const value = readable(null, (set: (value: T) => void) => {
        promise.then((value: T) => set(value))
    })

    const error = readable(null, (set: (value: E) => void) => {
        promise.catch((value: E) => set(value))
    })

    const pending = derived([value, error], (_, set) => set(true), true)
    const successful = derived([value], (_, set) => set(true), false)

    return { value, error, pending, successful }
}

export default useAsync
