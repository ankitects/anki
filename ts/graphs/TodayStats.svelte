<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Stats } from "lib/proto";

    import Graph from "./Graph.svelte";

    import type { TodayData } from "./today";
    import { gatherData } from "./today";

    export let sourceData: Stats.GraphsResponse | null = null;

    let todayData: TodayData | null = null;
    $: if (sourceData) {
        todayData = gatherData(sourceData);
    }
</script>

{#if todayData}
    <Graph title={todayData.title}>
        <div class="legend">
            {#each todayData.lines as line}
                <div>{line}</div>
            {/each}
        </div>
    </Graph>
{/if}

<style lang="scss">
    .legend {
        text-align: center;
    }
</style>
