<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Modal from "bootstrap/js/dist/modal";
    import { getContext, onDestroy, onMount } from "svelte";

    import { registerModalClosingHandler } from "$lib/sveltelib/modal-closing";

    import { modalsKey } from "./context-keys";

    export let modalKey: string = Math.random().toString(36).substring(2);
    export const dialogClass: string = "";
    export const onOkClicked: () => void = () => {};
    export const onCancelClicked: () => void = () => {};
    export const onShown: () => void = () => {};
    export const onHidden: () => void = () => {};

    const modals = getContext<Map<string, Modal>>(modalsKey);

    let modal: Modal;
    let modalRef: HTMLDivElement;

    function onOkClicked_(): void {
        modal.hide();
        onOkClicked();
    }

    function onCancelClicked_(): void {
        modal.hide();
        onCancelClicked();
    }

    export function show(): void {
        modal.show();
    }

    export function hide(): void {
        modal.hide();
    }

    export { onOkClicked_ as acceptHandler, onCancelClicked_ as cancelHandler };

    const { set: setModalOpen, remove: removeModalClosingHandler } =
        registerModalClosingHandler(onOkClicked_);

    function onShown_() {
        setModalOpen(true);
        onShown();
    }

    function onHidden_() {
        setModalOpen(false);
        onHidden();
    }

    onMount(() => {
        modalRef.addEventListener("shown.bs.modal", onShown_);
        modalRef.addEventListener("hidden.bs.modal", onHidden_);
        modal = new Modal(modalRef, { keyboard: false });
        modals.set(modalKey, modal);
    });

    onDestroy(() => {
        removeModalClosingHandler();
        modalRef.removeEventListener("shown.bs.modal", onShown_);
        modalRef.removeEventListener("hidden.bs.modal", onHidden_);
    });
</script>

<div
    bind:this={modalRef}
    class="modal fade"
    tabindex="-1"
    aria-labelledby="modalLabel"
    aria-hidden="true"
>
    <div class="modal-dialog {dialogClass}">
        <div class="modal-content">
            <slot name="header" />
            <slot name="body" />
            <slot name="footer" />
        </div>
    </div>
</div>

<style lang="scss">
    .modal {
        z-index: 1066;
        background-color: rgba($color: black, $alpha: 0.5);
    }

    .modal-content {
        background-color: var(--canvas);
        color: var(--fg);
    }
</style>
