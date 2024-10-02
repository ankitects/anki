<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Modal from "bootstrap/js/dist/modal";
    import { getContext, onDestroy, onMount } from "svelte";

    import { modalsKey } from "$lib/components/context-keys";
    import { registerModalClosingHandler } from "$lib/sveltelib/modal-closing";
    import { pageTheme } from "$lib/sveltelib/theme";

    export let title: string;
    export let prompt: string;
    export let initialValue = "";
    export let onOk: (text: string) => void;
    $: value = initialValue;

    export const modalKey: string = Math.random().toString(36).substring(2);

    const modals = getContext<Map<string, Modal>>(modalsKey);

    let modalRef: HTMLDivElement;
    let modal: Modal;

    let inputRef: HTMLInputElement;

    function onOkClicked(): void {
        onOk(inputRef.value);
        modal.hide();
        value = initialValue;
    }

    function onCancelClicked(): void {
        modal.hide();
        value = initialValue;
    }

    function onShown(): void {
        inputRef.focus();
        setModalOpen(true);
    }

    function onHidden() {
        setModalOpen(false);
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
    tabindex="-1"
    aria-labelledby="modalLabel"
    aria-hidden="true"
>
    <div class="modal-dialog">
        <div class="modal-content" class:default-colors={$pageTheme.isDark}>
            <div class="modal-header">
                <h5 class="modal-title" id="modalLabel">{title}</h5>
                <button
                    type="button"
                    class="btn-close"
                    class:invert={$pageTheme.isDark}
                    data-bs-dismiss="modal"
                    aria-label="Close"
                ></button>
            </div>
            <div class="modal-body">
                <form on:submit|preventDefault={onOkClicked}>
                    <div class="mb-3">
                        <label for="prompt-input" class="col-form-label">
                            {prompt}:
                        </label>
                        <input
                            id="prompt-input"
                            bind:this={inputRef}
                            type="text"
                            class:nightMode={$pageTheme.isDark}
                            class="form-control"
                            bind:value
                        />
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button
                    type="button"
                    class="btn btn-secondary"
                    on:click={onCancelClicked}
                >
                    Cancel
                </button>
                <button type="button" class="btn btn-primary" on:click={onOkClicked}>
                    OK
                </button>
            </div>
        </div>
    </div>
</div>

<style lang="scss">
    @use "$lib/sass/night-mode" as nightmode;

    .nightMode {
        @include nightmode.input;
    }

    .default-colors {
        background-color: var(--canvas);
        color: var(--fg);
    }

    .invert {
        filter: invert(1) grayscale(100%) brightness(200%);
    }
</style>
