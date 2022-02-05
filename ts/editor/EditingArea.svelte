<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";

    import contextProperty from "../sveltelib/context-property";

    export interface EditingInputAPI {
        readonly name: string;
        focusable: boolean;
        /**
         * The reaction to a user-initiated focus, e.g. by clicking on the
         * editor label, or pressing Tab.
         */
        focus(): void;
        /**
         * Behaves similar to a refresh, e.g. sync with content, put the caret
         * into a neutral position, and/or clear selections.
         */
        refocus(): void;
    }

    export interface EditingAreaAPI {
        content: Writable<string>;
        editingInputs: Writable<EditingInputAPI[]>;
        focus(): void;
        refocus(): void;
    }

    const key = Symbol("editingArea");
    const [context, setContextProperty] = contextProperty<EditingAreaAPI>(key);

    export { context };
</script>

<script lang="ts">
    import { setContext as svelteSetContext } from "svelte";
    import { writable } from "svelte/store";

    import { fontFamilyKey, fontSizeKey } from "../lib/context-keys";
    import FocusTrap from "./FocusTrap.svelte";

    export let fontFamily: string;
    const fontFamilyStore = writable(fontFamily);
    $: $fontFamilyStore = fontFamily;
    svelteSetContext(fontFamilyKey, fontFamilyStore);

    export let fontSize: number;
    const fontSizeStore = writable(fontSize);
    $: $fontSizeStore = fontSize;
    svelteSetContext(fontSizeKey, fontSizeStore);

    export let content: Writable<string>;

    let editingArea: HTMLElement;
    let focusTrap: FocusTrap;

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
        if (focusTrap && focusTrap.isFocusTrap(document.activeElement!)) {
            focusEditingInputIfAvailable();
        }
    }

    $: {
        $inputsStore;
        /**
         * Triggers when all editing inputs are hidden,
         * the editor field has focus, and then some
         * editing input is shown
         */
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

    let apiPartial: Partial<EditingAreaAPI>;
    export { apiPartial as api };

    const api = Object.assign(apiPartial, {
        content,
        editingInputs: inputsStore,
        focus,
        refocus,
    });

    setContextProperty(api);
</script>

<FocusTrap bind:this={focusTrap} on:focus={focusEditingInputInsteadIfAvailable} />

<div bind:this={editingArea} class="editing-area" on:focusout={trapFocusOnBlurOut}>
    <slot />
</div>

<style lang="scss">
    .editing-area {
        display: grid;
        /* TODO allow configuration of grid #1503 */
        /* grid-template-columns: repeat(2, 1fr); */

        position: relative;
        background: var(--frame-bg);
        border-radius: 0 0 5px 5px;

        &:focus {
            outline: none;
        }
    }
</style>
