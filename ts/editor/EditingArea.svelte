<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { DecoratedElement } from "editable/decorated";

    export interface EditingAreaAPI {
        focus(): void;
        readonly decoratedComponents: DecoratedElement[];
    }

    export interface EditingInputAPI {
        readonly name: string;
        fieldHTML: string;
        focus(): void;
        moveCaretToEnd(): void;
    }

    import { DefineArray } from "editable/decorated";
    import { Mathjax } from "editable/mathjax-component";

    const decoratedComponents = new DefineArray();

    decoratedComponents.push(Mathjax);
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
    import type { EditorFieldAPI } from "./EditorField.svelte";

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

    const activeInput: Writable<EditingInputAPI | null> = writable(null);
    setContext(activeInputKey, activeInput);

    let editingArea: HTMLElement;

    const editingAreaAPI = Object.defineProperties(
        {},
        {
            fieldHTML: {
                get: () => {
                    let result = content;
                    for (const component of decoratedComponents) {
                        result = component.toUndecorated(result);
                    }
                    return result;
                },
                set: (value: string) => {
                    for (const component of decoratedComponents) {
                        value = component.toUndecorated(value);
                    }
                    content = value;
                },
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
            decoratedComponents: {
                value: decoratedComponents,
            },
            focus: {
                value: () => {
                    if (editingArea.contains(document.activeElement)) {
                        // do nothing
                    } else if (editingInputsList.length > 0) {
                        const firstInput = editingInputsList[0];
                        firstInput.focus();
                        firstInput.moveCaretToEnd();
                    } else {
                        editingArea.focus();
                    }
                },
            },
        }
    );

    setContext(editingAreaKey, editingAreaAPI);

    const editorFieldAPI = getContext<EditorFieldAPI>(editorFieldKey);
    Object.defineProperty(editorFieldAPI, "editingArea", { value: editingAreaAPI });
</script>

<slot name="label" />

<div bind:this={editingArea} class="editing-area">
    {#each editingInputs as component}
        <svelte:component
            this={component}
            {content}
            {decoratedComponents}
            on:editingfocus
            on:editinginput={fetchContent}
            on:editinginput
            on:editingblur
        />
    {/each}
</div>

<style>
    .editing-area {
        background: var(--frame-bg);
        border-radius: 0 0 5px 5px;

        /* TODO move this up one layer */
        /* &.dupe { */
        /*     // this works around the background colour persisting in copy+paste */
        /*     // (https://github.com/ankitects/anki/pull/1278) */
        /*     background-image: linear-gradient(var(--flag1-bg), var(--flag1-bg)); */
        /* } */
    }
</style>
