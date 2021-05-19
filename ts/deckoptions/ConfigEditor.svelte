<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DailyLimits from "./DailyLimits.svelte";
    import LearningOptions from "./LearningOptions.svelte";
    import NewOptions from "./NewOptions.svelte";
    import AdvancedOptions from "./AdvancedOptions.svelte";
    import ReviewOptions from "./ReviewOptions.svelte";
    import LapseOptions from "./LapseOptions.svelte";
    import GeneralOptions from "./GeneralOptions.svelte";
    import Addons from "./Addons.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
</script>

<style lang="scss">
    :global(h2) {
        margin-top: 1em;
        font-weight: bold;
    }
    .outer {
        // the right margin has an indent to allow for the undo
        // buttons; add the same indent on the left for balance
        padding-left: 16px;
    }
</style>

<div class="outer">
    <DailyLimits {state} />
    <LearningOptions {state} />
    <NewOptions {state} />
    <ReviewOptions {state} />
    <LapseOptions {state} />
    <GeneralOptions {state} />
    <Addons {state} />
    <AdvancedOptions {state} />
</div>
