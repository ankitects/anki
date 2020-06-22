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

    enum SearchRange {
        Deck = 1,
        Collection = 2,
        Custom = 3,
    }

    let searchRange: SearchRange = SearchRange.Deck;
    let range: GraphRange = GraphRange.Month;
    let days: number = 31;

    let search = "deck:current";

    const refresh = async () => {
        console.log(`search is ${search}`);
        data = await getGraphData(search, days);
    };

    $: {
        switch (searchRange as SearchRange) {
            case SearchRange.Deck:
                search = "deck:current";
                refresh();
                break;
            case SearchRange.Collection:
                search = "";
                refresh();
                break;
            case SearchRange.Custom:
                break;
        }
    }

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
        <input type="radio" bind:group={searchRange} value={SearchRange.Deck} />
        Deck
    </label>
    <label>
        <input type="radio" bind:group={searchRange} value={SearchRange.Collection} />
        Collection
    </label>
    <label>
        <input type="radio" bind:group={searchRange} value={SearchRange.Custom} />
        Custom
    </label>

    <input type="text" bind:value={search} on:input={scheduleRefresh} />

</div>

<div class="range-box">
    Review history:
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

<IntervalsGraph {data} />
