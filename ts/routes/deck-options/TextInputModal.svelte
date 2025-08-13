<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Modal from "$lib/components/Modal.svelte";
    import { pageTheme } from "$lib/sveltelib/theme";

    export let title: string;
    export let prompt: string;
    export let initialValue = "";
    export let onOk: (text: string) => void;
    $: value = initialValue;

    let inputRef: HTMLInputElement;
    let modal: Modal;
    export let modalKey: string;

    function onOkClicked(): void {
        onOk(inputRef.value);
        value = initialValue;
    }

    function onCancelClicked(): void {
        value = initialValue;
    }

    function onShown(): void {
        inputRef.focus();
    }
</script>

<Modal bind:this={modal} bind:modalKey {onOkClicked} {onShown} {onCancelClicked}>
    <div slot="header" class="modal-header">
        <h5 class="modal-title" id="modalLabel">{title}</h5>
        <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
        ></button>
    </div>
    <div slot="body" class="modal-body">
        <form on:submit|preventDefault={modal.acceptHandler}>
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
    <div slot="footer" class="modal-footer">
        <button type="button" class="btn btn-secondary" on:click={modal.cancelHandler}>
            Cancel
        </button>
        <button type="button" class="btn btn-primary" on:click={modal.acceptHandler}>OK</button>
    </div>
</Modal>

<style lang="scss">
    @use "../../lib/sass/night-mode" as nightmode;

    .nightMode {
        @include nightmode.input;
    }
</style>
