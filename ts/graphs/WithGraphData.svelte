<script lang="typescript">
    import { writable } from "svelte/store";
    import useAsync from "./async";
    import useAsyncReactive from "./asyncReactive";

    import { getGraphData, RevlogRange, daysToRevlogRange } from "./graph-helpers";
    import { getPreferences } from "./preferences";

    export let initialSearch: string;
    export let initialDays: number;

    const preferencesPromise = getPreferences();

    const search = writable(initialSearch);
    const days = writable(initialDays);

    const {
        pending: graphPending,
        loading: graphLoading,
        value: graphValue,
    } = useAsyncReactive(() => getGraphData($search, $days), [
        search,
        days,
    ]);

    const {
        pending: prefsPending,
        loading: prefsLoading,
        value: prefsValue,
    } = useAsync(() => getPreferences());

    $: revlogRange = daysToRevlogRange($days);
</script>

<slot
    {search}
    {days}
    {revlogRange}
    pending={$graphPending || $prefsPending}
    loading={$graphLoading || $prefsLoading}
    sourceData={$graphValue}
    preferences={$prefsValue} />
