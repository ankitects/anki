<script lang="typescript">
    import { createEventDispatcher } from "svelte";

    import type { I18n } from "anki/i18n";
    import { RevlogRange, daysToRevlogRange } from "./graph-helpers";

    enum SearchRange {
        Deck = 1,
        Collection = 2,
        Custom = 3,
    }

    type UpdateEventMap = {
        update: { days: number; search: string; searchRange: SearchRange };
    };

    export let i18n: I18n;
    export let active: boolean;

    export let days: number;
    export let search: string;

    const dispatch = createEventDispatcher<UpdateEventMap>();

    let revlogRange = daysToRevlogRange(days);
    let searchRange: SearchRange =
        search === "deck:current"
            ? SearchRange.Deck
            : search === ""
            ? SearchRange.Collection
            : SearchRange.Custom;

    let displayedSearch = search;

    const update = () => {
        dispatch("update", {
            days: days,
            search: search,
            searchRange: searchRange,
        });
    };

    $: {
        switch (searchRange as SearchRange) {
            case SearchRange.Deck:
                search = displayedSearch = "deck:current";
                update();
                break;
            case SearchRange.Collection:
                search = displayedSearch = "";
                update();
                break;
        }
    }

    $: {
        switch (revlogRange as RevlogRange) {
            case RevlogRange.Year:
                days = 365;
                update();
                break;
            case RevlogRange.All:
                days = 0;
                update();
                break;
        }
    }

    const searchKeyUp = (e: KeyboardEvent) => {
        // fetch data on enter
        if (e.key == "Enter") {
            search = displayedSearch;
            update();
        }
    };

    const year = i18n.tr(i18n.TR.STATISTICS_RANGE_1_YEAR_HISTORY);
    const deck = i18n.tr(i18n.TR.STATISTICS_RANGE_DECK);
    const collection = i18n.tr(i18n.TR.STATISTICS_RANGE_COLLECTION);
    const searchLabel = i18n.tr(i18n.TR.STATISTICS_RANGE_SEARCH);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_HISTORY);
</script>

<div class="range-box">
    <div class="spin" class:active>◐</div>

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
