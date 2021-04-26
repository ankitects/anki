<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import type { DeckOptionsState, ConfigListEntry } from "./lib";

    import StickyBar from "sveltelib/StickyBar.svelte";
    import { getOptionsDropdown } from "./optionsDropdown";

    export let state: DeckOptionsState;
    let configList = state.configList;
    $: optionsDropdown = getOptionsDropdown(state, $configList);
</script>

<style lang="scss">
</style>

<StickyBar>
    <div>{tr.actionsOptionsFor({ val: state.currentDeck.name })}</div>
    <svelte:component this={optionsDropdown.component} {...optionsDropdown} />
</StickyBar>
