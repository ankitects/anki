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
        collapsed: boolean;
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
    export let flipInputs = false;

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

<div class="field-container" on:mouseenter on:mouseleave>
    <slot name="field-label" />

    <Collapsible collapse={collapsed} let:collapsed={hidden}>
        <div use:elementResolve class="editor-field" on:focusin on:focusout {hidden}>
            <EditingArea
                {content}
                fontFamily={field.fontFamily}
                fontSize={field.fontSize}
                api={editingArea}
            >
                {#if flipInputs}
                    <slot name="plain-text-input" />
                    <slot name="rich-text-input" />
                {:else}
                    <slot name="rich-text-input" />
                    <slot name="plain-text-input" />
                {/if}
            </EditingArea>
        </div>
    </Collapsible>
</div>

<style lang="scss">
    @use "sass/elevation" as *;

    .editor-field {
        overflow: hidden;
        margin: 1px 3px 0 3px;

        border-radius: 5px;
        border: 1px solid var(--border);

        @include elevation(1);

        &:focus-within {
            outline: 2px solid var(--border-focus);
            outline-offset: -1px;
        }
    }
</style>
