<script lang="typescript">
    import { gatherData, TodayData } from "./today";
    import { studiedToday } from "../time";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";

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
