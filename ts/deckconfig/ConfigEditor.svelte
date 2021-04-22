<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DailyLimits from "./DailyLimits.svelte";
    import NewOptions from "./NewOptions.svelte";
    import ReviewOptions from "./ReviewOptions.svelte";
    import LapseOptions from "./LapseOptions.svelte";
    import GeneralOptions from "./GeneralOptions.svelte";
    import type { DeckConfigState } from "./lib";

    export let state: DeckConfigState;
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
    <ReviewOptions {state} />
    <LapseOptions {state} />
    <GeneralOptions {state} />
</div>
