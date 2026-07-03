<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import {
        getGraphPreferences,
        graphs,
        setGraphPreferences,
    } from "@generated/backend";
    import type { Writable } from "svelte/store";

    import { autoSavingPrefs } from "$lib/sveltelib/preferences";

    import { daysToRevlogRange } from "./graph-helpers";

    export let search: Writable<string>;
    export let days: Writable<number>;

    const prefsPromise = autoSavingPrefs(
        () => getGraphPreferences({}),
        setGraphPreferences,
    );

    let sourceData: GraphsResponse | null = null;
    let loading = true;
    $: updateSourceData($search, $days);

    async function updateSourceData(search: string, days: number): Promise<void> {
        // ensure the fast-loading preferences come first
        await prefsPromise;
        loading = true;
        try {
            sourceData = await graphs({ search, days });
        } finally {
            loading = false;
        }
    }

    $: revlogRange = daysToRevlogRange($days);
</script>

<!--
We block graphs loading until the preferences have been fetched, so graphs
don't have to worry about a null initial value. We don't do the same for the
graph data, as it gets updated as the user changes options, and we don't want
the current graphs to disappear until the new graphs have finished loading.
-->
{#await prefsPromise then prefs}
    <slot {revlogRange} {prefs} {sourceData} {loading} />
{/await}
