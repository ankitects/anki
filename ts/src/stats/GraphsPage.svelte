<script context="module">
    import style from "./graphs.css";
</script>

<script lang="typescript">
    import { timeSpan, MONTH, YEAR } from "../time";
    import { I18n } from "../i18n";
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

    export let i18n: I18n;

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

    const month = timeSpan(i18n, 1 * MONTH);
    const year = timeSpan(i18n, 1 * YEAR);
    const deck = i18n.tr(i18n.TR.STATISTICS_RANGE_DECK);
    const collection = i18n.tr(i18n.TR.STATISTICS_RANGE_COLLECTION);
    const searchLabel = i18n.tr(i18n.TR.STATISTICS_RANGE_SEARCH);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_TIME);
</script>

<div class="range-box">
    <div class="spin {refreshing ? 'active' : ''}">◐</div>

    <div class="range-box-inner">
        <label>
            <input type="radio" bind:group={searchRange} value={SearchRange.Deck} />
            {deck}
        </label>
        <label>
            <input
                type="radio"
                bind:group={searchRange}
                value={SearchRange.Collection} />
            {collection}
        </label>

        <input
            type="text"
            bind:value={displayedSearch}
            on:keyup={searchKeyUp}
            on:focus={() => {
                searchRange = SearchRange.Custom;
            }}
            placeholder={searchLabel} />
    </div>

    <div class="range-box-inner">
        <label>
            <input type="radio" bind:group={revlogRange} value={RevlogRange.Month} />
            {month}
        </label>
        <label>
            <input type="radio" bind:group={revlogRange} value={RevlogRange.Year} />
            {year}
        </label>
        <label>
            <input type="radio" bind:group={revlogRange} value={RevlogRange.All} />
            {all}
        </label>
    </div>
</div>
<div class="range-box-pad" />

<TodayStats {sourceData} {i18n} />
<CardCounts {sourceData} {i18n} />
<FutureDue {sourceData} {i18n} />
<ReviewsGraph {sourceData} {revlogRange} {i18n} />
<IntervalsGraph {sourceData} {i18n} />
<EaseGraph {sourceData} {i18n} />
<HourGraph {sourceData} {i18n} />
<ButtonsGraph {sourceData} {i18n} />
<AddedGraph {sourceData} {i18n} />
