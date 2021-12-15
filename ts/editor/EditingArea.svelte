<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";
    import contextProperty from "../sveltelib/context-property";

    export interface EditingInputAPI {
        readonly name: string;
        focus(): void;
        refocus(): void;
        focusable: boolean;
        moveCaretToEnd(): void;
    }

    export interface EditingAreaAPI {
        content: Writable<string>;
        editingInputs: Writable<EditingInputAPI[]>;
        focus(): void;
        refocus(): void;
    }

    const key = Symbol("editingArea");
    const [set, getEditingArea, hasEditingArea] = contextProperty<EditingAreaAPI>(key);

    export { getEditingArea, hasEditingArea };
</script>

<script lang="ts">
    import { writable } from "svelte/store";
    import { onMount, setContext as svelteSetContext } from "svelte";
    import { fontFamilyKey, fontSizeKey } from "../lib/context-keys";

    export let fontFamily: string;
    const fontFamilyStore = writable(fontFamily);
    $: $fontFamilyStore = fontFamily;
    svelteSetContext(fontFamilyKey, fontFamilyStore);

    export let fontSize: number;
    const fontSizeStore = writable(fontSize);
    $: $fontSizeStore = fontSize;
    svelteSetContext(fontSizeKey, fontSizeStore);

    export let content: Writable<string>;
    export let autofocus = false;

    let editingArea: HTMLElement;
    let focusTrap: HTMLInputElement;

    const inputsStore = writable<EditingInputAPI[]>([]);
    $: editingInputs = $inputsStore;

    function getAvailableInput(): EditingInputAPI | undefined {
        return editingInputs.find((input) => input.focusable);
    }

    function focusEditingInputIfAvailable(): boolean {
        const availableInput = getAvailableInput();

        if (availableInput) {
            availableInput.focus();
            return true;
        }

        return false;
    }

    function focusEditingInputIfFocusTrapFocused(): void {
        if (document.activeElement === focusTrap) {
            focusEditingInputIfAvailable();
        }
    }

    $: {
        $inputsStore;
        focusEditingInputIfFocusTrapFocused();
    }

    function focus(): void {
        if (editingArea.contains(document.activeElement)) {
            // do nothing
        } else if (!focusEditingInputIfAvailable()) {
            focusTrap.focus();
        }
    }

    function refocus(): void {
        const availableInput = getAvailableInput();

        if (availableInput) {
            availableInput.refocus();
        } else {
            focusTrap.blur();
            focusTrap.focus();
        }
    }

    function focusEditingInputInsteadIfAvailable(event: FocusEvent): void {
        if (focusEditingInputIfAvailable()) {
            event.preventDefault();
        }
    }

    // prevents editor field being entirely deselected when
    // closing active field
    function trapFocusOnBlurOut(event: FocusEvent): void {
        if (!event.relatedTarget && editingInputs.every((input) => !input.focusable)) {
            focusTrap.focus();
            event.preventDefault();
        }
    }

    export let api: Partial<EditingAreaAPI> = {};

    Object.assign(
        api,
        set({
            content,
            editingInputs: inputsStore,
            focus,
            refocus,
        }),
    );

    onMount(() => {
        if (autofocus) {
            focus();
        }
    });
</script>

<input
    bind:this={focusTrap}
    readonly
    tabindex="-1"
    class="focus-trap"
    on:focus={focusEditingInputInsteadIfAvailable}
/>

<div bind:this={editingArea} class="editing-area" on:focusout={trapFocusOnBlurOut}>
    <slot />
</div>

<style lang="scss">
    .editing-area {
        position: relative;
        background: var(--frame-bg);
        border-radius: 0 0 5px 5px;

        &:focus {
            outline: none;
        }
    }

    .focus-trap {
        display: block;
        width: 0px;
        height: 0;
        padding: 0;
        margin: 0;
        border: none;
        outline: none;
        -webkit-appearance: none;
        background: none;
        resize: none;
        appearance: none;
    }
</style>
