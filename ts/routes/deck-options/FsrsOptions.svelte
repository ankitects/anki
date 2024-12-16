<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import {
        ComputeRetentionProgress,
        type ComputeParamsProgress,
    } from "@generated/anki/collection_pb";
    import {
        ComputeOptimalRetentionRequest,
        SimulateFsrsReviewRequest,
        type SimulateFsrsReviewResponse,
    } from "@generated/anki/scheduler_pb";
    import {
        computeFsrsParams,
        computeOptimalRetention,
        simulateFsrsReview,
        evaluateParams,
        setWantsAbort,
    } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { runWithBackendProgress } from "@tslib/progress";

    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";

    import GlobalLabel from "./GlobalLabel.svelte";
    import { fsrsParams, type DeckOptionsState } from "./lib";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import Warning from "./Warning.svelte";
    import ParamsInputRow from "./ParamsInputRow.svelte";
    import ParamsSearchRow from "./ParamsSearchRow.svelte";
    import { renderSimulationChart, type Point } from "../graphs/simulator";
    import Graph from "../graphs/Graph.svelte";
    import HoverColumns from "../graphs/HoverColumns.svelte";
    import CumulativeOverlay from "../graphs/CumulativeOverlay.svelte";
    import AxisTicks from "../graphs/AxisTicks.svelte";
    import NoDataOverlay from "../graphs/NoDataOverlay.svelte";
    import TableData from "../graphs/TableData.svelte";
    import { defaultGraphBounds, type TableDatum } from "../graphs/graph-helpers";
    import InputBox from "../graphs/InputBox.svelte";

    export let state: DeckOptionsState;
    export let openHelpModal: (String) => void;

    const presetName = state.currentPresetName;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const fsrsReschedule = state.fsrsReschedule;
    const daysSinceLastOptimization = state.daysSinceLastOptimization;

    $: lastOptimizationWarning =
        $daysSinceLastOptimization > 30 ? tr.deckConfigOptimizeAllTip() : "";

    let computeParamsProgress: ComputeParamsProgress | undefined;
    let computingParams = false;
    let checkingParams = false;
    let computingRetention = false;
    let optimalRetention = 0;
    $: if ($presetName) {
        optimalRetention = 0;
    }
    $: computing = computingParams || checkingParams || computingRetention;
    $: defaultparamSearch = `preset:"${state.getCurrentNameForSearch()}" -is:suspended`;
    $: roundedRetention = Number($config.desiredRetention.toFixed(2));
    $: desiredRetentionWarning = getRetentionWarning(roundedRetention);
    $: retentionWarningClass = getRetentionWarningClass(roundedRetention);

    let computeRetentionProgress:
        | ComputeParamsProgress
        | ComputeRetentionProgress
        | undefined;

    let showTime = false;

    const optimalRetentionRequest = new ComputeOptimalRetentionRequest({
        daysToSimulate: 365,
        lossAversion: 2.5,
    });
    $: if (optimalRetentionRequest.daysToSimulate > 3650) {
        optimalRetentionRequest.daysToSimulate = 3650;
    }

    const simulateFsrsRequest = new SimulateFsrsReviewRequest({
        params: fsrsParams($config),
        desiredRetention: $config.desiredRetention,
        deckSize: 0,
        daysToSimulate: 365,
        newLimit: $config.newPerDay,
        reviewLimit: $config.reviewsPerDay,
        maxInterval: $config.maximumReviewInterval,
        search: `preset:"${state.getCurrentNameForSearch()}" -is:suspended`,
    });

    function getRetentionWarning(retention: number): string {
        const decay = -0.5;
        const factor = 0.9 ** (1 / decay) - 1;
        const stability = 100;
        const days = Math.round(
            (stability / factor) * (Math.pow(retention, 1 / decay) - 1),
        );
        if (days === 100) {
            return "";
        }
        return tr.deckConfigA100DayInterval({ days });
    }

    function getRetentionWarningClass(retention: number): string {
        if (retention < 0.7 || retention > 0.97) {
            return "alert-danger";
        } else if (retention < 0.8 || retention > 0.95) {
            return "alert-warning";
        } else {
            return "alert-info";
        }
    }

    function getIgnoreRevlogsBeforeMs() {
        return BigInt(
            $config.ignoreRevlogsBeforeDate
                ? new Date($config.ignoreRevlogsBeforeDate).getTime()
                : 0,
        );
    }

    async function computeParams(): Promise<void> {
        if (computingParams) {
            await setWantsAbort({});
            return;
        }
        if (state.presetAssignmentsChanged()) {
            alert(tr.deckConfigPleaseSaveYourChangesFirst());
            return;
        }
        computingParams = true;
        computeParamsProgress = undefined;
        try {
            await runWithBackendProgress(
                async () => {
                    const params = fsrsParams($config);
                    const resp = await computeFsrsParams({
                        search: $config.paramSearch
                            ? $config.paramSearch
                            : defaultparamSearch,
                        ignoreRevlogsBeforeMs: getIgnoreRevlogsBeforeMs(),
                        currentParams: params,
                    });

                    const already_optimal =
                        (params.length &&
                            params.every(
                                (n, i) => n.toFixed(4) === resp.params[i].toFixed(4),
                            )) ||
                        resp.params.length === 0;

                    if (already_optimal) {
                        const msg = resp.fsrsItems
                            ? tr.deckConfigFsrsParamsOptimal()
                            : tr.deckConfigFsrsParamsNoReviews();
                        setTimeout(() => alert(msg), 200);
                    } else {
                        $config.fsrsParams5 = resp.params;
                    }
                    if (computeParamsProgress) {
                        computeParamsProgress.current = computeParamsProgress.total;
                    }
                },
                (progress) => {
                    if (progress.value.case === "computeParams") {
                        computeParamsProgress = progress.value.value;
                    }
                },
            );
        } finally {
            computingParams = false;
        }
    }

    async function checkParams(): Promise<void> {
        if (checkingParams) {
            await setWantsAbort({});
            return;
        }
        if (state.presetAssignmentsChanged()) {
            alert(tr.deckConfigPleaseSaveYourChangesFirst());
            return;
        }
        checkingParams = true;
        computeParamsProgress = undefined;
        try {
            await runWithBackendProgress(
                async () => {
                    const search = $config.paramSearch
                        ? $config.paramSearch
                        : defaultparamSearch;
                    const resp = await evaluateParams({
                        params: fsrsParams($config),
                        search,
                        ignoreRevlogsBeforeMs: getIgnoreRevlogsBeforeMs(),
                    });
                    if (computeParamsProgress) {
                        computeParamsProgress.current = computeParamsProgress.total;
                    }
                    setTimeout(
                        () =>
                            alert(
                                `Log loss: ${resp.logLoss.toFixed(4)}, RMSE(bins): ${(
                                    resp.rmseBins * 100
                                ).toFixed(2)}%. ${tr.deckConfigSmallerIsBetter()}`,
                            ),
                        200,
                    );
                },
                (progress) => {
                    if (progress.value.case === "computeParams") {
                        computeParamsProgress = progress.value.value;
                    }
                },
            );
        } finally {
            checkingParams = false;
        }
    }

    async function computeRetention(): Promise<void> {
        if (computingRetention) {
            await setWantsAbort({});
            return;
        }
        if (state.presetAssignmentsChanged()) {
            alert(tr.deckConfigPleaseSaveYourChangesFirst());
            return;
        }
        computingRetention = true;
        computeRetentionProgress = undefined;
        try {
            await runWithBackendProgress(
                async () => {
                    optimalRetentionRequest.maxInterval = $config.maximumReviewInterval;
                    optimalRetentionRequest.params = fsrsParams($config);
                    optimalRetentionRequest.search = `preset:"${state.getCurrentNameForSearch()}" -is:suspended`;
                    const resp = await computeOptimalRetention(optimalRetentionRequest);
                    optimalRetention = resp.optimalRetention;
                    computeRetentionProgress = undefined;
                },
                (progress) => {
                    if (progress.value.case === "computeRetention") {
                        computeRetentionProgress = progress.value.value;
                    }
                },
            );
        } finally {
            computingRetention = false;
        }
    }

    $: computeParamsProgressString = renderWeightProgress(computeParamsProgress);
    $: computeRetentionProgressString = renderRetentionProgress(
        computeRetentionProgress,
    );
    $: totalReviews = computeParamsProgress?.reviews ?? undefined;

    function renderWeightProgress(val: ComputeParamsProgress | undefined): String {
        if (!val || !val.total) {
            return "";
        }
        const pct = ((val.current / val.total) * 100).toFixed(1);
        if (val instanceof ComputeRetentionProgress) {
            return `${pct}%`;
        } else {
            return tr.deckConfigPercentOfReviews({ pct, reviews: val.reviews });
        }
    }

    function renderRetentionProgress(
        val: ComputeRetentionProgress | undefined,
    ): String {
        if (!val) {
            return "";
        }
        return tr.deckConfigIterations({ count: val.current });
    }

    function estimatedRetention(retention: number): String {
        if (!retention) {
            return "";
        }
        return tr.deckConfigPredictedOptimalRetention({ num: retention.toFixed(2) });
    }

    let tableData: TableDatum[] = [] as any;
    const bounds = defaultGraphBounds();
    let svg = null as HTMLElement | SVGElement | null;
    const title = tr.statisticsReviewsTitle();
    let simulationNumber = 0;

    let points: Point[] = [];

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

    $: simulateProgressString = "";

    async function simulateFsrs(): Promise<void> {
        let resp: SimulateFsrsReviewResponse | undefined;
        simulationNumber += 1;
        try {
            await runWithBackendProgress(
                async () => {
                    simulateFsrsRequest.params = fsrsParams($config);
                    simulateFsrsRequest.desiredRetention = $config.desiredRetention;
                    simulateFsrsRequest.search = `preset:"${state.getCurrentNameForSearch()}" -is:suspended`;
                    simulateProgressString = "processing...";
                    resp = await simulateFsrsReview(simulateFsrsRequest);
                },
                () => {},
            );
        } finally {
            simulateProgressString = "";
            if (resp) {
                const dailyTimeCost = movingAverage(
                    resp.dailyTimeCost,
                    Math.ceil(simulateFsrsRequest.daysToSimulate / 50),
                );
                const dailyReviewCount = movingAverage(
                    addArrays(resp.dailyReviewCount, resp.dailyNewCount),
                    Math.ceil(simulateFsrsRequest.daysToSimulate / 50),
                );
                points = points.concat(
                    dailyTimeCost.map((v, i) => ({
                        x: i,
                        timeCost: v,
                        count: dailyReviewCount[i],
                        label: simulationNumber,
                    })),
                );
                tableData = renderSimulationChart(
                    svg as SVGElement,
                    bounds,
                    points,
                    showTime,
                );
            }
        }
    }

    $: tableData = renderSimulationChart(svg as SVGElement, bounds, points, showTime);

    function clearSimulation(): void {
        points = points.filter((p) => p.label !== simulationNumber);
        simulationNumber = Math.max(0, simulationNumber - 1);
        tableData = renderSimulationChart(svg as SVGElement, bounds, points, showTime);
    }

    const label = tr.statisticsReviewsTimeCheckbox();
</script>

<SpinBoxFloatRow
    bind:value={$config.desiredRetention}
    defaultValue={defaults.desiredRetention}
    min={0.7}
    max={0.99}
>
    <SettingTitle on:click={() => openHelpModal("desiredRetention")}>
        {tr.deckConfigDesiredRetention()}
    </SettingTitle>
</SpinBoxFloatRow>

<Warning warning={desiredRetentionWarning} className={retentionWarningClass} />

<div class="ms-1 me-1">
    <ParamsInputRow
        bind:value={$config.fsrsParams5}
        defaultValue={[]}
        defaults={defaults.fsrsParams5}
    >
        <SettingTitle on:click={() => openHelpModal("modelParams")}>
            {tr.deckConfigWeights()}
        </SettingTitle>
    </ParamsInputRow>

    <ParamsSearchRow
        bind:value={$config.paramSearch}
        placeholder={defaultparamSearch}
    />

    <button
        class="btn {computingParams ? 'btn-warning' : 'btn-primary'}"
        disabled={!computingParams && computing}
        on:click={() => computeParams()}
    >
        {#if computingParams}
            {tr.actionsCancel()}
        {:else}
            {tr.deckConfigOptimizeButton()}
        {/if}
    </button>
    <button
        class="btn {checkingParams ? 'btn-warning' : 'btn-primary'}"
        disabled={!checkingParams && computing}
        on:click={() => checkParams()}
    >
        {#if checkingParams}
            {tr.actionsCancel()}
        {:else}
            {tr.deckConfigEvaluateButton()}
        {/if}
    </button>
    <div>
        {#if computingParams || checkingParams}
            {computeParamsProgressString}
        {:else if totalReviews !== undefined}
            {tr.statisticsReviews({ reviews: totalReviews })}
        {/if}
    </div>

    <Warning warning={lastOptimizationWarning} className="alert-warning" />
</div>

<div class="m-2">
    <SwitchRow bind:value={$fsrsReschedule} defaultValue={false}>
        <SettingTitle on:click={() => openHelpModal("rescheduleCardsOnChange")}>
            <GlobalLabel title={tr.deckConfigRescheduleCardsOnChange()} />
        </SettingTitle>
    </SwitchRow>

    {#if $fsrsReschedule}
        <Warning warning={tr.deckConfigRescheduleCardsWarning()} />
    {/if}
</div>

<div class="m-2">
    <details>
        <summary>{tr.deckConfigComputeOptimalRetention()}</summary>

        <SpinBoxRow
            bind:value={optimalRetentionRequest.daysToSimulate}
            defaultValue={365}
            min={1}
            max={3650}
        >
            <SettingTitle on:click={() => openHelpModal("computeOptimalRetention")}>
                {tr.deckConfigDaysToSimulate()}
            </SettingTitle>
        </SpinBoxRow>

        <button
            class="btn {computingRetention ? 'btn-warning' : 'btn-primary'}"
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

<div class="m-2">
    <details>
        <summary>FSRS simulator (experimental)</summary>

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
                {tr.deckConfigAdditionalSimulateCards()}
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
            on:click={() => simulateFsrs()}
        >
            {"Simulate"}
        </button>

        <button
            class="btn {computing ? 'btn-warning' : 'btn-primary'}"
            disabled={computing}
            on:click={() => clearSimulation()}
        >
            {"Clear last simulation"}
        </button>
        <div>{simulateProgressString}</div>

        <Graph {title}>
            <InputBox>
                <label>
                    <input type="checkbox" bind:checked={showTime} />
                    {label}
                </label>
            </InputBox>

            <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
                <CumulativeOverlay />
                <HoverColumns />
                <AxisTicks {bounds} />
                <NoDataOverlay {bounds} />
            </svg>

            <TableData {tableData} />
        </Graph>
    </details>
</div>

<style>
</style>
