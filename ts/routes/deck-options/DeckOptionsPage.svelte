<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Writable } from "svelte/store";

    import "$lib/sveltelib/export-runtime";

    import Container from "$lib/components/Container.svelte";
    import Row from "$lib/components/Row.svelte";
    import type { DynamicSvelteComponent } from "$lib/sveltelib/dynamicComponent";

    import Addons from "./Addons.svelte";
    import AdvancedOptions from "./AdvancedOptions.svelte";
    import AudioOptions from "./AudioOptions.svelte";
    import AutoAdvance from "./AutoAdvance.svelte";
    import BuryOptions from "./BuryOptions.svelte";
    import ConfigSelector from "./ConfigSelector.svelte";
    import DailyLimits from "./DailyLimits.svelte";
    import DisplayOrder from "./DisplayOrder.svelte";
    import FsrsOptionsOuter from "./FsrsOptionsOuter.svelte";
    import HtmlAddon from "./HtmlAddon.svelte";
    import LapseOptions from "./LapseOptions.svelte";
    import type { DeckOptionsState } from "./lib";
    import NewOptions from "./NewOptions.svelte";
    import TimerOptions from "./TimerOptions.svelte";
    import EasyDays from "./EasyDays.svelte";

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
    export const easyDays = {};

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
        <div>
            <Row class="row-columns">
                <DailyLimits {state} api={dailyLimits} bind:onPresetChange />
            </Row>

            <Row class="row-columns">
                <NewOptions {state} api={newOptions} />
            </Row>

            <Row class="row-columns">
                <LapseOptions {state} api={lapseOptions} />
            </Row>

            <Row class="row-columns">
                <DisplayOrder {state} api={displayOrder} />
            </Row>

            <Row class="row-columns">
                <FsrsOptionsOuter {state} api={{}} bind:onPresetChange />
            </Row>
        </div>

        <div>
            <Row class="row-columns">
                <BuryOptions {state} api={buryOptions} />
            </Row>

            <Row class="row-columns">
                <AudioOptions {state} api={audioOptions} />
            </Row>

            <Row class="row-columns">
                <TimerOptions {state} api={timerOptions} />
            </Row>

            <Row class="row-columns">
                <AutoAdvance {state} api={timerOptions} />
            </Row>

            {#if $addons.length}
                <Row class="row-columns">
                    <Addons {state} />
                </Row>
            {/if}

            <Row class="row-columns">
                <EasyDays {state} api={easyDays} />
            </Row>

            <Row class="row-columns">
                <AdvancedOptions {state} api={advancedOptions} />
            </Row>
        </div>
    </Container>
</div>

<style lang="scss">
    @use "$lib/sass/breakpoints" as bp;

    .deck-options-page {
        overflow-x: auto;

        :global(.container-columns) {
            display: grid;
            gap: 0px;
        }

        @include bp.with-breakpoint("lg") {
            :global(.container-columns) {
                grid-template-columns: repeat(2, 1fr);
                gap: 20px;
            }
        }
    }
</style>
