<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import {
        ComputeRetentionProgress,
        type ComputeWeightsProgress,
    } from "@tslib/anki/collection_pb";
    import { ComputeOptimalRetentionRequest } from "@tslib/anki/scheduler_pb";
    import {
        computeFsrsWeights,
        computeOptimalRetention,
        evaluateWeights,
        setWantsAbort,
    } from "@tslib/backend";
    import * as tr from "@tslib/ftl";
    import { runWithBackendProgress } from "@tslib/progress";
    import SwitchRow from "components/SwitchRow.svelte";

    import SettingTitle from "../components/SettingTitle.svelte";
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

    let computeWeightsProgress: ComputeWeightsProgress | undefined;
    let computingWeights = false;
    let checkingWeights = false;
    let computingRetention = false;
    let optimalRetention = 0;
    $: if ($presetName) {
        optimalRetention = 0;
    }
    $: computing = computingWeights || checkingWeights || computingRetention;
    $: defaultWeightSearch = `preset:"${state.getCurrentName()}"`;
    $: desiredRetentionWarning = getRetentionWarning($config.desiredRetention);
    $: retentionWarningClass = getRetentionWarningClass($config.desiredRetention);

    let computeRetentionProgress:
        | ComputeWeightsProgress
        | ComputeRetentionProgress
        | undefined;

    const optimalRetentionRequest = new ComputeOptimalRetentionRequest({
        deckSize: 10000,
        daysToSimulate: 365,
        maxMinutesOfStudyPerDay: 30,
        lossAversion: 2.5,
    });
    $: if (optimalRetentionRequest.daysToSimulate > 3650) {
        optimalRetentionRequest.daysToSimulate = 3650;
    }

    function getRetentionWarning(retention: number): string {
        const days = Math.round(9 * 100 * (1.0 / retention - 1.0));
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
        try {
            await runWithBackendProgress(
                async () => {
                    const resp = await computeFsrsWeights({
                        search: $config.weightSearch
                            ? $config.weightSearch
                            : defaultWeightSearch,
                    });
                    if (computeWeightsProgress) {
                        computeWeightsProgress.current = computeWeightsProgress.total;
                    }
                    if (resp.fsrsItems < 1000) {
                        alert(
                            tr.deckConfigMustHave1000Reviews({ count: resp.fsrsItems }),
                        );
                    } else {
                        $config.fsrsWeights = resp.weights;
                    }
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
        try {
            await runWithBackendProgress(
                async () => {
                    const search =
                        $config.weightSearch ?? `preset:"${state.getCurrentName()}"`;
                    const resp = await evaluateWeights({
                        weights: $config.fsrsWeights,
                        search,
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
        try {
            await runWithBackendProgress(
                async () => {
                    optimalRetentionRequest.maxInterval = $config.maximumReviewInterval;
                    optimalRetentionRequest.weights = $config.fsrsWeights;
                    optimalRetentionRequest.search = `preset:"${state.getCurrentName()}"`;
                    const resp = await computeOptimalRetention(optimalRetentionRequest);
                    optimalRetention = resp.optimalRetention;
                    if (computeRetentionProgress) {
                        computeRetentionProgress.current =
                            computeRetentionProgress.total;
                    }
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
        let pct = ((val.current / val.total) * 100).toFixed(1);
        pct = `${pct}%`;
        if (val instanceof ComputeRetentionProgress) {
            return pct;
        } else {
            return `${pct} of ${val.fsrsItems} reviews`;
        }
    }

    function renderRetentionProgress(
        val: ComputeRetentionProgress | undefined,
    ): String {
        if (!val || !val.total) {
            return "";
        }
        const pct = ((val.current / val.total) * 100).toFixed(0);
        return tr.deckConfigComplete({ num: pct });
    }

    function estimatedRetention(retention: number): String {
        if (!retention) {
            return "";
        }
        return tr.deckConfigEstimatedRetention({ num: retention.toFixed(2) });
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

<SpinBoxFloatRow
    bind:value={$config.sm2Retention}
    defaultValue={defaults.sm2Retention}
    min={0.5}
    max={1.0}
>
    <SettingTitle on:click={() => openHelpModal("sm2Retention")}>
        {tr.deckConfigSm2Retention()}
    </SettingTitle>
</SpinBoxFloatRow>

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
</div>

<div class="m-2">
    <SwitchRow bind:value={$config.rescheduleFsrsCards} defaultValue={false}>
        <SettingTitle on:click={() => openHelpModal("rescheduleCardsOnChange")}>
            {tr.deckConfigRescheduleCardsOnChange()}
        </SettingTitle>
    </SwitchRow>

    {#if $config.rescheduleFsrsCards}
        <Warning warning={tr.deckConfigRescheduleCardsWarning()} />
    {/if}
</div>

<div class="m-2">
    <details>
        <summary>{tr.deckConfigComputeOptimalWeights()}</summary>
        <input
            bind:value={$config.weightSearch}
            placeholder={defaultWeightSearch}
            class="w-100 mb-1"
        />
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
    </details>
</div>

<div class="m-2">
    <details>
        <summary>{tr.deckConfigComputeOptimalRetention()} (experimental)</summary>

        <SpinBoxRow
            bind:value={optimalRetentionRequest.deckSize}
            defaultValue={10000}
            min={100}
            max={99999}
        >
            <SettingTitle>Deck size</SettingTitle>
        </SpinBoxRow>

        <SpinBoxRow
            bind:value={optimalRetentionRequest.daysToSimulate}
            defaultValue={365}
            min={1}
            max={3650}
        >
            <SettingTitle>Days to simulate</SettingTitle>
        </SpinBoxRow>

        <SpinBoxRow
            bind:value={optimalRetentionRequest.maxMinutesOfStudyPerDay}
            defaultValue={30}
            min={1}
            max={1800}
        >
            <SettingTitle>Minutes study/day</SettingTitle>
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
        {/if}
        <div>{computeRetentionProgressString}</div>
    </details>
</div>

<style>
</style>
