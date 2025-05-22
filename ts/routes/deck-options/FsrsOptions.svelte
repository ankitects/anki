<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import {
        ComputeRetentionProgress,
        type ComputeParamsProgress,
    } from "@generated/anki/collection_pb";
    import { SimulateFsrsReviewRequest } from "@generated/anki/scheduler_pb";
    import {
        computeFsrsParams,
        evaluateParams,
        getRetentionWorkload,
        setWantsAbort,
    } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { runWithBackendProgress } from "@tslib/progress";

    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";

    import GlobalLabel from "./GlobalLabel.svelte";
    import { commitEditing, fsrsParams, type DeckOptionsState } from "./lib";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import Warning from "./Warning.svelte";
    import ParamsInputRow from "./ParamsInputRow.svelte";
    import ParamsSearchRow from "./ParamsSearchRow.svelte";
    import SimulatorModal from "./SimulatorModal.svelte";
    import {
        GetRetentionWorkloadRequest,
        UpdateDeckConfigsMode,
    } from "@generated/anki/deck_config_pb";

    export let state: DeckOptionsState;
    export let openHelpModal: (String) => void;
    export let onPresetChange: () => void;
    export let newlyEnabled = false;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const fsrsReschedule = state.fsrsReschedule;
    const daysSinceLastOptimization = state.daysSinceLastOptimization;

    $: lastOptimizationWarning =
        $daysSinceLastOptimization > 30 ? tr.deckConfigTimeToOptimize() : "";
    let desiredRetentionFocused = false;
    let desiredRetentionEverFocused = false;
    const startingDesiredRetention = $config.desiredRetention.toFixed(2);
    $: if (desiredRetentionFocused) {
        desiredRetentionEverFocused = true;
    }
    $: showDesiredRetentionTooltip = newlyEnabled || desiredRetentionEverFocused;

    let computeParamsProgress: ComputeParamsProgress | undefined;
    let computingParams = false;
    let checkingParams = false;

    $: computing = computingParams || checkingParams;
    $: defaultparamSearch = `preset:"${state.getCurrentNameForSearch()}" -is:suspended`;
    $: roundedRetention = Number($config.desiredRetention.toFixed(2));
    $: desiredRetentionWarning = getRetentionLongShortWarning(roundedRetention);
    $: desiredRetentionChangeInfo = showDesiredRetentionTooltip
        ? getRetentionChangeInfo(roundedRetention, fsrsParams($config))
        : Promise.resolve("");
    $: retentionWarningClass = getRetentionWarningClass(roundedRetention);

    $: newCardsIgnoreReviewLimit = state.newCardsIgnoreReviewLimit;

    $: simulateFsrsRequest = new SimulateFsrsReviewRequest({
        params: fsrsParams($config),
        desiredRetention: $config.desiredRetention,
        newLimit: $config.newPerDay,
        reviewLimit: $config.reviewsPerDay,
        maxInterval: $config.maximumReviewInterval,
        search: `preset:"${state.getCurrentNameForSearch()}" -is:suspended`,
        newCardsIgnoreReviewLimit: $newCardsIgnoreReviewLimit,
        easyDaysPercentages: $config.easyDaysPercentages,
        reviewOrder: $config.reviewOrder,
    });

    const desiredRetentionLowThreshold = 0.8;
    const desiredRetentionHighThreshold = 0.95;

    function getRetentionLongShortWarning(retention: number) {
        if (retention < desiredRetentionLowThreshold) {
            return tr.deckConfigDesiredRetentionTooLow();
        } else if (retention > desiredRetentionHighThreshold) {
            return tr.deckConfigDesiredRetentionTooHigh();
        } else {
            return "";
        }
    }

    async function getRetentionChangeInfo(
        retention: number,
        params: number[],
    ): Promise<string> {
        if (+startingDesiredRetention == roundedRetention) {
            return tr.deckConfigWorkloadPercentageUnchanged();
        }
        const request = new GetRetentionWorkloadRequest({
            w: params.length > 0 ? params : defaults.fsrsParams6,
            before: +startingDesiredRetention,
            after: retention,
        });
        const resp = await getRetentionWorkload(request);
        const percent = (resp.factor - 1) * 100;
        console.log({resp, percent})
        if (percent > 0) {
            return tr.deckConfigWorkloadPercentageIncrease({
                percent,
            });
        } else {
            return tr.deckConfigWorkloadPercentageDecrease({
                percent: -percent,
            });
        }
    }

    function getRetentionWarningClass(retention: number): string {
        if (retention < 0.7 || retention > 0.97) {
            return "alert-danger";
        } else if (
            retention < desiredRetentionLowThreshold ||
            retention > desiredRetentionHighThreshold
        ) {
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
                    const RelearningSteps = $config.relearnSteps;
                    let numOfRelearningStepsInDay = 0;
                    let accumulatedTime = 0;
                    for (let i = 0; i < RelearningSteps.length; i++) {
                        accumulatedTime += RelearningSteps[i];
                        if (accumulatedTime >= 1440) {
                            break;
                        }
                        numOfRelearningStepsInDay++;
                    }
                    const resp = await computeFsrsParams({
                        search: $config.paramSearch
                            ? $config.paramSearch
                            : defaultparamSearch,
                        ignoreRevlogsBeforeMs: getIgnoreRevlogsBeforeMs(),
                        currentParams: params,
                        numOfRelearningSteps: numOfRelearningStepsInDay,
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
                        $config.fsrsParams6 = resp.params;
                        showDesiredRetentionTooltip = true;
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

    $: computeParamsProgressString = renderWeightProgress(computeParamsProgress);
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

    async function computeAllParams(): Promise<void> {
        await commitEditing();
        state.save(UpdateDeckConfigsMode.COMPUTE_ALL_PARAMS);
    }

    let showSimulator = false;
</script>

<SpinBoxFloatRow
    bind:value={$config.desiredRetention}
    defaultValue={defaults.desiredRetention}
    min={0.7}
    max={0.99}
    percentage={true}
    bind:focused={desiredRetentionFocused}
>
    <SettingTitle on:click={() => openHelpModal("desiredRetention")}>
        {tr.deckConfigDesiredRetention()}
    </SettingTitle>
</SpinBoxFloatRow>

{#await desiredRetentionChangeInfo}
    <Warning warning={tr.qtMiscProcessing()} className={"alert-info"} />
{:then desiredRetentionChangeInfo}
    <Warning warning={desiredRetentionChangeInfo} className={"alert-info"} />
{/await}
<Warning warning={desiredRetentionWarning} className={retentionWarningClass} />

<div class="ms-1 me-1">
    <ParamsInputRow
        bind:value={$config.fsrsParams6}
        defaultValue={[]}
        defaults={defaults.fsrsParams6}
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
</div>

<div class="m-1">
    <Warning warning={lastOptimizationWarning} className="alert-warning" />

    <button class="btn btn-primary" on:click={() => computeAllParams()}>
        {tr.deckConfigSaveAndOptimize()}
    </button>
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
    <button class="btn btn-primary" on:click={() => (showSimulator = true)}>
        {tr.deckConfigFsrsSimulatorExperimental()}
    </button>
</div>

<SimulatorModal
    bind:shown={showSimulator}
    {state}
    {simulateFsrsRequest}
    {computing}
    {openHelpModal}
    {onPresetChange}
/>

<style>
    .btn {
        margin-bottom: 0.375rem;
    }
</style>
