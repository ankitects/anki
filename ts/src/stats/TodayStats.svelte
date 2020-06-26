<script lang="typescript">
    import { gatherData, TodayData } from "./today";
    import pb from "../backend/proto";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;

    let todayData: TodayData | null = null;
    $: if (sourceData) {
        console.log("gathering data");
        todayData = gatherData(sourceData);
    }
</script>

<div class="graph">
    <h1>Today</h1>
    {JSON.stringify(todayData)}
</div>
