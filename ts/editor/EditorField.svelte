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
        hidden: boolean;
    }

    export interface EditorFieldAPI {
        element: Promise<HTMLElement>;
        direction: Readable<"ltr" | "rtl">;
        editingArea: EditingAreaAPI;
    }

    import { registerPackage } from "@tslib/runtime-require";

    import contextProperty from "$lib/sveltelib/context-property";
    import lifecycleHooks from "$lib/sveltelib/lifecycle-hooks";

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
    import { collapsedKey, directionKey } from "@tslib/context-keys";
    import { promiseWithResolver } from "@tslib/promise";
    import { onDestroy, setContext } from "svelte";
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";

    import Collapsible from "$lib/components/Collapsible.svelte";

    import type { Destroyable } from "./destroyable";
    import EditingArea from "./EditingArea.svelte";

    export let content: Writable<string>;
    export let field: FieldData;
    export let collapsed = false;
    export let flipInputs = false;
    export let dupe = false;
    export let index;

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
    class="field-container"
    class:hide={field.hidden}
    on:mouseenter
    on:mouseleave
    role="presentation"
    data-index={index}
>
    <slot name="field-label" />

    <Collapsible collapse={collapsed} let:collapsed={hidden}>
        <div
            use:elementResolve
            class="editor-field"
            class:dupe
            on:focusin
            on:focusout
            {hidden}
        >
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
    @use "../lib/sass/elevation" as *;

    /* Make sure labels are readable on custom Qt backgrounds */
    .field-container {
        background: var(--canvas);
        border-radius: var(--border-radius);
        overflow: hidden;
    }

    .field-container.hide {
        display: none;
    }

    .editor-field {
        overflow: hidden;
        /* make room for thicker focus border */
        margin: 1px;

        border-radius: var(--border-radius);
        border: 1px solid var(--border);

        @include elevation(1);

        outline-offset: -1px;
        &.dupe,
        &.dupe:focus-within {
            outline: 2px solid var(--accent-danger);
        }
        &:focus-within {
            outline: 2px solid var(--border-focus);
        }
    }
</style>
