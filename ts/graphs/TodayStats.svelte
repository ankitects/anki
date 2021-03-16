<script lang="typescript">
    import type pb from "anki/backend_proto";
    import type { I18n } from "anki/i18n";

    import Graph from "./Graph.svelte";

    import type { TodayData } from "./today";
    import { gatherData } from "./today";
    import { graph } from "./graph-styles";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let todayData: TodayData | null = null;
    $: if (sourceData) {
        todayData = gatherData(sourceData, i18n);
    }
</script>

<style lang="scss">
    .legend {
        text-align: center;
    }
</style>

{#if todayData}
    <Graph title={todayData.title}>
        <div class="legend">
            {#each todayData.lines as line}
                <div>{line}</div>
            {/each}
        </div>
    </Graph>
{/if}
