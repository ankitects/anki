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
    .sticky-bar {
        position: sticky;
        z-index: 1;
        top: 0;
        color: var(--text-fg);
        background: var(--window-bg);
        padding-bottom: 0.5em;
        padding-top: 0.5em;
    }

    .selector-grid {
        display: grid;
        grid-template-columns: 6fr 1fr;
        grid-column-gap: 0.5em;
        padding-right: 0.5em;
    }
</style>

<div class="sticky-bar">
    <div>{tr.actionsOptionsFor({ val: state.currentDeck.name })}</div>

    <div class="selector-grid">
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
