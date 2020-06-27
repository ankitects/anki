<script lang="typescript">
    import { gatherData, TodayData } from "./today";
    import { studiedToday } from "../time";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let todayData: TodayData | null = null;
    $: if (sourceData) {
        console.log("gathering data");
        todayData = gatherData(sourceData, i18n);
    }
</script>

{#if todayData}
    <div class="graph">
        <h1>Today</h1>

        <div>{todayData.studiedToday}</div>
        {JSON.stringify(todayData)}
    </div>
{/if}
