<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import {
        Progress_ComputeRetention,
        type Progress_ComputeWeights,
    } from "@tslib/anki/collection_pb";
    import { ComputeOptimalRetentionRequest } from "@tslib/anki/scheduler_pb";
    import {
        computeFsrsWeights,
        computeOptimalRetention,
        evaluateWeights,
        setWantsAbort,
    } from "@tslib/backend";
    import { runWithBackendProgress } from "@tslib/progress";
    import TitledContainer from "components/TitledContainer.svelte";

    import ConfigInput from "./ConfigInput.svelte";
    import type { DeckOptionsState } from "./lib";
    import RevertButton from "./RevertButton.svelte";
    import SettingTitle from "./SettingTitle.svelte";
    import WeightsInputRow from "./WeightsInputRow.svelte";

    export let state: DeckOptionsState;

    const config = state.currentConfig;

    let computeWeightsProgress: Progress_ComputeWeights | undefined;
    let customSearch = "";
    let computing = false;

    let computeRetentionProgress:
        | Progress_ComputeWeights
        | Progress_ComputeRetention
        | undefined;

    const computeOptimalRequest = new ComputeOptimalRetentionRequest({
        deckSize: 10000,
        daysToSimulate: 365,
        maxSecondsOfStudyPerDay: 1800,
        maxInterval: 36500,
        recallSecs: 10,
        forgetSecs: 50,
        learnSecs: 20,
    });

    async function computeWeights(): Promise<void> {
        if (computing) {
            await setWantsAbort({});
            return;
        }
        computing = true;
        try {
            await runWithBackendProgress(
                async () => {
                    const search = customSearch ?? `preset:"${state.getCurrentName()}"`;
                    const resp = await computeFsrsWeights({
                        search,
                    });
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
            computing = false;
        }
    }

    async function checkWeights(): Promise<void> {
        const search = customSearch ?? `preset:"${state.getCurrentName()}"`;
        const resp = await evaluateWeights({
            weights: $config.fsrsWeights,
            search,
        });
        alert(JSON.stringify(resp));
    }

    async function computeRetention(): Promise<void> {
        if (computing) {
            await setWantsAbort({});
            return;
        }
        computing = true;
        try {
            await runWithBackendProgress(
                async () => {
                    computeOptimalRequest.weights = $config.fsrsWeights;
                    const resp = await computeOptimalRetention(computeOptimalRequest);
                    $config.desiredRetention = resp.optimalRetention;
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
            computing = false;
        }
    }

    $: computeWeightsProgressString = renderWeightProgress(computeWeightsProgress);
    $: computeRetentionProgressString = renderRetentionProgress(
        computeRetentionProgress,
    );

    function renderWeightProgress(val: Progress_ComputeWeights | undefined): String {
        if (!val || !val.total) {
            return "";
        }
        let pct = ((val.current / val.total) * 100).toFixed(2);
        pct = `${pct}%`;
        if (val instanceof Progress_ComputeRetention) {
            return pct;
        } else {
            return `${pct} of ${val.revlogEntries} reviews`;
        }
    }

    function renderRetentionProgress(
        val: Progress_ComputeRetention | undefined,
    ): String {
        if (!val || !val.total) {
            return "";
        }
        const pct = ((val.current / val.total) * 100).toFixed(2);
        return `${pct}%`;
    }
</script>

<TitledContainer title={"FSRS"}>
    <WeightsInputRow
        bind:value={$config.fsrsWeights}
        defaultValue={[
            0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05,
            0.34, 1.26, 0.29, 2.61,
        ]}
    >
        <SettingTitle>Weights</SettingTitle>
    </WeightsInputRow>
    <div>Optimal retention</div>

    <ConfigInput>
        <input type="number" bind:value={$config.desiredRetention} />
        <RevertButton
            slot="revert"
            bind:value={$config.desiredRetention}
            defaultValue={0.9}
        />
    </ConfigInput>

    <div class="mb-3" />

    <div class="bordered">
        <b>Optimize weights</b>
        <br />
        <input
            bind:value={customSearch}
            placeholder="Search; leave blank for all cards using this preset"
            class="w-100 mb-1"
        />
        <button
            class="btn {computing ? 'btn-warning' : 'btn-primary'}"
            on:click={() => computeWeights()}
        >
            {#if computing}
                Cancel
            {:else}
                Compute
            {/if}
        </button>
        <button
            class="btn {computing ? 'btn-warning' : 'btn-primary'}"
            on:click={() => checkWeights()}
        >
            {#if computing}
                Cancel
            {:else}
                Check
            {/if}
        </button>
        <div>{computeWeightsProgressString}</div>
    </div>

    <div class="bordered">
        <b>Calculate optimal retention</b>
        <br />

        Deck size:
        <br />
        <input type="number" bind:value={computeOptimalRequest.deckSize} />
        <br />

        Days to simulate
        <br />
        <input type="number" bind:value={computeOptimalRequest.daysToSimulate} />
        <br />

        Max seconds of study per day:
        <br />
        <input
            type="number"
            bind:value={computeOptimalRequest.maxSecondsOfStudyPerDay}
        />
        <br />

        Maximum interval:
        <br />
        <input type="number" bind:value={computeOptimalRequest.maxInterval} />
        <br />

        Seconds to recall a card:
        <br />
        <input type="number" bind:value={computeOptimalRequest.recallSecs} />
        <br />

        Seconds to forget a card:
        <br />
        <input type="number" bind:value={computeOptimalRequest.forgetSecs} />
        <br />

        Seconds to learn a card:
        <br />
        <input type="number" bind:value={computeOptimalRequest.learnSecs} />
        <br />

        <button
            class="btn {computing ? 'btn-warning' : 'btn-primary'}"
            on:click={() => computeRetention()}
        >
            {#if computing}
                Cancel
            {:else}
                Compute
            {/if}
        </button>
        <div>{computeRetentionProgressString}</div>
    </div>
</TitledContainer>

<style>
    .bordered {
        border: 1px solid #777;
        padding: 1em;
        margin-bottom: 2px;
    }
</style>
