<script lang="typescript">
    import { gatherData } from "./today";
    import type { TodayData } from "./today";
    import type pb from "anki/backend_proto";
    import type { I18n } from "anki/i18n";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let todayData: TodayData | null = null;
    $: if (sourceData) {
        todayData = gatherData(sourceData, i18n);
    }
</script>

{#if todayData}
    <div class="graph" id="graph-today-stats">
        <h1>{todayData.title}</h1>

        <div class="legend-outer">
            {#each todayData.lines as line}
                <div>{line}</div>
            {/each}
        </div>
    </div>
{/if}
