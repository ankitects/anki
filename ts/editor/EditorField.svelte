<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Readable } from "svelte/store";

    import contextProperty from "../sveltelib/context-property";
    import type { EditingAreaAPI } from "./EditingArea.svelte";

    export interface EditorFieldAPI {
        element: Promise<HTMLElement>;
        field: Readable<Notetypes.Notetype.Field>;
        editingArea: EditingAreaAPI;
    }

    const key = Symbol("editorField");
    const [context, setContextProperty] = contextProperty<EditorFieldAPI>(key);

    export { context };
</script>

<script lang="ts">
    import { onDestroy } from "svelte";
    import { writable } from "svelte/store";

    import { promiseWithResolver } from "../lib/promise";
    import type { Notetypes } from "../lib/proto";
    import { setDirectionProperty } from "../sveltelib/context-property";
    import type { Destroyable } from "./destroyable";
    import EditingArea from "./EditingArea.svelte";
    import FieldState from "./FieldState.svelte";
    import LabelContainer from "./LabelContainer.svelte";
    import LabelDescription from "./LabelDescription.svelte";
    import LabelName from "./LabelName.svelte";

    export let content: string;

    const field = writable<Notetypes.Notetype.Field>();

    let fieldInput: Notetypes.Notetype.Field;
    export { fieldInput as field };

    $: $field = fieldInput;
    $: config = fieldInput.config!;

    const directionStore = writable<"ltr" | "rtl">();
    setDirectionProperty(directionStore);

    $: $directionStore = config.rtl ? "rtl" : "ltr";

    const [element, elementResolve] = promiseWithResolver<HTMLElement>();

    let apiPartial: Partial<EditorFieldAPI> & Destroyable;
    export { apiPartial as api };

    const editingArea = {} as EditingAreaAPI;

    const api: EditorFieldAPI & Destroyable = Object.assign(apiPartial, {
        element,
        field,
        editingArea,
    });

    setContextProperty(api);

    onDestroy(() => api.destroy());
</script>

<div
    use:elementResolve
    class="editor-field"
    on:focusin
    on:focusout
    on:click={() => editingArea.focus()}
>
    <LabelContainer --editor-field-direction={$directionStore}>
        <span>
            <LabelName>
                {fieldInput.name}
            </LabelName>
            {#if config.description}
                <LabelDescription description={config.description} />
            {/if}
        </span>
        <FieldState><slot name="field-state" /></FieldState>
    </LabelContainer>
    <EditingArea {content} api={editingArea} on:contentupdate>
        <slot name="editing-inputs" />
    </EditingArea>
</div>

<style lang="scss">
    .editor-field {
        --border-color: var(--border);

        border-radius: 5px;
        border: 1px solid var(--border-color);

        &:focus-within {
            --border-color: var(--focus-border);

            outline: none;
            box-shadow: 0 0 0 3px var(--focus-shadow);
        }
    }
</style>
