<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let symbol: string;

    export let containsHTML: boolean;
    export let fontFamily: string;

    export let names: string[];

    // Emojis can have a string length of up to 5 characters.
    // This could be done better with Intl.Segmenter once it has wider support:
    // const segmenter = new Intl.Segmenter();
    // const displayInTwoRows = [...segmenter.segment(symbol)].length;
    $: displayInTwoRows = symbol.length > 6;
</script>

<div class="symbols-entry" style:flex-direction={displayInTwoRows ? "column" : "row"}>
    <div class="symbol" style:font-family={fontFamily}>
        {#if containsHTML}
            {@html symbol}
        {:else}
            {symbol}
        {/if}
    </div>

    <div class="description">
        {#each names as name}
            <span class="name">
                <slot symbolName={name} />
            </span>
        {/each}
    </div>
</div>

<style lang="scss">
    .symbols-entry {
        display: flex;
    }

    .symbol {
        transform: scale(1.1);
        font-size: 150%;
        /* The widest emojis I could find were couple_with_heart_ */
        /* We should make sure it can still be properly displayed */
        width: 38px;
    }

    .description {
        align-self: center;
    }

    .name {
        margin-left: 3px;
    }
</style>
