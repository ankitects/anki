<script lang="typescript">
    import useAsync from "sveltelib/async";
    import useAsyncReactive from "sveltelib/asyncReactive";

    import { getGraphData, RevlogRange, daysToRevlogRange } from "./graph-helpers";
    import { getPreferences } from "./preferences";

    export let search: Writable<string>;
    export let days: Writable<number>;

    const {
        loading: graphLoading,
        error: graphError,
        value: graphValue,
    } = useAsyncReactive(() => getGraphData($search, $days), [search, days]);

    const {
        loading: prefsLoading,
        error: prefsError,
        value: prefsValue,
    } = useAsync(() => getPreferences());

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
