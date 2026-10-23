<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Modal from "$lib/components/Modal.svelte";
    import * as tr from "@generated/ftl";
    import type { SimulateRescheduleResponse } from "@generated/anki/deck_config_pb";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let stats: SimulateRescheduleResponse | undefined = undefined;
    export let desiredRetention: number = 0.9;
    export let onConfirm: () => Promise<void>;

    let modal: Modal;
    export let modalKey: string = "reschedule-recap-modal";
    let processing = false;

    export function show(): void {
        modal?.show();
    }

    export function hide(): void {
        modal?.hide();
    }

    async function onConfirmClicked(): Promise<void> {
        if (processing) {
            return;
        }
        processing = true;
        try {
            await onConfirm();
            modal.hide();
        } finally {
            processing = false;
        }
    }
</script>

<Modal bind:this={modal} bind:modalKey dialogClass="modal-lg">
    <div slot="header" class="modal-header">
        <h5 class="modal-title" id="modalLabel">
            {tr.deckConfigRescheduleNoAddedWorkloadTitle()}
        </h5>
        <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
        ></button>
    </div>

    <div slot="body" class="modal-body">
        {#if stats}
            <div class="preset-badge mb-3">
                {tr.deckConfigReschedulePresetInfo({
                    preset: state.getCurrentName(),
                    retention: (desiredRetention * 100).toFixed(1),
                })}
            </div>

            <div class="rule-box mb-3">
                <div class="rule-title">
                    {tr.deckConfigRescheduleNoAddedWorkloadRuleTitle()}
                </div>
                <div class="rule-desc">
                    {tr.deckConfigRescheduleNoAddedWorkloadRuleDesc()}
                </div>
            </div>

            <div class="metrics-card mb-3">
                <div class="metric-row total-row">
                    <span class="metric-label">
                        {tr.deckConfigRescheduleTotalExamined({
                            count: stats.totalExamined,
                        })}
                    </span>
                    <span class="metric-val">{stats.totalExamined}</span>
                </div>

                <hr class="metric-divider" />

                <div class="metric-row bold-row text-success-emphasis">
                    <span class="metric-label">
                        {tr.deckConfigRescheduleRescheduledTotal({
                            count: stats.rescheduledTotal,
                        })}
                    </span>
                    <span class="metric-val">{stats.rescheduledTotal}</span>
                </div>
                <div class="metric-row sub-row">
                    <span class="metric-label">
                        {tr.deckConfigRescheduleFuturePushed({
                            count: stats.futurePushed,
                        })}
                    </span>
                    <span class="metric-val">{stats.futurePushed}</span>
                </div>
                <div class="metric-row sub-row">
                    <span class="metric-label">
                        {tr.deckConfigRescheduleTodayPostponed({
                            count: stats.todayPostponed,
                        })}
                    </span>
                    <span class="metric-val">{stats.todayPostponed}</span>
                </div>

                <hr class="metric-divider" />

                <div class="metric-row bold-row">
                    <span class="metric-label">
                        {tr.deckConfigReschedulePreservedTotal({
                            count: stats.preservedTotal,
                        })}
                    </span>
                    <span class="metric-val">{stats.preservedTotal}</span>
                </div>
                <div class="metric-row sub-row">
                    <span class="metric-label">
                        {tr.deckConfigReschedulePreservedCloser({
                            count: stats.preservedCloser,
                        })}
                    </span>
                    <span class="metric-val">{stats.preservedCloser}</span>
                </div>
                <div class="metric-row sub-row">
                    <span class="metric-label">
                        {tr.deckConfigReschedulePreservedToday({
                            count: stats.preservedToday,
                        })}
                    </span>
                    <span class="metric-val">{stats.preservedToday}</span>
                </div>
            </div>

            <div class="impact-box">
                <div class="impact-title">
                    {tr.deckConfigRescheduleImpactTitle()}
                </div>
                <div class="impact-desc">
                    {#if stats.todayPostponed > 0}
                        <div>
                            {tr.deckConfigRescheduleImpactReduced({
                                before: String(stats.workloadTodayBefore),
                                after: String(stats.workloadTodayAfter),
                                count: stats.todayPostponed,
                            })}
                        </div>
                    {:else}
                        <div>
                            {tr.deckConfigRescheduleImpactUnchanged({
                                count: stats.workloadTodayBefore,
                            })}
                        </div>
                    {/if}
                    <div class="text-muted small mt-1">
                        {tr.deckConfigRescheduleImpactGuarantee()}
                    </div>
                </div>
            </div>
        {:else}
            <div class="p-3 text-center text-muted">
                {tr.actionsProcessing()}
            </div>
        {/if}
    </div>

    <div slot="footer" class="modal-footer">
        <button
            type="button"
            class="btn btn-secondary"
            on:click={modal.cancelHandler}
            disabled={processing}
        >
            {tr.actionsCancel()}
        </button>
        <button
            type="button"
            class="btn btn-primary"
            on:click={onConfirmClicked}
            disabled={processing || !stats || stats.rescheduledTotal === 0}
        >
            {#if processing}
                {tr.actionsProcessing()}
            {:else}
                {tr.deckConfigRescheduleConfirmButton()}
            {/if}
        </button>
    </div>
</Modal>

<style lang="scss">
    .preset-badge {
        font-size: 0.875rem;
        color: var(--subtle-fg, #6c757d);
        font-weight: 500;
    }

    .rule-box {
        border: 1px solid var(--border, rgba(128, 128, 128, 0.25));
        border-radius: 6px;
        padding: 0.75rem 1rem;
        background: var(--hover, rgba(128, 128, 128, 0.05));

        .rule-title {
            font-weight: 600;
            font-size: 0.8125rem;
            margin-bottom: 0.25rem;
        }

        .rule-desc {
            font-size: 0.8125rem;
            line-height: 1.4;
            color: var(--subtle-fg, #6c757d);
        }
    }

    .metrics-card {
        border: 1px solid var(--border, rgba(128, 128, 128, 0.2));
        border-radius: 6px;
        padding: 0.875rem 1rem;

        .metric-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 0.875rem;
            padding: 0.2rem 0;

            .metric-val {
                font-variant-numeric: tabular-nums;
            }

            &.total-row {
                font-weight: 600;
            }

            &.bold-row {
                font-weight: 600;
            }

            &.sub-row {
                font-size: 0.8125rem;
                color: var(--subtle-fg, #6c757d);
                padding-left: 1rem;
            }
        }

        .metric-divider {
            margin: 0.4rem 0;
            border-top: 1px solid var(--border, rgba(128, 128, 128, 0.15));
            opacity: 0.6;
        }
    }

    .impact-box {
        border: 1px solid var(--border, rgba(128, 128, 128, 0.2));
        border-radius: 6px;
        padding: 0.75rem 1rem;
        background: var(--hover, rgba(128, 128, 128, 0.03));

        .impact-title {
            font-weight: 600;
            font-size: 0.8125rem;
            margin-bottom: 0.35rem;
        }

        .impact-desc {
            font-size: 0.8125rem;
            line-height: 1.4;
        }
    }
</style>
