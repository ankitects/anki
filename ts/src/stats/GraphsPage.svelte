<script context="module">
    import style from "./graphs.css";

    document.head.append(style);
</script>

<script lang="typescript">
    import { assertUnreachable } from "../typing";
    import pb from "../backend/proto";
    import { getGraphData, RevlogRange } from "./graphs";
    import IntervalsGraph from "./IntervalsGraph.svelte";
    import EaseGraph from "./EaseGraph.svelte";
    import AddedGraph from "./AddedGraph.svelte";
    import TodayStats from "./TodayStats.svelte";
    import ButtonsGraph from "./ButtonsGraph.svelte";
    import CardCounts from "./CardCounts.svelte";
    import HourGraph from "./HourGraph.svelte";
    import FutureDue from "./FutureDue.svelte";
    import ReviewsGraph from "./ReviewsGraph.svelte";

    let sourceData: pb.BackendProto.GraphsOut | null = null;

    enum SearchRange {
        Deck = 1,
        Collection = 2,
        Custom = 3,
    }

    let searchRange: SearchRange = SearchRange.Deck;
    let revlogRange: RevlogRange = RevlogRange.Month;
    let days: number = 31;
    let refreshing = false;

    let search = "deck:current";
    let displayedSearch = search;

    const refresh = async () => {
        refreshing = true;
        sourceData = await getGraphData(search, days);
        refreshing = false;
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
        switch (revlogRange as RevlogRange) {
            case RevlogRange.Month:
                days = 31;
                break;
            case RevlogRange.Year:
                days = 365;
                break;
            case RevlogRange.All:
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
    <div class="spin {refreshing ? 'active' : ''}">◐</div>

    <div class="range-box-inner">
        <label>
            <input type="radio" bind:group={searchRange} value={SearchRange.Deck} />
            Deck
        </label>
        <label>
            <input
                type="radio"
                bind:group={searchRange}
                value={SearchRange.Collection} />
            Collection
        </label>
        <label>
            <input type="radio" bind:group={searchRange} value={SearchRange.Custom} />
            Custom
        </label>

        <input type="text" bind:value={displayedSearch} on:keyup={searchKeyUp} />
    </div>

    <div class="range-box-inner">
        Review history:
        <label>
            <input type="radio" bind:group={revlogRange} value={RevlogRange.Month} />
            Month
        </label>
        <label>
            <input type="radio" bind:group={revlogRange} value={RevlogRange.Year} />
            Year
        </label>
        <label>
            <input type="radio" bind:group={revlogRange} value={RevlogRange.All} />
            All
        </label>
    </div>
</div>
<div class="range-box-pad" />

<TodayStats {sourceData} />
<CardCounts {sourceData} />
<FutureDue {sourceData} />
<ReviewsGraph {sourceData} {revlogRange} />
<IntervalsGraph {sourceData} />
<EaseGraph {sourceData} />
<ButtonsGraph {sourceData} />
<HourGraph {sourceData} />
<AddedGraph {sourceData} />
