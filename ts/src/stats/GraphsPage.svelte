<script context="module">
    import style from "./graphs.css";

    document.head.append(style);
</script>

<script lang="typescript">
    import { assertUnreachable } from "../typing";
    import pb from "../backend/proto";
    import { getGraphData, GraphRange } from "./graphs";
    import IntervalsGraph from "./IntervalsGraph.svelte";
    import EaseGraph from "./EaseGraph.svelte";
    import AddedGraph from "./AddedGraph.svelte";
    import TodayStats from "./TodayStats.svelte";
    import ButtonsGraph from "./ButtonsGraph.svelte";
    import CardCounts from "./CardCounts.svelte";

    let sourceData: pb.BackendProto.GraphsOut | null = null;

    enum SearchRange {
        Deck = 1,
        Collection = 2,
        Custom = 3,
    }

    let searchRange: SearchRange = SearchRange.Deck;
    let range: GraphRange = GraphRange.Month;
    let days: number = 31;

    let search = "deck:current";
    let displayedSearch = search;

    const refresh = async () => {
        console.log(`search is ${search}`);
        sourceData = await getGraphData(search, days);
    };

    $: {
        // refresh if either of these change
        search;
        days;
        refresh();
    }

    $: {
        switch (searchRange as SearchRange) {
            case SearchRange.Deck:
                search = displayedSearch = "deck:current";
                break;
            case SearchRange.Collection:
                search = displayedSearch = "";
                break;
            case SearchRange.Custom:
                break;
        }
    }

    $: {
        switch (range as GraphRange) {
            case GraphRange.Month:
                days = 31;
                break;
            case GraphRange.Year:
                days = 365;
                break;
            case GraphRange.All:
                days = 0;
                break;
        }
    }

    const searchKeyUp = (e: KeyboardEvent) => {
        // fetch data on enter
        if (e.keyCode == 13) {
            search = displayedSearch;
        }
    };
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

    <input type="text" bind:value={displayedSearch} on:keyup={searchKeyUp} />

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

<TodayStats {sourceData} />
<CardCounts {sourceData} />
<AddedGraph {sourceData} />
<IntervalsGraph {sourceData} />
<EaseGraph {sourceData} />
<ButtonsGraph {sourceData} />
