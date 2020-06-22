<script context="module">
    import style from "./graphs.css";

    document.head.append(style);
</script>

<script lang="typescript">
    import debounce from "lodash.debounce";

    import { assertUnreachable } from "../typing";
    import pb from "../backend/proto";
    import { getGraphData, GraphRange } from "./graphs";
    import IntervalsGraph from "./IntervalsGraph.svelte";

    let data: pb.BackendProto.GraphsOut | null = null;

    let search = "deck:current";
    let range: GraphRange = GraphRange.Month;
    let days: number = 31;

    const refresh = async () => {
        data = await getGraphData(search, days);
    };

    $: {
        const rangeTmp = range as GraphRange; // ts workaround
        switch (rangeTmp) {
            case GraphRange.Month:
                days = 31;
                break;
            case GraphRange.Year:
                days = 365;
                break;
            case GraphRange.All:
                days = 0;
                break;
            default:
                assertUnreachable(rangeTmp);
        }
        console.log("refresh");
        refresh();
    }

    const scheduleRefresh = debounce(() => {
        refresh();
    }, 1000);
</script>

<div class="range-box">
    <label>
        <input type="radio" bind:group={range} value={GraphRange.Month} />
        Month
    </label>
    <label>
        <input type="radio" bind:group={range} value={GraphRange.Year} />
        Year
    </label>
    <label>
        <input type="radio" bind:group={range} value={GraphRange.All} />
        All
    </label>
</div>

<input type="text" bind:value={search} on:input={scheduleRefresh} />

<IntervalsGraph {data} />
