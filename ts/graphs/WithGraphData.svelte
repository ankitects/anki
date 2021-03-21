<script lang="typescript">
    import { onMount } from "svelte";
    import { writable } from "svelte/store";
    import useAsync from "./async";
    import useAsyncReactive from "./asyncReactive";

    import { getGraphData, RevlogRange, daysToRevlogRange } from "./graph-helpers";
    import { getPreferences } from "./preferences";

    export let initialSearch: string;
    export let initialDays: number;

    const search = writable(initialSearch);
    const days = writable(initialDays);

    const {
        loading: graphLoading,
        error: graphError,
        value: graphValue,
    } = useAsyncReactive(() => getGraphData($search, $days), [
        search,
        days,
    ]);

    const preferencesPromise = getPreferences();
    const {
        loading: prefsLoading,
        error: prefsError,
        value: prefsValue,
    } = useAsync(() => preferencesPromise);

    $: revlogRange = daysToRevlogRange($days);

    $: {
        if ($graphError) {
            alert($graphError)
        }
    }

    $: {
        if ($prefsError) {
            alert($prefsError)
        }
    }
</script>

<slot
    {search}
    {days}
    {revlogRange}
    loading={$graphLoading || $prefsLoading}
    sourceData={$graphValue}
    preferences={$prefsValue} />
