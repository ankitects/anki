<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Writable } from "svelte/store";
    import type { PreferenceRaw, PreferencePayload } from "sveltelib/preferences";

    import { Backend } from "lib/proto";
    import { postRequest } from "lib/postrequest";

    import useAsync from "sveltelib/async";
    import useAsyncReactive from "sveltelib/asyncReactive";
    import { getPreferences } from "sveltelib/preferences";

    import { daysToRevlogRange } from "./graph-helpers";

    export let search: Writable<string>;
    export let days: Writable<number>;

    async function getGraphData(
        search: string,
        days: number
    ): Promise<Backend.GraphsResponse> {
        return Backend.GraphsResponse.decode(
            await postRequest("/_anki/graphData", JSON.stringify({ search, days }))
        );
    }

    async function getGraphPreferences(): Promise<Backend.GraphPreferences> {
        return Backend.GraphPreferences.decode(
            await postRequest("/_anki/graphPreferences", JSON.stringify({}))
        );
    }

    async function setGraphPreferences(
        prefs: PreferencePayload<Backend.GraphPreferences>
    ): Promise<void> {
        await postRequest(
            "/_anki/setGraphPreferences",
            Backend.GraphPreferences.encode(prefs).finish()
        );
    }

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
            Backend.GraphPreferences.toObject.bind(Backend.GraphPreferences) as (
                preferences: Backend.GraphPreferences,
                options: { defaults: boolean }
            ) => PreferenceRaw<Backend.GraphPreferences>
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
    preferences={$prefsValue}
/>
