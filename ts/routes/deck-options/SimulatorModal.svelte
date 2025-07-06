<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
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
    import { computeOptimalRetention, simulateFsrsReview } from "@generated/backend";
    import { runWithBackendProgress } from "@tslib/progress";
    import type {
        ComputeOptimalRetentionResponse,
        SimulateFsrsReviewRequest,
        SimulateFsrsReviewResponse,
    } from "@generated/anki/scheduler_pb";
    import type { DeckOptionsState } from "./lib";
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import GlobalLabel from "./GlobalLabel.svelte";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import { reviewOrderChoices } from "./choices";
    import EnumSelectorRow from "$lib/components/EnumSelectorRow.svelte";
    import { DeckConfig_Config_LeechAction } from "@generated/anki/deck_config_pb";
    import EasyDaysInput from "./EasyDaysInput.svelte";
    import Warning from "./Warning.svelte";
    import type { ComputeRetentionProgress } from "@generated/anki/collection_pb";
    import Modal from "bootstrap/js/dist/modal";

    export let state: DeckOptionsState;
    export let simulateFsrsRequest: SimulateFsrsReviewRequest;
    export let computing: boolean;
    export let openHelpModal: (key: string) => void;
    export let onPresetChange: () => void;

    const config = state.currentConfig;
    let simulateSubgraph: SimulateSubgraph = SimulateSubgraph.count;
    let tableData: TableDatum[] = [];
    let simulating: boolean = false;
    const fsrs = state.fsrs;
    const bounds = defaultGraphBounds();

    let svg: HTMLElement | SVGElement | null = null;
    let simulationNumber = 0;
    let points: Point[] = [];
    const newCardsIgnoreReviewLimit = state.newCardsIgnoreReviewLimit;
    let smooth = true;
    let suspendLeeches = $config.leechAction == DeckConfig_Config_LeechAction.SUSPEND;
    let leechThreshold = $config.leechThreshold;

    let optimalRetention: null | number = null;
    let computingRetention = false;
    let computeRetentionProgress: ComputeRetentionProgress | undefined = undefined;

    $: daysToSimulate = 365;
    $: deckSize = 0;
    $: windowSize = Math.ceil(daysToSimulate / 365);
    $: processing = simulating || computingRetention;

    function movingAverage(y: number[], windowSize: number): number[] {
        const result: number[] = [];
        for (let i = 0; i < y.length; i++) {
            let sum = 0;
            let count = 0;
            for (let j = Math.max(0, i - windowSize + 1); j <= i; j++) {
                sum += y[j];
                count++;
            }
            result.push(sum / count);
        }
        return result;
    }

    function addArrays(arr1: number[], arr2: number[]): number[] {
        return arr1.map((value, index) => value + arr2[index]);
    }

    function estimatedRetention(retention: number): String {
        if (!retention) {
            return "";
        }
        return tr.deckConfigPredictedOptimalRetention({ num: retention.toFixed(2) });
    }

    function updateRequest() {
        simulateFsrsRequest.daysToSimulate = daysToSimulate;
        simulateFsrsRequest.deckSize = deckSize;
        simulateFsrsRequest.suspendAfterLapseCount = suspendLeeches
            ? leechThreshold
            : undefined;
        simulateFsrsRequest.easyDaysPercentages = easyDayPercentages;
    }

    function renderRetentionProgress(
        val: ComputeRetentionProgress | undefined,
    ): String {
        if (!val) {
            return "";
        }
        return tr.deckConfigIterations({ count: val.current });
    }

    $: computeRetentionProgressString = renderRetentionProgress(
        computeRetentionProgress,
    );

    async function computeRetention() {
        let resp: ComputeOptimalRetentionResponse | undefined;
        updateRequest();
        try {
            await runWithBackendProgress(
                async () => {
                    computingRetention = true;
                    resp = await computeOptimalRetention(simulateFsrsRequest);
                },
                (progress) => {
                    if (progress.value.case === "computeRetention") {
                        computeRetentionProgress = progress.value.value;
                    }
                },
            );
        } finally {
            computingRetention = false;
            if (resp) {
                optimalRetention = resp.optimalRetention;
            }
        }
    }

    async function simulateFsrs(): Promise<void> {
        let resp: SimulateFsrsReviewResponse | undefined;
        updateRequest();
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
        let pointsToRender = points;
        if (smooth) {
            // Group points by label (simulation number)
            const groupedPoints = points.reduce(
                (acc, point) => {
                    acc[point.label] = acc[point.label] || [];
                    acc[point.label].push(point);
                    return acc;
                },
                {} as Record<number, Point[]>,
            );

            // Apply smoothing to each group separately
            pointsToRender = Object.values(groupedPoints).flatMap((group) => {
                const smoothedTimeCost = movingAverage(
                    group.map((p) => p.timeCost),
                    windowSize,
                );
                const smoothedCount = movingAverage(
                    group.map((p) => p.count),
                    windowSize,
                );
                const smoothedMemorized = movingAverage(
                    group.map((p) => p.memorized),
                    windowSize,
                );

                return group.map((p, i) => ({
                    ...p,
                    timeCost: smoothedTimeCost[i],
                    count: smoothedCount[i],
                    memorized: smoothedMemorized[i],
                }));
            });
        }

        tableData = renderSimulationChart(
            svg as SVGElement,
            bounds,
            pointsToRender,
            simulateSubgraph,
        );
    }

    $: easyDayPercentages = [...$config.easyDaysPercentages];

    export let modal: Modal | null = null;

    function setupModal(node: Element) {
        modal = new Modal(node);
        return {
            destroy() {
                modal?.dispose();
                modal = null;
            },
        };
    }
</script>

<div class="modal" tabindex="-1" use:setupModal>
    <div class="modal-dialog modal-xl">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">{tr.deckConfigFsrsSimulatorExperimental()}</h5>
                <button
                    type="button"
                    class="btn-close"
                    aria-label="Close"
                    on:click={() => modal?.hide()}
                ></button>
            </div>
            <div class="modal-body">
                <SpinBoxRow
                    bind:value={daysToSimulate}
                    defaultValue={365}
                    min={1}
                    max={3650}
                >
                    <SettingTitle on:click={() => openHelpModal("simulateFsrsReview")}>
                        {tr.deckConfigDaysToSimulate()}
                    </SettingTitle>
                </SpinBoxRow>

                <SpinBoxRow bind:value={deckSize} defaultValue={0} min={0} max={100000}>
                    <SettingTitle on:click={() => openHelpModal("simulateFsrsReview")}>
                        {tr.deckConfigAdditionalNewCardsToSimulate()}
                    </SettingTitle>
                </SpinBoxRow>

                <SpinBoxFloatRow
                    bind:value={simulateFsrsRequest.desiredRetention}
                    defaultValue={$config.desiredRetention}
                    min={0.7}
                    max={0.99}
                    percentage={true}
                >
                    <SettingTitle on:click={() => openHelpModal("desiredRetention")}>
                        {tr.deckConfigDesiredRetention()}
                    </SettingTitle>
                </SpinBoxFloatRow>

                <SpinBoxRow
                    bind:value={simulateFsrsRequest.newLimit}
                    defaultValue={$config.newPerDay}
                    min={0}
                    max={9999}
                >
                    <SettingTitle on:click={() => openHelpModal("newLimit")}>
                        {tr.schedulingNewCardsday()}
                    </SettingTitle>
                </SpinBoxRow>

                <SpinBoxRow
                    bind:value={simulateFsrsRequest.reviewLimit}
                    defaultValue={$config.reviewsPerDay}
                    min={0}
                    max={9999}
                >
                    <SettingTitle on:click={() => openHelpModal("reviewLimit")}>
                        {tr.schedulingMaximumReviewsday()}
                    </SettingTitle>
                </SpinBoxRow>

                <details>
                    <summary>{tr.deckConfigEasyDaysTitle()}</summary>
                    {#key easyDayPercentages}
                        <EasyDaysInput bind:values={easyDayPercentages} />
                    {/key}
                </details>

                <details>
                    <summary>{tr.deckConfigAdvancedSettings()}</summary>
                    <SpinBoxRow
                        bind:value={simulateFsrsRequest.maxInterval}
                        defaultValue={$config.maximumReviewInterval}
                        min={1}
                        max={36500}
                    >
                        <SettingTitle on:click={() => openHelpModal("maximumInterval")}>
                            {tr.schedulingMaximumInterval()}
                        </SettingTitle>
                    </SpinBoxRow>

                    <EnumSelectorRow
                        bind:value={simulateFsrsRequest.reviewOrder}
                        defaultValue={$config.reviewOrder}
                        choices={reviewOrderChoices($fsrs)}
                    >
                        <SettingTitle on:click={() => openHelpModal("reviewSortOrder")}>
                            {tr.deckConfigReviewSortOrder()}
                        </SettingTitle>
                    </EnumSelectorRow>

                    <SwitchRow
                        bind:value={simulateFsrsRequest.newCardsIgnoreReviewLimit}
                        defaultValue={$newCardsIgnoreReviewLimit}
                    >
                        <SettingTitle
                            on:click={() => openHelpModal("newCardsIgnoreReviewLimit")}
                        >
                            <GlobalLabel
                                title={tr.deckConfigNewCardsIgnoreReviewLimit()}
                            />
                        </SettingTitle>
                    </SwitchRow>

                    <SwitchRow bind:value={smooth} defaultValue={true}>
                        <SettingTitle
                            on:click={() => openHelpModal("simulateFsrsReview")}
                        >
                            {tr.deckConfigSmoothGraph()}
                        </SettingTitle>
                    </SwitchRow>

                    <SwitchRow
                        bind:value={suspendLeeches}
                        defaultValue={$config.leechAction ==
                            DeckConfig_Config_LeechAction.SUSPEND}
                    >
                        <SettingTitle on:click={() => openHelpModal("leechAction")}>
                            {tr.deckConfigSuspendLeeches()}
                        </SettingTitle>
                    </SwitchRow>

                    {#if suspendLeeches}
                        <SpinBoxRow
                            bind:value={leechThreshold}
                            defaultValue={$config.leechThreshold}
                            min={1}
                            max={9999}
                        >
                            <SettingTitle
                                on:click={() => openHelpModal("leechThreshold")}
                            >
                                {tr.schedulingLeechThreshold()}
                            </SettingTitle>
                        </SpinBoxRow>
                    {/if}
                </details>

                <div style="display:none;">
                    <details>
                        <summary>{tr.deckConfigComputeOptimalRetention()}</summary>
                        <button
                            class="btn {computingRetention
                                ? 'btn-warning'
                                : 'btn-primary'}"
                            disabled={!computingRetention && computing}
                            on:click={() => computeRetention()}
                        >
                            {#if computingRetention}
                                {tr.actionsCancel()}
                            {:else}
                                {tr.deckConfigComputeButton()}
                            {/if}
                        </button>

                        {#if optimalRetention}
                            {estimatedRetention(optimalRetention)}
                            {#if optimalRetention - $config.desiredRetention >= 0.01}
                                <Warning
                                    warning={tr.deckConfigDesiredRetentionBelowOptimal()}
                                    className="alert-warning"
                                />
                            {/if}
                        {/if}

                        {#if computingRetention}
                            <div>{computeRetentionProgressString}</div>
                        {/if}
                    </details>
                </div>
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

                <button
                    class="btn {computing ? 'btn-warning' : 'btn-primary'}"
                    disabled={computing}
                    on:click={() => {
                        $config.newPerDay = simulateFsrsRequest.newLimit;
                        $config.reviewsPerDay = simulateFsrsRequest.reviewLimit;
                        $config.maximumReviewInterval = simulateFsrsRequest.maxInterval;
                        $config.desiredRetention = simulateFsrsRequest.desiredRetention;
                        $newCardsIgnoreReviewLimit =
                            simulateFsrsRequest.newCardsIgnoreReviewLimit;
                        $config.reviewOrder = simulateFsrsRequest.reviewOrder;
                        $config.leechAction = suspendLeeches
                            ? DeckConfig_Config_LeechAction.SUSPEND
                            : DeckConfig_Config_LeechAction.TAG_ONLY;
                        $config.leechThreshold = leechThreshold;
                        $config.easyDaysPercentages = [...easyDayPercentages];
                        onPresetChange();
                    }}
                >
                    {tr.deckConfigSaveOptionsToPreset()}
                </button>

                {#if processing}
                    {tr.actionsProcessing()}
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

                    <div class="svg-container">
                        <svg
                            bind:this={svg}
                            viewBox={`0 0 ${bounds.width} ${bounds.height}`}
                        >
                            <CumulativeOverlay />
                            <HoverColumns />
                            <AxisTicks {bounds} />
                            <NoDataOverlay {bounds} />
                        </svg>
                    </div>

                    <TableData {tableData} />
                </Graph>
            </div>
        </div>
    </div>
</div>

<style>
    .modal {
        background-color: rgba(0, 0, 0, 0.5);
        --bs-modal-margin: 0;
    }

    .svg-container {
        width: 100%;
        max-height: calc(100vh - 400px); /* Account for modal header, controls, etc */
        aspect-ratio: 600 / 250;
        display: flex;
        align-items: center;
    }

    svg {
        width: 100%;
        height: 100%;
    }

    .modal-header {
        position: sticky;
        top: 0;
        background-color: var(--bs-body-bg);
        z-index: 100;
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

    summary {
        margin-bottom: 0.5em;
    }
</style>
