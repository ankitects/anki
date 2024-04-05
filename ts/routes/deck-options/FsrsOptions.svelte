<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import {
        ComputeRetentionProgress,
        type ComputeWeightsProgress,
    } from "@generated/anki/collection_pb";
    import { ComputeOptimalRetentionRequest } from "@generated/anki/scheduler_pb";
    import {
        computeFsrsWeights,
        computeOptimalRetention,
        evaluateWeights,
        setWantsAbort,
    } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { runWithBackendProgress } from "@tslib/progress";

    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";

    import DateInput from "./DateInput.svelte";
    import GlobalLabel from "./GlobalLabel.svelte";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import Warning from "./Warning.svelte";
    import WeightsInputRow from "./WeightsInputRow.svelte";

    export let state: DeckOptionsState;
    export let openHelpModal: (String) => void;

    const presetName = state.currentPresetName;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const fsrsReschedule = state.fsrsReschedule;
    const daysSinceLastOptimization = state.daysSinceLastOptimization;

    $: lastOptimizationWarning =
        $daysSinceLastOptimization > 30 ? tr.deckConfigOptimizeAllTip() : "";

    let computeWeightsProgress: ComputeWeightsProgress | undefined;
    let computingWeights = false;
    let checkingWeights = false;
    let computingRetention = false;
    let optimalRetention = 0;
    $: if ($presetName) {
        optimalRetention = 0;
    }
    $: computing = computingWeights || checkingWeights || computingRetention;
    $: defaultWeightSearch = `preset:"${state.getCurrentName()}" -is:suspended`;
    $: desiredRetentionWarning = getRetentionWarning($config.desiredRetention);
    $: retentionWarningClass = getRetentionWarningClass($config.desiredRetention);

    let computeRetentionProgress:
        | ComputeWeightsProgress
        | ComputeRetentionProgress
        | undefined;

    const optimalRetentionRequest = new ComputeOptimalRetentionRequest({
        daysToSimulate: 365,
        lossAversion: 2.5,
    });
    $: if (optimalRetentionRequest.daysToSimulate > 3650) {
        optimalRetentionRequest.daysToSimulate = 3650;
    }

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

    async function computeWeights(): Promise<void> {
        if (computingWeights) {
            await setWantsAbort({});
            return;
        }
        if (state.presetAssignmentsChanged()) {
            alert(tr.deckConfigPleaseSaveYourChangesFirst());
            return;
        }
        computingWeights = true;
        computeWeightsProgress = undefined;
        try {
            await runWithBackendProgress(
                async () => {
                    const resp = await computeFsrsWeights({
                        search: $config.weightSearch
                            ? $config.weightSearch
                            : defaultWeightSearch,
                        ignoreRevlogsBeforeMs: getIgnoreRevlogsBeforeMs(),
                        currentWeights: $config.fsrsWeights,
                    });
                    if (
                        ($config.fsrsWeights.length &&
                            $config.fsrsWeights.every(
                                (n, i) => n.toFixed(4) === resp.weights[i].toFixed(4),
                            )) ||
                        resp.weights.length === 0
                    ) {
                        alert(tr.deckConfigFsrsParamsOptimal());
                    }
                    if (computeWeightsProgress) {
                        computeWeightsProgress.current = computeWeightsProgress.total;
                    }
                    $config.fsrsWeights = resp.weights;
                },
                (progress) => {
                    if (progress.value.case === "computeWeights") {
                        computeWeightsProgress = progress.value.value;
                    }
                },
            );
        } finally {
            computingWeights = false;
        }
    }

    async function checkWeights(): Promise<void> {
        if (checkingWeights) {
            await setWantsAbort({});
            return;
        }
        if (state.presetAssignmentsChanged()) {
            alert(tr.deckConfigPleaseSaveYourChangesFirst());
            return;
        }
        checkingWeights = true;
        computeWeightsProgress = undefined;
        try {
            await runWithBackendProgress(
                async () => {
                    const search = $config.weightSearch
                        ? $config.weightSearch
                        : defaultWeightSearch;
                    const resp = await evaluateWeights({
                        weights: $config.fsrsWeights,
                        search,
                        ignoreRevlogsBeforeMs: getIgnoreRevlogsBeforeMs(),
                    });
                    if (computeWeightsProgress) {
                        computeWeightsProgress.current = computeWeightsProgress.total;
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
                    if (progress.value.case === "computeWeights") {
                        computeWeightsProgress = progress.value.value;
                    }
                },
            );
        } finally {
            checkingWeights = false;
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
                    optimalRetentionRequest.weights = $config.fsrsWeights;
                    optimalRetentionRequest.search = `preset:"${state.getCurrentName()}" -is:suspended`;
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

    $: computeWeightsProgressString = renderWeightProgress(computeWeightsProgress);
    $: computeRetentionProgressString = renderRetentionProgress(
        computeRetentionProgress,
    );

    function renderWeightProgress(val: ComputeWeightsProgress | undefined): String {
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
    <WeightsInputRow
        bind:value={$config.fsrsWeights}
        defaultValue={[]}
        defaults={defaults.fsrsWeights}
    >
        <SettingTitle on:click={() => openHelpModal("modelWeights")}>
            {tr.deckConfigWeights()}
        </SettingTitle>
    </WeightsInputRow>

    <input
        bind:value={$config.weightSearch}
        placeholder={defaultWeightSearch}
        class="w-100 mb-1"
    />
    <DateInput bind:date={$config.ignoreRevlogsBeforeDate}>
        <SettingTitle on:click={() => openHelpModal("ignoreBefore")}>
            {tr.deckConfigIgnoreBefore()}
        </SettingTitle>
    </DateInput>
    <button
        class="btn {computingWeights ? 'btn-warning' : 'btn-primary'}"
        disabled={!computingWeights && computing}
        on:click={() => computeWeights()}
    >
        {#if computingWeights}
            {tr.actionsCancel()}
        {:else}
            {tr.deckConfigOptimizeButton()}
        {/if}
    </button>
    <button
        class="btn {checkingWeights ? 'btn-warning' : 'btn-primary'}"
        disabled={!checkingWeights && computing}
        on:click={() => checkWeights()}
    >
        {#if checkingWeights}
            {tr.actionsCancel()}
        {:else}
            {tr.deckConfigEvaluateButton()}
        {/if}
    </button>
    {#if computingWeights || checkingWeights}<div>
            {computeWeightsProgressString}
        </div>{/if}

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
        <summary>{tr.deckConfigComputeOptimalRetention()} (experimental)</summary>

        <SpinBoxRow
            bind:value={optimalRetentionRequest.daysToSimulate}
            defaultValue={365}
            min={1}
            max={3650}
        >
            <SettingTitle on:click={() => openHelpModal("computeOptimalRetention")}>
                Days to simulate
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
        <div>{computeRetentionProgressString}</div>
    </details>
</div>

<style>
</style>
