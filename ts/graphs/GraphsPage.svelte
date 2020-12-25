<script context="module">
    // import _ from "./graph-helpers.scss";
</script>

<script lang="typescript">
    import { fade } from 'svelte/transition';
    import type { I18n } from "anki/i18n";
    import type pb from "anki/backend_proto";
    import { getGraphData, RevlogRange } from "./graph-helpers";

    export let i18n: I18n;
    export let nightMode: boolean;
    export let graphs: any[];

    export let search: string;
    export let revlogRange: RevlogRange;
    export let withRangeBox: boolean;

    let dataPromise;
    let days;

    enum SearchRange {
        Deck = 1,
        Collection = 2,
        Custom = 3,
    }

    let displayedSearch = search;
    let searchRange: SearchRange = SearchRange.Deck;

    let refreshing = true;

    $: {
        if (refreshing) {
            dataPromise = getGraphData(search, days);
            console.log(dataPromise)
            refreshing = false;
        }
    }

    $: {
        // refresh if either of these change
        search;
        days;
        refreshing = true;
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
        if (e.key == "Enter") {
            const wasSame = search == displayedSearch;
            search = displayedSearch;
            if (wasSame) {
                //  force a refresh (user may have changed current deck, etc)
                refreshing = true;
            }
        }
    };

    const year = i18n.tr(i18n.TR.STATISTICS_RANGE_1_YEAR_HISTORY);
    const deck = i18n.tr(i18n.TR.STATISTICS_RANGE_DECK);
    const collection = i18n.tr(i18n.TR.STATISTICS_RANGE_COLLECTION);
    const searchLabel = i18n.tr(i18n.TR.STATISTICS_RANGE_SEARCH);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_HISTORY);
</script>

{#if withRangeBox}
    <div class="range-box">
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
{/if}

{#await dataPromise}
    <div class="spin" transition:fade="{{duration=50}}">◐</div>
{:then sourceData}
    <div tabindex="-1" class="no-focus-outline">
        {#each graphs as Graph}
            <Graph {sourceData} {revlogRange} {i18n} {nightMode} />
        {/each}
    </div>
{:catch error}
    <script>
        alert({i18n.tr(i18n.TR.STATISTICS_ERROR_FETCHING)});
    </script>
{/await}
