<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Writable } from "svelte/store";

    import pb from "anki/backend_proto";

    import useAsync from "sveltelib/async";
    import useAsyncReactive from "sveltelib/asyncReactive";
    import { getPreferences } from "sveltelib/preferences";

    import {
        getGraphData,
        getGraphPreferences,
        setGraphPreferences,
        daysToRevlogRange,
    } from "./graph-helpers";

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
    } = useAsync(() =>
        getPreferences(
            getGraphPreferences,
            setGraphPreferences,
            pb.BackendProto.GraphPreferences.toObject.bind(
                pb.BackendProto.GraphPreferences
            )
        )
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
