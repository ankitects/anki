<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigSelector from "./ConfigSelector.svelte";
    import Container from "components/Container.svelte";
    import Item from "components/Item.svelte";
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

<div class="multi-column">
    <Container api={options} class="g-1 mb-3 mt-3">
        <Item>
            <DailyLimits {state} api={dailyLimits} />
        </Item>

        <Item>
            <NewOptions {state} api={newOptions} />
        </Item>

        <Item>
            <LapseOptions {state} api={lapseOptions} />
        </Item>

        {#if state.v3Scheduler}
            <Item>
                <DisplayOrder {state} api={displayOrder} />
            </Item>
        {/if}

        <Item>
            <TimerOptions {state} api={timerOptions} />
        </Item>

        <Item>
            <BuryOptions {state} api={buryOptions} />
        </Item>

        <Item>
            <AudioOptions {state} api={audioOptions} />
        </Item>

        <Item>
            <Addons {state} api={addonOptions} />
        </Item>

        <Item>
            <AdvancedOptions {state} api={advancedOptions} />
        </Item>
    </Container>
</div>

<style lang="scss">
    .multi-column :global(.container) {
        column-count: 2;
        column-gap: 5em;
    }

    @media (max-width: 1000px) {
        .multi-column :global(.container) {
            column-count: 1;
        }
    }
</style>
