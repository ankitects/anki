<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { EditingAreaAPI } from "./EditingArea.svelte";
    import contextProperty from "../sveltelib/context-property";

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

    const key = Symbol("editorField");
    const [set, getEditorField, hasEditorField] = contextProperty<EditorFieldAPI>(key);

    export { getEditorField, hasEditorField };
</script>

<script lang="ts">
    import EditingArea from "./EditingArea.svelte";
    import LabelContainer from "./LabelContainer.svelte";
    import LabelName from "./LabelName.svelte";
    import FieldState from "./FieldState.svelte";

    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import type { Writable } from "svelte/store";
    import { directionKey } from "../lib/context-keys";

    export let content: Writable<string>;
    export let field: FieldData;
    export let autofocus = false;

    const directionStore = writable();
    setContext(directionKey, directionStore);

    $: $directionStore = field.direction;

    let editorField: HTMLElement;

    export const api = set(
        Object.create(
            {},
            {
                element: {
                    get: () => editorField,
                },
                direction: {
                    get: () => $directionStore,
                },
            },
        ) as EditorFieldAPI,
    );
</script>

<div
    bind:this={editorField}
    class="editor-field"
    on:focusin
    on:focusout
    on:click={() => api.editingArea?.focus()}
>
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
