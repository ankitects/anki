<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Readable } from "svelte/store";

    import type { EditingAreaAPI } from "./EditingArea.svelte";

    export interface FieldData {
        name: string;
        fontFamily: string;
        fontSize: number;
        direction: "ltr" | "rtl";
        plainText: boolean;
        description: string;
    }

    export interface EditorFieldAPI {
        element: Promise<HTMLElement>;
        direction: Readable<"ltr" | "rtl">;
        editingArea: EditingAreaAPI;
    }

    import { registerPackage } from "../lib/runtime-require";
    import contextProperty from "../sveltelib/context-property";
    import lifecycleHooks from "../sveltelib/lifecycle-hooks";

    const key = Symbol("editorField");
    const [context, setContextProperty] = contextProperty<EditorFieldAPI>(key);
    const [lifecycle, instances, setupLifecycleHooks] =
        lifecycleHooks<EditorFieldAPI>();

    export { context };

    registerPackage("anki/EditorField", {
        context,
        lifecycle,
        instances,
    });
</script>

<script lang="ts">
    import { onDestroy, setContext } from "svelte";
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";

    import Collapsible from "../components/Collapsible.svelte";
    import { collapsedKey, directionKey } from "../lib/context-keys";
    import { promiseWithResolver } from "../lib/promise";
    import type { Destroyable } from "./destroyable";
    import EditingArea from "./EditingArea.svelte";

    export let content: Writable<string>;
    export let field: FieldData;
    export let collapsed = false;

    const directionStore = writable<"ltr" | "rtl">();
    setContext(directionKey, directionStore);

    $: $directionStore = field.direction;

    const collapsedStore = writable<boolean>();
    setContext(collapsedKey, collapsedStore);

    $: $collapsedStore = collapsed;

    const editingArea: Partial<EditingAreaAPI> = {};
    const [element, elementResolve] = promiseWithResolver<HTMLElement>();

    let apiPartial: Partial<EditorFieldAPI> & Destroyable;
    export { apiPartial as api };

    const api: EditorFieldAPI & Destroyable = Object.assign(apiPartial, {
        element,
        direction: directionStore,
        editingArea: editingArea as EditingAreaAPI,
    });

    setContextProperty(api);
    setupLifecycleHooks(api);

    onDestroy(() => api?.destroy());
</script>

<div
    use:elementResolve
    class="editor-field"
    on:focusin
    on:focusout
    on:click={() => {/* TODO This breaks editor fields without open inputs: what to do? editingArea.focus?.() */}}
    on:mouseenter
    on:mouseleave
>
    <slot name="field-label" />

    <Collapsible {collapsed}>
        <EditingArea
            {content}
            fontFamily={field.fontFamily}
            fontSize={field.fontSize}
            api={editingArea}
        >
            <slot name="editing-inputs" />
        </EditingArea>
    </Collapsible>
</div>

<style lang="scss">
    .editor-field {
        position: relative;
        --border-color: var(--border);
    }
</style>
