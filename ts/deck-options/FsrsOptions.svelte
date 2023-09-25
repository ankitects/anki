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

    import SettingTitle from "../components/SettingTitle.svelte";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import WeightsInputRow from "./WeightsInputRow.svelte";

    export let state: DeckOptionsState;

    const presetName = state.currentPresetName;

    const config = state.currentConfig;
    const defaults = state.defaults;

    let computeWeightsProgress: ComputeWeightsProgress | undefined;
    let computingWeights = false;
    let checkingWeights = false;
    let computingRetention = false;
    $: computing = computingWeights || checkingWeights || computingRetention;
    $: customSearch = `preset:"${$presetName}"`;

    let computeRetentionProgress:
        | ComputeWeightsProgress
        | ComputeRetentionProgress
        | undefined;

    const optimalRetentionRequest = new ComputeOptimalRetentionRequest({
        deckSize: 10000,
        daysToSimulate: 365,
        maxMinutesOfStudyPerDay: 30,
    });
    $: if (optimalRetentionRequest.daysToSimulate > 3650) {
        optimalRetentionRequest.daysToSimulate = 3650;
    }
    async function computeWeights(): Promise<void> {
        if (computingWeights) {
            await setWantsAbort({});
            return;
        }
        computingWeights = true;
        try {
            await runWithBackendProgress(
                async () => {
                    const resp = await computeFsrsWeights({
                        search: customSearch,
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
        checkingWeights = true;
        try {
            await runWithBackendProgress(
                async () => {
                    const search = customSearch ?? `preset:"${state.getCurrentName()}"`;
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
                                `Log loss: ${resp.logLoss.toFixed(
                                    3,
                                )}, RMSE(bins): ${resp.rmseBins.toFixed(
                                    3,
                                )}. ${tr.deckConfigSmallerIsBetter()}`,
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
        computingRetention = true;
        try {
            await runWithBackendProgress(
                async () => {
                    optimalRetentionRequest.maxInterval = $config.maximumReviewInterval;
                    optimalRetentionRequest.weights = $config.fsrsWeights;
                    optimalRetentionRequest.search = `preset:"${state.getCurrentName()}"`;
                    const resp = await computeOptimalRetention(optimalRetentionRequest);
                    alert(
                        tr.deckConfigYourOptimalRetention({
                            num: resp.optimalRetention,
                        }),
                    );
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
        let pct = ((val.current / val.total) * 100).toFixed(2);
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
        const pct = ((val.current / val.total) * 100).toFixed(2);
        return `${pct}%`;
    }
</script>

<SpinBoxFloatRow
    bind:value={$config.desiredRetention}
    defaultValue={defaults.desiredRetention}
    min={0.8}
    max={0.97}
>
    <SettingTitle>
        {tr.deckConfigDesiredRetention()}
    </SettingTitle>
</SpinBoxFloatRow>

<div class="ms-1 me-1">
    <WeightsInputRow bind:value={$config.fsrsWeights} defaultValue={[]}>
        <SettingTitle>{tr.deckConfigWeights()}</SettingTitle>
    </WeightsInputRow>
</div>

<div class="m-2">
    <details>
        <summary>{tr.deckConfigComputeOptimalWeights()}</summary>
        <input bind:value={customSearch} class="w-100 mb-1" />
        <button
            class="btn {computingWeights ? 'btn-warning' : 'btn-primary'}"
            disabled={!computingWeights && computing}
            on:click={() => computeWeights()}
        >
            {#if computingWeights}
                {tr.actionsCancel()}
            {:else}
                {tr.deckConfigComputeButton()}
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
                {tr.deckConfigAnalyzeButton()}
            {/if}
        </button>
        {#if checkingWeights}<div>{computeWeightsProgressString}</div>{/if}
    </details>
</div>

<div class="m-2">
    <details>
        <summary>{tr.deckConfigComputeOptimalRetention()}</summary>

        Deck size:
        <br />
        <input type="number" bind:value={optimalRetentionRequest.deckSize} />
        <br />

        Days to simulate
        <br />
        <input type="number" bind:value={optimalRetentionRequest.daysToSimulate} />
        <br />

        Target minutes of study per day:
        <br />
        <input
            type="number"
            bind:value={optimalRetentionRequest.maxMinutesOfStudyPerDay}
        />
        <br />

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
        <div>{computeRetentionProgressString}</div>
    </details>
</div>

<style>
</style>
