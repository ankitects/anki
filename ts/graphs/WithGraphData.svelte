<script lang="typescript">
    import useAsync from "./async";
    import useAsyncReactive from "./asyncReactive";

    import { getGraphData, RevlogRange, daysToRevlogRange } from "./graph-helpers";
    import { getPreferences } from "./preferences";

    export let search: Writable<string>;
    export let days: Writable<number>;

    const {
        loading: graphLoading,
        error: graphError,
        value: graphValue,
    } = useAsyncReactive(() => getGraphData($search, $days), [search, days]);

    const preferencesPromise = getPreferences();
    const { loading: prefsLoading, error: prefsError, value: prefsValue } = useAsync(
        () => preferencesPromise
    );

    $: revlogRange = daysToRevlogRange($days);

    $: {
        if ($graphError) {
            alert($graphError);
        }
    }

    $: {
        if ($prefsError) {
            alert($prefsError);
        }
    }
</script>

<slot
    {revlogRange}
    loading={$graphLoading || $prefsLoading}
    sourceData={$graphValue}
    preferences={$prefsValue} />
