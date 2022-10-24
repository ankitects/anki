<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Writable } from "svelte/store";

    import Container from "../components/Container.svelte";
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import Row from "../components/Row.svelte";
    import type { DynamicSvelteComponent } from "../sveltelib/dynamicComponent";
    import Addons from "./Addons.svelte";
    import AdvancedOptions from "./AdvancedOptions.svelte";
    import AudioOptions from "./AudioOptions.svelte";
    import BuryOptions from "./BuryOptions.svelte";
    import ConfigSelector from "./ConfigSelector.svelte";
    import DailyLimits from "./DailyLimits.svelte";
    import DisplayOrder from "./DisplayOrder.svelte";
    import HtmlAddon from "./HtmlAddon.svelte";
    import LapseOptions from "./LapseOptions.svelte";
    import type { DeckOptionsState } from "./lib";
    import NewOptions from "./NewOptions.svelte";
    import TimerOptions from "./TimerOptions.svelte";

    export let state: DeckOptionsState;
    const addons = state.addonComponents;

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
    export const advancedOptions = {};

    let onPresetChange: () => void;
</script>

<ConfigSelector {state} on:presetchange={onPresetChange} />

<div class="deck-options-page">
    <Container
        breakpoint="sm"
        --gutter-inline="0.25rem"
        --gutter-block="0.75rem"
        class="container-columns"
    >
        <DynamicallySlottable slotHost={Item} api={options}>
            <Item>
                <Row class="row-columns">
                    <DailyLimits {state} api={dailyLimits} bind:onPresetChange />
                </Row>
            </Item>

            <Item>
                <Row class="row-columns">
                    <NewOptions {state} api={newOptions} />
                </Row>
            </Item>

            <Item>
                <Row class="row-columns">
                    <LapseOptions {state} api={lapseOptions} />
                </Row>
            </Item>

            {#if state.v3Scheduler}
                <Item>
                    <Row class="row-columns">
                        <DisplayOrder {state} api={displayOrder} />
                    </Row>
                </Item>
            {/if}

            <Item>
                <Row class="row-columns">
                    <TimerOptions {state} api={timerOptions} />
                </Row>
            </Item>

            <Item>
                <Row class="row-columns">
                    <BuryOptions {state} api={buryOptions} />
                </Row>
            </Item>

            <Item>
                <Row class="row-columns">
                    <AudioOptions {state} api={audioOptions} />
                </Row>
            </Item>

            <Item>
                <Row class="row-columns">
                    <Addons {state} />
                </Row>
            </Item>

            <Item>
                <Row class="row-columns">
                    <AdvancedOptions {state} api={advancedOptions} />
                </Row>
            </Item>
        </DynamicallySlottable>
    </Container>
</div>

<style lang="scss">
    @use "sass/breakpoints" as bp;

    .deck-options-page {
        overflow-x: hidden;

        @include bp.with-breakpoint("lg") {
            :global(.container) {
                display: block;
            }

            :global(.container-columns) {
                column-count: 2;
                column-gap: 5em;

                :global(.container) {
                    break-inside: avoid;
                }
            }

            :global(.row-columns) {
                display: block;
            }
        }
    }
</style>
