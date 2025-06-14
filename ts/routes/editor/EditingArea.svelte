<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";

    import contextProperty from "$lib/sveltelib/context-property";

    export interface FocusableInputAPI {
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

    export interface EditingInputAPI extends FocusableInputAPI {
        /**
         * Check whether blurred target belongs to an editing input.
         * The editing area can then restore focus to this input.
         *
         * @returns An editing input api that is associated with the event target.
         */
        getInputAPI(target: EventTarget): Promise<FocusableInputAPI | null>;
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
    import { fontFamilyKey, fontSizeKey } from "@tslib/context-keys";
    import { setContext as svelteSetContext } from "svelte";
    import { writable } from "svelte/store";

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

    const inputsStore = writable<EditingInputAPI[]>([]);
    $: editingInputs = $inputsStore;

    function getAvailableInput(): EditingInputAPI | undefined {
        return editingInputs.find((input) => input.focusable);
    }

    function focus(): void {
        editingArea.contains(document.activeElement);
    }

    function refocus(): void {
        const availableInput = getAvailableInput();

        if (availableInput) {
            availableInput.refocus();
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

<div bind:this={editingArea} class="editing-area">
    <slot />
</div>

<style lang="scss">
    .editing-area {
        display: grid;

        /* This defines the border between inputs */
        grid-gap: 1px;
        background-color: var(--border);
    }
</style>
