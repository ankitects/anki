<script lang="ts">
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import Graph from "../graphs/Graph.svelte";
    import HoverColumns from "../graphs/HoverColumns.svelte";
    import CumulativeOverlay from "../graphs/CumulativeOverlay.svelte";
    import AxisTicks from "../graphs/AxisTicks.svelte";
    import NoDataOverlay from "../graphs/NoDataOverlay.svelte";
    import TableData from "../graphs/TableData.svelte";
    import InputBox from "../graphs/InputBox.svelte";
    import { defaultGraphBounds, type TableDatum } from "../graphs/graph-helpers";
    import { SimulateSubgraph, type Point } from "../graphs/simulator";
    import * as tr from "@generated/ftl";
    import { renderSimulationChart } from "../graphs/simulator";
    import { simulateFsrsReview } from "@generated/backend";
    import { runWithBackendProgress } from "@tslib/progress";
    import type { SimulateFsrsReviewResponse } from "@generated/anki/scheduler_pb";
    import type { DeckOptionsState } from "./lib";

    export let shown = false;
    export let state: DeckOptionsState;
    export let simulateFsrsRequest: any;
    export let computing: boolean;
    export let simulating: boolean;
    export let openHelpModal: (key: string) => void;

    let config = state.currentConfig;
    let simulateSubgraph: SimulateSubgraph = SimulateSubgraph.count;
    let tableData: TableDatum[] = [];
    const bounds = defaultGraphBounds();
    bounds.marginLeft += 8;
    let svg: HTMLElement | SVGElement | null = null;
    let simulationNumber = 0;
    let points: Point[] = [];

    function addArrays(arr1: number[], arr2: number[]): number[] {
        return arr1.map((value, index) => value + arr2[index]);
    }

    async function simulateFsrs(): Promise<void> {
        let resp: SimulateFsrsReviewResponse | undefined;
        try {
            await runWithBackendProgress(
                async () => {
                    simulating = true;
                    resp = await simulateFsrsReview(simulateFsrsRequest);
                },
                () => {},
            );
        } finally {
            simulating = false;
            if (resp) {
                simulationNumber += 1;
                const dailyTotalCount = addArrays(
                    resp.dailyReviewCount,
                    resp.dailyNewCount,
                );

                const dailyMemorizedCount = resp.accumulatedKnowledgeAcquisition;

                points = points.concat(
                    resp.dailyTimeCost.map((v, i) => ({
                        x: i,
                        timeCost: v,
                        count: dailyTotalCount[i],
                        memorized: dailyMemorizedCount[i],
                        label: simulationNumber,
                    })),
                );

                tableData = renderSimulationChart(
                    svg as SVGElement,
                    bounds,
                    points,
                    simulateSubgraph,
                );
            }
        }
    }

    function clearSimulation() {
        points = points.filter((p) => p.label !== simulationNumber);
        simulationNumber = Math.max(0, simulationNumber - 1);
        tableData = renderSimulationChart(
            svg as SVGElement,
            bounds,
            points,
            simulateSubgraph,
        );
    }

    $: if (svg) {
        tableData = renderSimulationChart(
            svg as SVGElement,
            bounds,
            points,
            simulateSubgraph,
        );
    }
</script>

<div class="modal" class:show={shown} class:d-block={shown} tabindex="-1">
    <div class="modal-dialog modal-xl">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">{tr.deckConfigFsrsSimulatorExperimental()}</h5>
                <button
                    type="button"
                    class="btn-close"
                    aria-label="Close"
                    on:click={() => (shown = false)}
                ></button>
            </div>
            <div class="modal-body">
                <SpinBoxRow
                    bind:value={simulateFsrsRequest.daysToSimulate}
                    defaultValue={365}
                    min={1}
                    max={3650}
                >
                    <SettingTitle on:click={() => openHelpModal("simulateFsrsReview")}>
                        {tr.deckConfigDaysToSimulate()}
                    </SettingTitle>
                </SpinBoxRow>

                <SpinBoxRow
                    bind:value={simulateFsrsRequest.deckSize}
                    defaultValue={0}
                    min={0}
                    max={100000}
                >
                    <SettingTitle on:click={() => openHelpModal("simulateFsrsReview")}>
                        {tr.deckConfigAdditionalNewCardsToSimulate()}
                    </SettingTitle>
                </SpinBoxRow>

                <SpinBoxRow
                    bind:value={simulateFsrsRequest.newLimit}
                    defaultValue={$config.newPerDay}
                    min={0}
                    max={9999}
                >
                    <SettingTitle on:click={() => openHelpModal("simulateFsrsReview")}>
                        {tr.schedulingNewCardsday()}
                    </SettingTitle>
                </SpinBoxRow>

                <SpinBoxRow
                    bind:value={simulateFsrsRequest.reviewLimit}
                    defaultValue={$config.reviewsPerDay}
                    min={0}
                    max={9999}
                >
                    <SettingTitle on:click={() => openHelpModal("simulateFsrsReview")}>
                        {tr.schedulingMaximumReviewsday()}
                    </SettingTitle>
                </SpinBoxRow>

                <SpinBoxRow
                    bind:value={simulateFsrsRequest.maxInterval}
                    defaultValue={$config.maximumReviewInterval}
                    min={1}
                    max={36500}
                >
                    <SettingTitle on:click={() => openHelpModal("simulateFsrsReview")}>
                        {tr.schedulingMaximumInterval()}
                    </SettingTitle>
                </SpinBoxRow>

                <button
                    class="btn {computing ? 'btn-warning' : 'btn-primary'}"
                    disabled={computing}
                    on:click={simulateFsrs}
                >
                    {tr.deckConfigSimulate()}
                </button>

                <button
                    class="btn {computing ? 'btn-warning' : 'btn-primary'}"
                    disabled={computing}
                    on:click={clearSimulation}
                >
                    {tr.deckConfigClearLastSimulate()}
                </button>

                {#if simulating}
                    {tr.qtMiscProcessing()}
                {/if}

                <Graph>
                    <div class="radio-group">
                        <InputBox>
                            <label>
                                <input
                                    type="radio"
                                    value={SimulateSubgraph.count}
                                    bind:group={simulateSubgraph}
                                />
                                {tr.deckConfigFsrsSimulatorRadioCount()}
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    value={SimulateSubgraph.time}
                                    bind:group={simulateSubgraph}
                                />
                                {tr.statisticsReviewsTimeCheckbox()}
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    value={SimulateSubgraph.memorized}
                                    bind:group={simulateSubgraph}
                                />
                                {tr.deckConfigFsrsSimulatorRadioMemorized()}
                            </label>
                        </InputBox>
                    </div>

                    <svg
                        bind:this={svg}
                        viewBox={`0 0 ${bounds.width} ${bounds.height}`}
                    >
                        <CumulativeOverlay />
                        <HoverColumns />
                        <AxisTicks {bounds} />
                        <NoDataOverlay {bounds} />
                    </svg>

                    <TableData {tableData} />
                </Graph>
            </div>
        </div>
    </div>
</div>

<style>
    .modal {
        background-color: rgba(0, 0, 0, 0.5);
    }

    :global(.modal-xl) {
        max-width: 100vw;
    }

    div.radio-group {
        margin: 0.5em;
    }

    .btn {
        margin-bottom: 0.375rem;
    }
</style>
