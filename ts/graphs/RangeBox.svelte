<script lang="typescript">
    import { createEventDispatcher } from 'svelte';

    import { RevlogRange } from "./graph-helpers";

    enum SearchRange {
        Deck = 1,
        Collection = 2,
        Custom = 3,
    }

    export let i18n: I18n;

    const dispatch = createEventDispatcher();

    let revlogRange: RevlogRange = RevlogRange.Year;
    let searchRange: SearchRange = SearchRange.Deck;

    let days;
    let search;
    let displayedSearch = search;

    const update = () => {
        dispatch('update', {
            days: days,
            search: search,
            searchRange: searchRange,
        });
    };

    $: {
        switch (searchRange as SearchRange) {
            case SearchRange.Deck:
                search = displayedSearch = "deck:current";
                update()
                break;
            case SearchRange.Collection:
                search = displayedSearch = "";
                update()
                break;
        }
    }

    $: {
        switch (revlogRange as RevlogRange) {
            case RevlogRange.Year:
                days = 365;
                update()
                break;
            case RevlogRange.All:
                days = 0;
                update()
                break;
        }
    }

    const searchKeyUp = (e: KeyboardEvent) => {
        // fetch data on enter
        if (e.key == "Enter") {
            search = displayedSearch;
            update()
        }
    };

    const year = i18n.tr(i18n.TR.STATISTICS_RANGE_1_YEAR_HISTORY);
    const deck = i18n.tr(i18n.TR.STATISTICS_RANGE_DECK);
    const collection = i18n.tr(i18n.TR.STATISTICS_RANGE_COLLECTION);
    const searchLabel = i18n.tr(i18n.TR.STATISTICS_RANGE_SEARCH);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_HISTORY);
</script>


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
