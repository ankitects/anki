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

    const sourceData = useAsyncReactive(() => getGraphData($search, $days), [
        search,
        days,
    ]);
    const preferences = useAsync(() => getPreferences());

    $: revlogRange = daysToRevlogRange($days);
</script>

<slot
    {search}
    {days}
    {revlogRange}
    pending={$sourceData.pending || $preferences.pending}
    loading={$sourceData.loading || $preferences.loading}
    sourceData={$sourceData.value}
    preferences={$preferences.value} />
