<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Modal from "bootstrap/js/dist/modal";
    import { getContext, onDestroy, onMount } from "svelte";
    import * as tr from "@generated/ftl";

    import { modalsKey } from "$lib/components/context-keys";
    import { registerModalClosingHandler } from "$lib/sveltelib/modal-closing";
    import { pageTheme } from "$lib/sveltelib/theme";
    import type { HistoryEntry } from "./types";
    import { searchInBrowser } from "@generated/backend";

    export const modalKey: string = Math.random().toString(36).substring(2);
    export let history: HistoryEntry[] = [];

    const modals = getContext<Map<string, Modal>>(modalsKey);

    let modalRef: HTMLDivElement;
    let modal: Modal;

    function onCancelClicked(): void {
        modal.hide();
    }

    function onShown(): void {
        setModalOpen(true);
    }

    function onHidden() {
        setModalOpen(false);
    }

    function onEntryClick(entry: HistoryEntry): void {
        searchInBrowser({
            filter: {
                case: "nids",
                value: { ids: [entry.noteId] },
            },
        });
        modal.hide();
    }

    const { set: setModalOpen, remove: removeModalClosingHandler } =
        registerModalClosingHandler(onCancelClicked);

    onMount(() => {
        modalRef.addEventListener("shown.bs.modal", onShown);
        modalRef.addEventListener("hidden.bs.modal", onHidden);
        modal = new Modal(modalRef, { keyboard: false });
        modals.set(modalKey, modal);
    });

    onDestroy(() => {
        removeModalClosingHandler();
        modalRef.removeEventListener("shown.bs.modal", onShown);
        modalRef.removeEventListener("hidden.bs.modal", onHidden);
    });
</script>

<div
    bind:this={modalRef}
    class="modal fade"
    class:nightMode={$pageTheme.isDark}
    tabindex="-1"
    aria-labelledby="modalLabel"
    aria-hidden="true"
>
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="modalLabel">{tr.addingHistory()}</h5>
                <button
                    type="button"
                    class="btn-close"
                    data-bs-dismiss="modal"
                    aria-label="Close"
                ></button>
            </div>
            <div class="modal-body">
                <ul class="history-list">
                    {#each history as entry}
                        <li>
                            <button
                                type="button"
                                class="history-entry"
                                on:click={() => onEntryClick(entry)}
                            >
                                {entry.text}
                            </button>
                        </li>
                    {/each}
                </ul>
            </div>
            <div class="modal-footer">
                <button
                    type="button"
                    class="btn btn-secondary"
                    on:click={onCancelClicked}
                >
                    Cancel
                </button>
            </div>
        </div>
    </div>
</div>

<style lang="scss">
    .modal {
        --link-color: #007bff;
        --canvas-elevated-hover: rgba(0, 0, 0, 0.05);
    }

    .nightMode.modal {
        --link-color: #4dabf7;
        --canvas-elevated-hover: rgba(255, 255, 255, 0.1): ;
    }

    .nightMode .modal-content {
        background-color: var(--canvas);
        color: var(--fg);
    }

    .nightMode .btn-close {
        filter: invert(1) grayscale(100%) brightness(200%);
    }

    .history-list {
        list-style: none;
        padding: 0;
        margin: 0;
        max-height: 400px;
        overflow-y: auto;
    }

    .history-list li {
        margin-bottom: 8px;
    }

    .history-list li:last-child {
        margin-bottom: 0;
    }

    .history-entry {
        display: block;
        width: 100%;
        padding: 12px 16px;
        background-color: var(--canvas-elevated);
        border: 1px solid var(--border);
        border-radius: 6px;
        transition: all 0.2s ease;
        font-size: 14px;
        line-height: 1.4;
        word-break: break-word;
        text-align: left;
        color: var(--fg);
        text-decoration: none;
        cursor: pointer;
        position: relative;
    }

    .history-entry::after {
        content: "";
        position: absolute;
        bottom: 8px;
        left: 16px;
        right: 16px;
        height: 1px;
        background-color: var(--link-color);
        opacity: 0;
        transition: opacity 0.2s ease;
    }

    .history-entry:hover {
        background-color: var(--canvas-elevated-hover);
        border-color: var(--border-strong);
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
        color: var(--link-color);
    }

    .history-entry:hover::after {
        opacity: 0.6;
    }

    .history-entry:active {
        transform: translateY(0px);
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
    }

    .history-entry:focus {
        outline: 2px solid var(--link-color);
        outline-offset: 2px;
    }

    .nightMode .history-entry {
        background-color: var(--canvas-elevated);
        border-color: var(--border, rgba(255, 255, 255, 0.15));
        color: var(--fg);
    }

    .nightMode .history-entry:hover {
        border-color: var(--border-strong, rgba(255, 255, 255, 0.25));
        color: var(--link-color);
    }
</style>
