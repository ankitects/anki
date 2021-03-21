import { Readable, derived, get } from "svelte/store";
import useAsync, { AsyncData } from "./async";

interface AsyncRefreshData<T, E> {
    value: Readable<T | null>,
    error: Readable<E | null>,
    pending: Readable<boolean>,
    success: Readable<boolean>,
    loading: Readable<boolean>,
}


function useAsyncRefresh<T, E = unknown>(asyncFunction: () => Promise<T>, dependencies: [Readable<unknown>, ...Readable<unknown>[]]): AsyncRefreshData<T, E> {
    const current = derived(
        dependencies,
        (_, set: (value: AsyncData<T, E>) => void) => set(useAsync<T, E>(asyncFunction)),
        useAsync<T, E>(asyncFunction),
    )

    const value = derived(current, ($current, set: (value: T | null) => void) => set(get($current.value)), null)
    const error = derived(current, ($current, set: (error: E | null) => void) => set(get($current.error)), null)

    const pending = derived(current, (_, set) => set(false), true)
    const success = derived(current, ($current, set: (success: boolean) => void) => set(get($current.success)), false)
    const loading = derived(current, ($current, set: (pending: boolean) => void) => set(get($current.pending)), true)

    return { value, error, pending, success, loading }
}

export default useAsyncRefresh
