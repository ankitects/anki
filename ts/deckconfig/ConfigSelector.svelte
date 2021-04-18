<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "anki/i18n";
    import type { DeckConfigState, ConfigListEntry } from "./lib";
    import OptionsDropdown from "./OptionsDropdown.svelte";

    export let state: DeckConfigState;
    let configList = state.configList;

    function configLabel(entry: ConfigListEntry): string {
        const count = tr.deckConfigUsedByDecks({ decks: entry.useCount });
        return `${entry.name} (${count})`;
    }

    function blur(this: HTMLSelectElement) {
        state.setCurrentIndex(parseInt(this.value));
    }
</script>

<style lang="scss">
    .form-select {
        display: inline-block;
        width: 70%;
    }

    .outer {
        position: fixed;
        z-index: 1;
        top: 0;
        left: 0;
        width: 100%;
        color: var(--text-fg);
        background: var(--window-bg);
        padding: 0.5em;
        display: flex;
        justify-content: center;
    }

    .inner {
        display: flex;
        flex-wrap: wrap;
        width: 35em;

        & > :global(*) {
            padding-left: 0.5em;
            padding-right: 0.5em;
        }
    }

    .padding {
        height: 3em;
    }
</style>

<div class="outer">
    <div class="inner">
        <div>{tr.actionsOptionsFor({ val: state.currentDeck.name })}</div>

        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" on:change={blur}>
            {#each $configList as entry}
                <option value={entry.idx} selected={entry.current}>
                    {configLabel(entry)}
                </option>
            {/each}
        </select>

        <OptionsDropdown {state} />
    </div>
</div>

<div class="padding">
    <!-- make sure subsequent content doesn't flow under us -->
</div>
