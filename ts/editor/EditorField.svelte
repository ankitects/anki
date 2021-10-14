<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { EditingAreaAPI } from "./EditingArea.svelte";

    export interface FieldData {
        name: string;
        fontFamily: string;
        fontSize: number;
        direction: "ltr" | "rtl";
    }

    export interface EditorFieldAPI {
        element: HTMLElement;
        index: number;
        direction: "ltr" | "rtl";
        editingArea?: EditingAreaAPI;
    }
</script>

<script lang="ts">
    import EditingArea from "./EditingArea.svelte";
    import LabelContainer from "./LabelContainer.svelte";
    import LabelName from "./LabelName.svelte";
    import FieldState from "./FieldState.svelte";

    import { setContext as svelteSetContext } from "svelte";
    import { writable } from "svelte/store";
    import type { Writable } from "svelte/store";
    import { directionKey } from "../lib/context-keys";
    import { setContext, editorFieldKey } from "./context";

    export let content: Writable<string>;
    export let field: FieldData;
    export let autofocus = false;

    const directionStore = writable();
    svelteSetContext(directionKey, directionStore);

    $: $directionStore = field.direction;

    const obj = {};

    let editorField: HTMLElement;

    export const api = setContext(
        editorFieldKey,
        Object.defineProperties(obj, {
            element: {
                get: () => editorField,
            },
            direction: {
                get: () => $directionStore,
            },
        }) as EditorFieldAPI
    );
</script>

<div bind:this={editorField} class="editor-field" on:focusin on:focusout>
    <LabelContainer>
        <LabelName>{field.name}</LabelName>
        <FieldState><slot name="field-state" /></FieldState>
    </LabelContainer>
    <EditingArea
        {content}
        {autofocus}
        fontFamily={field.fontFamily}
        fontSize={field.fontSize}
        bind:api={api.editingArea}
        on:editinginput
    >
        <slot name="editing-inputs" />
    </EditingArea>
</div>

<style lang="scss">
    .editor-field {
        --border-color: var(--border);

        border-radius: 5px;
        border: 1px solid var(--border-color);

        min-width: 0;

        &:focus-within {
            --border-color: var(--focus-border);

            outline: none;
            box-shadow: 0 0 0 3px var(--focus-shadow);
        }
    }
</style>
