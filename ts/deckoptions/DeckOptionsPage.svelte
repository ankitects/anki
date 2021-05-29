<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigSelector from "./ConfigSelector.svelte";
    import Container from "components/Container.svelte";
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
    import type { Writable } from "svelte/store";
    import HtmlAddon from "./HtmlAddon.svelte";
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

    export let state: DeckOptionsState;
    let addons = state.addonComponents;

    export function auxData(): Writable<Record<string, unknown>> {
        return state.currentAuxData;
    }

    export function addSvelteAddon(component: DynamicSvelteComponent): void {
        $addons = [...$addons, component];
    }

    export function addHtmlAddon(html: string, mounted: () => void): void {
        $addons = [
            ...$addons,
            {
                component: HtmlAddon,
                html,
                mounted,
            },
        ];
    }

    export const options = {};
    export const dailyLimits = {};
    export const newOptions = {};
    export const lapseOptions = {};
    export const buryOptions = {};
    export const displayOrder = {};
    export const timerOptions = {};
    export const audioOptions = {};
    export const addonOptions = {};
    export const advancedOptions = {};
</script>

<ConfigSelector {state} />

<Container api={options}>
    <SectionItem>
        <DailyLimits {state} api={dailyLimits} />
    </SectionItem>

    <SectionItem>
        <NewOptions {state} api={newOptions} />
    </SectionItem>
    <SectionItem>
        <LapseOptions {state} api={lapseOptions} />
    </SectionItem>
    <SectionItem>
        <BuryOptions {state} api={buryOptions} />
    </SectionItem>

    {#if state.v3Scheduler}
        <SectionItem>
            <DisplayOrder {state} api={displayOrder} />
        </SectionItem>
    {/if}

    <SectionItem>
        <TimerOptions {state} api={timerOptions} />
    </SectionItem>
    <SectionItem>
        <AudioOptions {state} api={audioOptions} />
    </SectionItem>
    <SectionItem>
        <Addons {state} api={addonOptions} />
    </SectionItem>
    <SectionItem>
        <AdvancedOptions {state} api={advancedOptions} />
    </SectionItem>
</Container>
