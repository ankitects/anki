<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { EditingAreaAPI } from "./EditorField.svelte";

    export interface ActiveInputAPI {
        readonly name: string;
        fieldHTML: string;
        focus(): void;
        moveCaretToEnd(): void;
    }
</script>

<script lang="ts">
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";
    import { setContext, getContext, createEventDispatcher } from "svelte";
    import { editingAreaKey, activeInputKey, fieldFocusedKey } from "lib/context-keys";

    const dispatch = createEventDispatcher();
    const fieldFocused = getContext<Writable<boolean>>(fieldFocusedKey);

    let codableActive = false;

    const activeInput: Writable<ActiveInputAPI | null> = writable(null);
    setContext(activeInputKey, activeInput);

    function onFocusIn(): void {
        dispatch("fieldfocus", 1);
        fieldFocused.set(true);
    }

    function onFocusOut(event: FocusEvent): void {
        /* const focusTo = event.relatedTarget!; */
        /* const fieldChanged = */
        /*     editingArea !== getCurrentField() && !editingArea.contains(focusTo); */

        /* saveField(editingArea, fieldChanged ? "blur" : "key"); */

        /* if (fieldChanged) { */
        /*     editingArea.resetHandles(); */
        /* } */

        fieldFocused.set(false);
    }

    const editingArea = getContext<Writable<EditingAreaAPI | null>>(editingAreaKey);
    $editingArea = Object.defineProperties(
        {},
        {
            activeInput: {
                get: () => $activeInput,
            },
            toggleCodable: {
                value: () => (codableActive = !codableActive),
            },
        }
    );
</script>

<div class="editing-area" on:focusin={onFocusIn} on:focusout={onFocusOut}>
    <slot activeInput={codableActive ? "codable" : "editable"} />
</div>

<style>
    .editing-area {
        background: var(--frame-bg);
        border-radius: 0 0 5px 5px;

        transition: height 5s;

        /* TODO move this up one layer */
        /* &.dupe { */
        /*     // this works around the background colour persisting in copy+paste */
        /*     // (https://github.com/ankitects/anki/pull/1278) */
        /*     background-image: linear-gradient(var(--flag1-bg), var(--flag1-bg)); */
        /* } */
    }
</style>
