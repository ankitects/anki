<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import EditableContainer from "editable/EditableContainer.svelte";
    import ImageHandle from "./ImageHandle.svelte";
    import MathjaxHandle from "./MathjaxHandle.svelte";

    import type Editable from "editable/Editable.svelte";
    import type { DecoratedElement } from "editable/decorated";

    import type { Writable } from "svelte/store";
    import type { EditingInputAPI } from "./EditingArea.svelte";
    import { getContext, createEventDispatcher, onMount, onDestroy } from "svelte";
    import { activeInputKey, editingInputsKey } from "lib/context-keys";

    export let content: string;
    export let decoratedComponents: DecoratedElement[];

    let editableContainer: EditableContainer;

    const activeInput = getContext<Writable<EditingInputAPI | null>>(activeInputKey);

    $: if (editableContainer && $activeInput?.name !== "editable") {
        editableContainer.editablePromise.then((editable: Editable) => {
            editable.api.fieldHTML = content;
        });
    }

    /* if (fieldChanged) { */
    /*     resetHandles(); */
    /* } */

    const dispatch = createEventDispatcher();

    const editingInputs = getContext<Partial<EditingInputAPI>[]>(editingInputsKey);
    const editingInputIndex = editingInputs.push({}) - 1;

    onMount(() =>
        editableContainer.editablePromise.then((editable: Editable) =>
            editingInputs.splice(editingInputIndex, 1, editable.api)
        )
    );
    onDestroy(() => editingInputs.splice(editingInputIndex, 1));
</script>

<EditableContainer
    bind:this={editableContainer}
    {content}
    {decoratedComponents}
    on:editablefocus={() => {
        dispatch("editingfocus");
        editableContainer.editablePromise.then(
            (editable) => ($activeInput = editable.api)
        );
    }}
    on:editableinput={() => {
        dispatch("editinginput");
    }}
    on:editableblur={() => {
        dispatch("editingblur");
        $activeInput = null;
    }}
    let:imageOverlaySheet
    let:overlayRelative={container}
>
    {#await imageOverlaySheet then sheet}
        <ImageHandle activeImage={null} {container} {sheet} />
    {/await}
    <MathjaxHandle activeImage={null} {container} />
</EditableContainer>
