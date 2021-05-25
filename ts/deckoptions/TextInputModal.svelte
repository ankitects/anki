<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    /* eslint
    @typescript-eslint/no-non-null-assertion: "off",
    */
    import { onMount, onDestroy, getContext } from "svelte";
    import { nightModeKey } from "components/contextKeys";
    import Modal from "bootstrap/js/dist/modal";

    export let title: string;
    export let prompt: string;
    export let startingValue = "";
    export let onOk: (text: string) => void;

    let inputRef: HTMLInputElement;
    let modal: Modal;

    function onShown(): void {
        inputRef.focus();
    }

    function onHidden(): void {
        const container = document.getElementById("modal")!;
        container.removeChild(container.firstElementChild!);
    }

    function onOkClicked(): void {
        onOk(inputRef.value);
        modal.hide();
    }

    onMount(() => {
        const container = document.getElementById("modal")!;
        container.addEventListener("shown.bs.modal", onShown);
        container.addEventListener("hidden.bs.modal", onHidden);
        modal = new Modal(container.firstElementChild!, {});
        modal.show();
    });

    onDestroy(() => {
        const container = document.getElementById("modal")!;
        container.removeEventListener("shown.bs.modal", onShown);
        container.removeEventListener("hidden.bs.modal", onHidden);
    });

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<style lang="scss">
    .default-colors {
        background-color: var(--window-bg);
        color: var(--text-fg);
    }

    .invert {
        filter: invert(1) grayscale(100%) brightness(200%);
    }
</style>

<div class="modal fade" tabindex="-1" aria-labelledby="modalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content" class:default-colors={nightMode}>
            <div class="modal-header">
                <h5 class="modal-title" id="modalLabel">{title}</h5>
                <button
                    type="button"
                    class="btn-close"
                    class:invert={nightMode}
                    data-bs-dismiss="modal"
                    aria-label="Close" />
            </div>
            <div class="modal-body">
                <form on:submit|preventDefault={onOkClicked}>
                    <div class="mb-3">
                        <label
                            for="prompt-input"
                            class="col-form-label">{prompt}</label>
                        <input
                            id="prompt-input"
                            bind:this={inputRef}
                            type="text"
                            class="form-control"
                            value={startingValue} />
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button
                    type="button"
                    class="btn btn-secondary"
                    data-bs-dismiss="modal">Cancel</button>
                <button
                    type="button"
                    class="btn btn-primary"
                    on:click={onOkClicked}>OK</button>
            </div>
        </div>
    </div>
</div>
