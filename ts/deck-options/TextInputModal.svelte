<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Modal from "bootstrap/js/dist/modal";
    import { getContext, onDestroy, onMount } from "svelte";

    import { modalsKey } from "../components/context-keys";
    import { pageTheme } from "../sveltelib/theme";

    export let title: string;
    export let prompt: string;
    export let value = "";
    export let onOk: (text: string) => void;

    export const modalKey: string = Math.random().toString(36).substring(2);

    const modals = getContext<Map<string, Modal>>(modalsKey);

    let modalRef: HTMLDivElement;
    let modal: Modal;

    let inputRef: HTMLInputElement;

    function onOkClicked(): void {
        onOk(inputRef.value);
        modal.hide();
        value = "";
    }

    function onShown(): void {
        inputRef.focus();
    }

    onMount(() => {
        modalRef.addEventListener("shown.bs.modal", onShown);
        modal = new Modal(modalRef);
        modals.set(modalKey, modal);
    });

    onDestroy(() => {
        modalRef.removeEventListener("shown.bs.modal", onShown);
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
                />
            </div>
            <div class="modal-body">
                <form on:submit|preventDefault={onOkClicked}>
                    <div class="mb-3">
                        <label for="prompt-input" class="col-form-label"
                            >{prompt}:</label
                        >
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
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal"
                    >Cancel</button
                >
                <button type="button" class="btn btn-primary" on:click={onOkClicked}
                    >OK</button
                >
            </div>
        </div>
    </div>
</div>

<style lang="scss">
    @use "sass/night-mode" as nightmode;

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
