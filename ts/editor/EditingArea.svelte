<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Writable } from "svelte/store";
    import { getContext, createEventDispatcher } from "svelte";
    import { fieldFocusedKey } from "./NoteEditor.svelte";

    let editingArea: HTMLElement;

    const dispatch = createEventDispatcher();
    const fieldFocused = getContext<Writable<boolean>>(fieldFocusedKey);

    function deferFocusDown(): void {
        /* editingArea.focus(); */
        /* editingArea.caretToEnd(); */

        /* if (editingArea.getSelection().anchorNode === null) { */
        /*     // selection is not inside editable after focusing */
        /*     editingArea.caretToEnd(); */
        /* } */

        /* bridgeCommand(`focus:${editingArea.ord}`); */
        dispatch("fieldfocus", 1);
        fieldFocused.set(true);
    }

    function saveFieldIfFieldChanged(event: FocusEvent): void {
        const focusTo = event.relatedTarget!;
        /* const fieldChanged = */
        /*     editingArea !== getCurrentField() && !editingArea.contains(focusTo); */

        /* saveField(editingArea, fieldChanged ? "blur" : "key"); */
        fieldFocused.set(false);

        /* if (fieldChanged) { */
        /*     editingArea.resetHandles(); */
        /* } */
    }
</script>

<div
    bind:this={editingArea}
    class="editing-area"
    on:focusin={deferFocusDown}
    on:focusout={saveFieldIfFieldChanged}
>
    <slot />
</div>

<style>
    .editing-area {
        background: var(--frame-bg);
        border-radius: 0 0 5px 5px;

        /* TODO move this up one layer */
        /* &.dupe { */
        /*     // this works around the background colour persisting in copy+paste */
        /*     // (https://github.com/ankitects/anki/pull/1278) */
        /*     background-image: linear-gradient(var(--flag1-bg), var(--flag1-bg)); */
        /* } */
    }
</style>
