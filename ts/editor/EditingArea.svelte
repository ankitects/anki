<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export interface EditingInputAPI {
        readonly name: string;
        fieldHTML: string;
        focus(): void;
        moveCaretToEnd(): void;
    }
</script>

<script lang="ts">
    import type { Writable } from "svelte/store";
    import type { SvelteComponent } from "svelte";
    import { writable } from "svelte/store";
    import { setContext, getContext } from "svelte";
    import {
        fontFamilyKey,
        fontSizeKey,
        editorFieldKey,
        editingAreaKey,
        editingInputsKey,
        activeInputKey,
    } from "lib/context-keys";
    import type { EditorFieldAPI } from "./MultiRootEditor.svelte";

    export let editingInputs: typeof SvelteComponent[];

    const editingInputsList: EditingInputAPI[] = [];
    setContext(editingInputsKey, editingInputsList);

    export let content: string;

    function fetchContent() {
        content = $activeInput!.fieldHTML;
    }

    export let fontFamily: string;
    export let fontSize: number;

    const fontFamilyStore = writable();
    $: $fontFamilyStore = fontFamily;
    setContext(fontFamilyKey, fontFamilyStore);

    const fontSizeStore = writable();
    $: $fontSizeStore = fontSize;
    setContext(fontSizeKey, fontSizeStore);

    /* if (fieldChanged) { */
    /*     editingArea.resetHandles(); */
    /* } */

    const activeInput: Writable<EditingInputAPI | null> = writable(null);
    setContext(activeInputKey, activeInput);

    const editingAreaAPI = getContext<EditorFieldAPI>(editorFieldKey).editingArea;
    Object.defineProperties(editingAreaAPI, {
        fieldHTML: {
            get: () => content,
            set: (value: string) => (content = value),
        },
        fontFamily: {
            get: () => $fontFamilyStore,
            set: (value: string) => ($fontFamilyStore = value),
        },
        fontSize: {
            get: () => $fontSizeStore,
            set: (value: number) => ($fontSizeStore = value),
        },
        activeInput: {
            get: () => $activeInput,
        },
        editingInputs: {
            value: editingInputsList,
        },
    });

    setContext(editingAreaKey, editingAreaAPI);
</script>

<div class="editing-area">
    {#each editingInputs as component}
        <svelte:component
            this={component}
            {content}
            on:editingfocus
            on:editinginput={fetchContent}
            on:editinginput
            on:editingblur={fetchContent}
            on:editingblur
        />
    {/each}
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
