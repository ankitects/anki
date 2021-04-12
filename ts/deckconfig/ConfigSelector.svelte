<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "anki/i18n";
    import type { DeckConfigId, ConfigWithCount } from "./lib";
    import OptionsDropdown from "./OptionsDropdown.svelte";

    export let allConfig: ConfigWithCount[];
    export let selectedConfigId: DeckConfigId;

    function configLabel(config: ConfigWithCount): string {
        const name = config.config.name;
        const count = tr.deckConfigUsedByDecks({ decks: config.useCount });
        return `${name} (${count})`;
    }
</script>

<style lang="scss">
    .form-select {
        display: inline-block;
        width: 30em;
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
    }

    .inner {
        display: flex;
        justify-content: center;

        & > :global(*) {
            padding-left: 0.5em;
            padding-right: 0.5em;
        }
    }
</style>

<div class="outer">
    <div class="inner">
        <select bind:value={selectedConfigId} class="form-select">
            {#each allConfig as config}
                <option value={config.config.id}>{configLabel(config)}</option>
            {/each}
        </select>

        <OptionsDropdown />
    </div>
</div>
