<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DailyLimits from "./DailyLimits.svelte";
    import NewOptions from "./NewOptions.svelte";
    import BuryingOptions from "./BuryingOptions.svelte";
    import AdvancedOptions from "./AdvancedOptions.svelte";
    import LapseOptions from "./LapseOptions.svelte";
    import GeneralOptions from "./GeneralOptions.svelte";
    import Addons from "./Addons.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
</script>

<style lang="scss">
    :global(h2) {
        margin-top: 1em;
    }

    :global(input, select) {
        font-size: 16px;
    }
</style>

<div>
    <DailyLimits {state} />
    <NewOptions {state} />
    <LapseOptions {state} />
    <BuryingOptions {state} />
    <GeneralOptions {state} />
    <Addons {state} />
    <AdvancedOptions {state} />
</div>
