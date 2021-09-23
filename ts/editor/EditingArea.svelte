<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export interface ActiveInputAPI {
        readonly name: string;
        fieldHTML: string;
        focus(): void;
        moveCaretToEnd(): void;
    }
</script>

<script lang="ts">
    import EditableContainer from "editable/EditableContainer.svelte";
    import ImageHandle from "./ImageHandle.svelte";
    import MathjaxHandle from "./MathjaxHandle.svelte";
    import Codable from "./Codable.svelte";

    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";
    import { setContext, getContext } from "svelte";
    import {
        fontFamilyKey,
        fontSizeKey,
        editorFieldKey,
        editingAreaKey,
        activeInputKey,
    } from "lib/context-keys";
    import type { EditorFieldAPI } from "./MultiRootEditor.svelte";

    export let fontFamily: string;
    export let fontSize: number;

    const fontFamilyStore = writable();
    $: $fontFamilyStore = fontFamily;
    setContext(fontFamilyKey, fontFamilyStore);

    const fontSizeStore = writable();
    $: $fontSizeStore = fontSize;
    setContext(fontSizeKey, fontSizeStore);

    export let content: string;

    function fetchContent() {
        content = $activeInput!.fieldHTML;
    }

    /* if (fieldChanged) { */
    /*     editingArea.resetHandles(); */
    /* } */

    /* TODO unused */
    let codableActive = false;

    const activeInput: Writable<ActiveInputAPI | null> = writable(null);

    setContext(activeInputKey, activeInput);

    const editingAreaAPI = getContext<EditorFieldAPI>(editorFieldKey).editingArea;
    Object.defineProperties(editingAreaAPI, {
        activeInput: {
            get: () => $activeInput,
        },
        toggleCodable: {
            value: () => (codableActive = !codableActive),
        },
        fontFamily: {
            get: () => $fontFamilyStore,
        },
        fontSize: {
            get: () => $fontSizeStore,
        },
    });

    setContext(editingAreaKey, editingAreaAPI);
</script>

<!-- Could be generalized -->
<div class="editing-area">
    <EditableContainer {content} let:imageOverlaySheet let:overlayRelative={container}>
        {#await imageOverlaySheet then sheet}
            <ImageHandle activeImage={null} {container} {sheet} />
        {/await}
        <MathjaxHandle activeImage={null} {container} />
    </EditableContainer>

    <Codable {content} />
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
