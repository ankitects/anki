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
        evaluateParamsLegacy,
        getRetentionWorkload,
        setWantsAbort,
    } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { runWithBackendProgress } from "@tslib/progress";

    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";

    import GlobalLabel from "./GlobalLabel.svelte";
    import { commitEditing, fsrsParams, type DeckOptionsState, ValueTab } from "./lib";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import Warning from "./Warning.svelte";
    import ParamsInputRow from "./ParamsInputRow.svelte";
    import ParamsSearchRow from "./ParamsSearchRow.svelte";
    import SimulatorModal from "./SimulatorModal.svelte";
    import {
        GetRetentionWorkloadRequest,
        type GetRetentionWorkloadResponse,
        UpdateDeckConfigsMode,
    } from "@generated/anki/deck_config_pb";
    import type Modal from "bootstrap/js/dist/modal";
    import TabbedValue from "./TabbedValue.svelte";
    import Item from "$lib/components/Item.svelte";
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";

    export let state: DeckOptionsState;
    export let openHelpModal: (String) => void;
    export let onPresetChange: () => void;
    export let newlyEnabled = false;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const fsrsReschedule = state.fsrsReschedule;
    const daysSinceLastOptimization = state.daysSinceLastOptimization;
    const limits = state.deckLimits;

    $: lastOptimizationWarning =
        $daysSinceLastOptimization > 30 ? tr.deckConfigTimeToOptimize() : "";
    let desiredRetentionFocused = false;
    let desiredRetentionEverFocused = false;
    let optimized = false;
    $: if (desiredRetentionFocused) {
        desiredRetentionEverFocused = true;
    }
    $: showDesiredRetentionTooltip =
        newlyEnabled || desiredRetentionEverFocused || optimized;

    let computeParamsProgress: ComputeParamsProgress | undefined;
    let computingParams = false;
    let checkingParams = false;

    const healthCheck = state.fsrsHealthCheck;

    $: computing = computingParams || checkingParams;
    $: defaultparamSearch = `preset:"${state.getCurrentNameForSearch()}" -is:suspended`;
    $: roundedRetention = Number(effectiveDesiredRetention.toFixed(2));
    $: desiredRetentionWarning = getRetentionLongShortWarning(roundedRetention);

    let desiredRetentionChangeInfo = "";
    $: if (showDesiredRetentionTooltip) {
        getRetentionChangeInfo(roundedRetention, fsrsParams($config));
    }

    $: retentionWarningClass = getRetentionWarningClass(roundedRetention);

    $: newCardsIgnoreReviewLimit = state.newCardsIgnoreReviewLimit;

    // Create tabs for desired retention
    const desiredRetentionTabs: ValueTab[] = [
        new ValueTab(
            tr.deckConfigSharedPreset(),
            $config.desiredRetention,
            (value) => ($config.desiredRetention = value!),
            $config.desiredRetention,
            null,
        ),
        new ValueTab(
            tr.deckConfigDeckOnly(),
            $limits.desiredRetention ?? null,
            (value) => ($limits.desiredRetention = value ?? undefined),
            null,
            null,
        ),
    ];

    // Get the effective desired retention value (deck-specific if set, otherwise config default)
    let effectiveDesiredRetention =
        $limits.desiredRetention ?? $config.desiredRetention;
    const startingDesiredRetention = effectiveDesiredRetention.toFixed(2);

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
        historicalRetention: $config.historicalRetention,
    });

    const DESIRED_RETENTION_LOW_THRESHOLD = 0.8;
    const DESIRED_RETENTION_HIGH_THRESHOLD = 0.95;

    function getRetentionLongShortWarning(retention: number) {
        if (retention < DESIRED_RETENTION_LOW_THRESHOLD) {
            return tr.deckConfigDesiredRetentionTooLow();
        } else if (retention > DESIRED_RETENTION_HIGH_THRESHOLD) {
            return tr.deckConfigDesiredRetentionTooHigh();
        } else {
            return "";
        }
    }

    let retentionWorkloadInfo: undefined | Promise<GetRetentionWorkloadResponse> =
        undefined;
    let lastParams = [...fsrsParams($config)];

    async function getRetentionChangeInfo(retention: number, params: number[]) {
        if (+startingDesiredRetention == roundedRetention) {
            desiredRetentionChangeInfo = tr.deckConfigWorkloadFactorUnchanged();
            return;
        }
        if (
            // If the cache is empty and a request has not yet been made to fill it
            !retentionWorkloadInfo ||
            // If the parameters have been changed
            lastParams.toString() !== params.toString()
        ) {
            const request = new GetRetentionWorkloadRequest({
                w: params,
                search: defaultparamSearch,
            });
            lastParams = [...params];
            retentionWorkloadInfo = getRetentionWorkload(request);
        }

        const previous = +startingDesiredRetention * 100;
        const after = retention * 100;
        const resp = await retentionWorkloadInfo;
        const factor = resp.costs[after] / resp.costs[previous];

        desiredRetentionChangeInfo = tr.deckConfigWorkloadFactorChange({
            factor: factor.toFixed(2),
            previousDr: previous.toString(),
        });
    }

    function getRetentionWarningClass(retention: number): string {
        if (retention < 0.7 || retention > 0.97) {
            return "alert-danger";
        } else if (
            retention < DESIRED_RETENTION_LOW_THRESHOLD ||
            retention > DESIRED_RETENTION_HIGH_THRESHOLD
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
                        healthCheck: $healthCheck,
                    });

                    const already_optimal =
                        (params.length &&
                            params.every(
                                (n, i) => n.toFixed(4) === resp.params[i].toFixed(4),
                            )) ||
                        resp.params.length === 0;

                    if (resp.healthCheckPassed !== undefined) {
                        if (resp.healthCheckPassed) {
                            setTimeout(() => alert(tr.deckConfigFsrsGoodFit()), 200);
                        } else {
                            setTimeout(
                                () => alert(tr.deckConfigFsrsBadFitWarning()),
                                200,
                            );
                        }
                    } else if (already_optimal) {
                        const msg = resp.fsrsItems
                            ? tr.deckConfigFsrsParamsOptimal()
                            : tr.deckConfigFsrsParamsNoReviews();
                        setTimeout(() => alert(msg), 200);
                    }
                    if (!already_optimal) {
                        $config.fsrsParams6 = resp.params;
                        setTimeout(() => {
                            optimized = true;
                        }, 201);
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
                    const resp = await evaluateParamsLegacy({
                        search,
                        ignoreRevlogsBeforeMs: getIgnoreRevlogsBeforeMs(),
                        params: fsrsParams($config),
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
            if (val.current === val.total) {
                return tr.deckConfigCheckingForImprovement();
            } else {
                return tr.deckConfigPercentOfReviews({ pct, reviews: val.reviews });
            }
        }
    }

    async function computeAllParams(): Promise<void> {
        await commitEditing();
        state.save(UpdateDeckConfigsMode.COMPUTE_ALL_PARAMS);
    }

    let simulatorModal: Modal;
    let workloadModal: Modal;
</script>

<DynamicallySlottable slotHost={Item} api={{}}>
    <Item>
        <SpinBoxFloatRow
            bind:value={effectiveDesiredRetention}
            defaultValue={defaults.desiredRetention}
            min={0.7}
            max={0.99}
            percentage={true}
            bind:focused={desiredRetentionFocused}
        >
            <TabbedValue
                slot="tabs"
                tabs={desiredRetentionTabs}
                bind:value={effectiveDesiredRetention}
            />
            <SettingTitle on:click={() => openHelpModal("desiredRetention")}>
                {tr.deckConfigDesiredRetention()}
            </SettingTitle>
        </SpinBoxFloatRow>
    </Item>
</DynamicallySlottable>

<button
    class="btn btn-primary"
    on:click={() => {
        simulateFsrsRequest.reviewLimit = 9999;
        workloadModal?.show();
    }}
>
    {tr.deckConfigFsrsDesiredRetentionHelpMeDecideExperimental()}
</button>

<Warning warning={desiredRetentionChangeInfo} className={"alert-info two-line"} />
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

    <SwitchRow bind:value={$fsrsReschedule} defaultValue={false}>
        <SettingTitle on:click={() => openHelpModal("rescheduleCardsOnChange")}>
            <GlobalLabel title={tr.deckConfigRescheduleCardsOnChange()} />
        </SettingTitle>
    </SwitchRow>

    {#if $fsrsReschedule}
        <Warning warning={tr.deckConfigRescheduleCardsWarning()} />
    {/if}

    <SwitchRow bind:value={$healthCheck} defaultValue={false}>
        <SettingTitle on:click={() => openHelpModal("healthCheck")}>
            <GlobalLabel
                title={tr.deckConfigSlowSuffix({ text: tr.deckConfigHealthCheck() })}
            />
        </SettingTitle>
    </SwitchRow>

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
    {#if state.legacyEvaluate}
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
    {/if}
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

<hr />

<div class="m-1">
    <button class="btn btn-primary" on:click={() => simulatorModal?.show()}>
        {tr.deckConfigFsrsSimulatorExperimental()}
    </button>
</div>

<SimulatorModal
    bind:modal={simulatorModal}
    {state}
    {simulateFsrsRequest}
    {computing}
    {openHelpModal}
    {onPresetChange}
/>

<SimulatorModal
    bind:modal={workloadModal}
    workload
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

    :global(.two-line) {
        white-space: pre-wrap;
        min-height: calc(2ch + 30px);
        box-sizing: content-box;
        display: flex;
        align-content: center;
        flex-wrap: wrap;
    }

    hr {
        border-top: 1px solid var(--border);
        opacity: 1;
    }
</style>
