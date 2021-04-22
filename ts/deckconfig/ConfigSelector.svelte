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
        grid-column: 1 / 5;
    }

    .fixed-bar {
        position: fixed;
        z-index: 1;
        top: 0;
        left: 0;
        width: 100%;
        color: var(--text-fg);
        background: var(--window-bg);
        display: flex;
        justify-content: center;
    }

    .grid {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr 1fr 1fr;
        grid-column-gap: 0.5em;
    }

    .padding {
        height: 3em;
    }
</style>

<div class="fixed-bar">
    <div class="width-limited">
        <div>{tr.actionsOptionsFor({ val: state.currentDeck.name })}</div>

        <div class="grid">
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
</div>

<div class="padding">
    <!-- make sure subsequent content doesn't flow under us -->
</div>
