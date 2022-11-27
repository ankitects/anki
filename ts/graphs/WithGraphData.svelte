<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { empty, Stats, stats } from "@tslib/proto";
    import type { Writable } from "svelte/store";

    import useAsync from "../sveltelib/async";
    import useAsyncReactive from "../sveltelib/asyncReactive";
    import type { PreferenceRaw } from "../sveltelib/preferences";
    import { getPreferences } from "../sveltelib/preferences";
    import { daysToRevlogRange } from "./graph-helpers";

    export let search: Writable<string>;
    export let days: Writable<number>;

    const {
        loading: graphLoading,
        error: graphError,
        value: graphValue,
    } = useAsyncReactive(
        () =>
            stats.graphs(Stats.GraphsRequest.create({ search: $search, days: $days })),
        [search, days],
    );

    const {
        loading: prefsLoading,
        error: prefsError,
        value: prefsValue,
    } = useAsync(() =>
        getPreferences(
            () => stats.getGraphPreferences(empty),
            async (input: Stats.IGraphPreferences): Promise<void> => {
                stats.setGraphPreferences(Stats.GraphPreferences.create(input));
            },
            Stats.GraphPreferences.toObject.bind(Stats.GraphPreferences) as (
                preferences: Stats.GraphPreferences,
                options: { defaults: boolean },
            ) => PreferenceRaw<Stats.GraphPreferences>,
        ),
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
    preferences={$prefsValue}
/>
