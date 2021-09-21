<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export interface EditorInputAPI {
        focus: () => void;
        moveCaretToEnd: () => void;
    }
</script>

<script lang="ts">
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";
    import { setContext, getContext, createEventDispatcher } from "svelte";
    import { activeInputKey, fieldFocusedKey } from "lib/context-keys";

    let editingArea: HTMLElement;

    const dispatch = createEventDispatcher();
    const fieldFocused = getContext<Writable<boolean>>(fieldFocusedKey);

    export const activeInput: Writable<EditorInputAPI | null> = writable(null);

    setContext(activeInputKey, activeInput);

    function focus(): void {
        $activeInput?.focus();
    }

    function moveCaretToEnd(): void {
        $activeInput?.moveCaretToEnd();
    }

    function onFocusIn(): void {
        focus();
        moveCaretToEnd();

        dispatch("fieldfocus", 1);
        fieldFocused.set(true);
    }

    function onFocusOut(event: FocusEvent): void {
        /* const focusTo = event.relatedTarget!; */
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
    on:focusin={onFocusIn}
    on:focusout={onFocusOut}
>
    <slot deferFocus />
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
