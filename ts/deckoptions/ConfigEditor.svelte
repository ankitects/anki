<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Section from "components/Section.svelte";
    import SectionItem from "components/SectionItem.svelte";

    import DailyLimits from "./DailyLimits.svelte";
    import DisplayOrder from "./DisplayOrder.svelte";
    import NewOptions from "./NewOptions.svelte";
    import AdvancedOptions from "./AdvancedOptions.svelte";
    import BuryOptions from "./BuryOptions.svelte";
    import LapseOptions from "./LapseOptions.svelte";
    import GeneralOptions from "./GeneralOptions.svelte";
    import Addons from "./Addons.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let options: Record<string, never> | undefined = undefined;
</script>

<Section api={options}>
    <SectionItem>
        <DailyLimits {state} />
    </SectionItem>

    <SectionItem>
        <NewOptions {state} />
    </SectionItem>
    <SectionItem>
        <LapseOptions {state} />
    </SectionItem>
    <SectionItem>
        <BuryOptions {state} />
    </SectionItem>

    {#if state.v3Scheduler}
        <SectionItem>
            <DisplayOrder {state} />
        </SectionItem>
    {/if}

    <SectionItem>
        <GeneralOptions {state} />
    </SectionItem>
    <SectionItem>
        <Addons {state} />
    </SectionItem>
    <SectionItem>
        <AdvancedOptions {state} />
    </SectionItem>
</Section>

<style lang="scss">
    :global(h2) {
        margin-top: 1em;
        font-weight: bold;
        // adding a border decreases the default font size,
        // so increase it again
        font-size: 2em;
        border-bottom: 1px solid var(--medium-border);
        margin-right: 16px;
        margin-bottom: 0.5em;
    }
    /* .outer { */
    /*     // the right margin has an indent to allow for the undo */
    /*     // buttons; add the same indent on the left for balance */
    /*     padding-left: 16px; */
    /* } */
</style>
