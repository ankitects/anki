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
    import TimerOptions from "./TimerOptions.svelte";
    import AudioOptions from "./AudioOptions.svelte";
    import Addons from "./Addons.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let options: Record<string, never>;
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
        <TimerOptions {state} />
    </SectionItem>
    <SectionItem>
        <AudioOptions {state} />
    </SectionItem>
    <SectionItem>
        <Addons {state} />
    </SectionItem>
    <SectionItem>
        <AdvancedOptions {state} />
    </SectionItem>
</Section>
