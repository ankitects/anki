<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Writable } from "svelte/store";

    import InputBox from "./InputBox.svelte";

    import * as tr from "lib/i18n";
    import { RevlogRange, daysToRevlogRange } from "./graph-helpers";

    enum SearchRange {
        Deck = 1,
        Collection = 2,
        Custom = 3,
    }

    export let loading: boolean;

    export let days: Writable<number>;
    export let search: Writable<string>;

    let revlogRange = daysToRevlogRange($days);
    let searchRange =
        $search === "deck:current"
            ? SearchRange.Deck
            : $search === ""
            ? SearchRange.Collection
            : SearchRange.Custom;

    let displayedSearch = $search;

    $: {
        switch (searchRange as SearchRange) {
            case SearchRange.Deck:
                $search = displayedSearch = "deck:current";
                break;
            case SearchRange.Collection:
                $search = displayedSearch = "";
                break;
        }
    }

    $: {
        switch (revlogRange as RevlogRange) {
            case RevlogRange.Year:
                $days = 365;
                break;
            case RevlogRange.All:
                $days = 0;
                break;
        }
    }

    function searchKeyUp(event: KeyboardEvent): void {
        // fetch data on enter
        if (event.code === "Enter") {
            $search = displayedSearch;
        }
    }

    const year = tr.statisticsRange_1YearHistory();
    const deck = tr.statisticsRangeDeck();
    const collection = tr.statisticsRangeCollection();
    const searchLabel = tr.statisticsRangeSearch();
    const all = tr.statisticsRangeAllHistory();
</script>

<div class="range-box">
    <div class="spin" class:loading>◐</div>

    <InputBox>
        <label>
            <input type="radio" bind:group={searchRange} value={SearchRange.Deck} />
            {deck}
        </label>
        <label>
            <input
                type="radio"
                bind:group={searchRange}
                value={SearchRange.Collection}
            />
            {collection}
        </label>

        <input
            type="text"
            bind:value={displayedSearch}
            on:keyup={searchKeyUp}
            on:focus={() => {
                searchRange = SearchRange.Custom;
            }}
            placeholder={searchLabel}
        />
    </InputBox>

    <InputBox>
        <label>
            <input type="radio" bind:group={revlogRange} value={RevlogRange.Year} />
            {year}
        </label>
        <label>
            <input type="radio" bind:group={revlogRange} value={RevlogRange.All} />
            {all}
        </label>
    </InputBox>
</div>

<div class="range-box-pad" />

<style lang="scss">
    .range-box {
        position: fixed;
        z-index: 1;
        top: 0;
        width: 100%;
        color: var(--text-fg);
        background: var(--window-bg);
        padding: 0.5em;

        @media print {
            position: absolute;
        }
    }

    @keyframes spin {
        0% {
            -webkit-transform: rotate(0deg);
        }
        100% {
            -webkit-transform: rotate(360deg);
        }
    }

    .spin {
        display: inline-block;
        position: absolute;
        font-size: 2em;
        animation: spin;
        animation-duration: 1s;
        animation-iteration-count: infinite;

        opacity: 0;

        &.loading {
            opacity: 0.5;
            transition: opacity 1s;
        }
    }

    .range-box-pad {
        height: 2em;
    }
</style>
