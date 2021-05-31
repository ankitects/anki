<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DailyLimits from "./DailyLimits.svelte";
    import DisplayOrder from "./DisplayOrder.svelte";
    import NewOptions from "./NewOptions.svelte";
    import AdvancedOptions from "./AdvancedOptions.svelte";
    import BuryOptions from "./BuryOptions.svelte";
    import LapseOptions from "./LapseOptions.svelte";
    import TimerOptions from "./TimerOptions.svelte";
    import AudioOptions from "./AudioOptions.svelte";
    import Addons from "./Addons.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
</script>

<div class="outer">
    <div class="small">
        <DailyLimits {state} />
    </div>

    <div class="large">
        <NewOptions {state} />
    </div>

    <div class="large">
        <LapseOptions {state} />
    </div>

    <div class="small">
        <BuryOptions {state} />
    </div>

    {#if state.v3Scheduler}
        <div class="small">
            <DisplayOrder {state} />
        </div>
    {/if}

    <div class="small">
        <TimerOptions {state} />
    </div>

    <div class="small">
        <AudioOptions {state} />
    </div>

    <div class="small">
        <Addons {state} />
    </div>

    <div class="large">
        <AdvancedOptions {state} />
    </div>
</div>

<style lang="scss">
    :global(h2) {
        margin-top: 1em;
        font-weight: bold;
        // adding a border decreases the default font size,
        // so increase it again
        font-size: 1.5em;
        border-bottom: 1px solid var(--medium-border);
        margin-right: 16px;
        margin-bottom: 0.5em;
    }
    .outer {
        // the right margin has an indent to allow for the undo
        // buttons; add some indentation on the left for balance
        padding-left: 6px;
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(24em, 1fr));
        column-gap: 2em;
        row-gap: 1.5em;
    }

    .small {
        grid-row: span 1;
    }

    // make grid items with more options span two rows
    .large {
        grid-row: span 2;
    }

</style>
