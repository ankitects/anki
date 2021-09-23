<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import EditableContainer from "editable/EditableContainer.svelte";
    import type Editable from "editable/Editable.svelte";
    import ImageHandle from "./ImageHandle.svelte";
    import MathjaxHandle from "./MathjaxHandle.svelte";

    import type { Writable } from "svelte/store";
    import type { EditingInputAPI } from "./EditingArea.svelte";
    import { getContext, createEventDispatcher, onMount, onDestroy } from "svelte";
    import { activeInputKey, editingInputsKey } from "lib/context-keys";

    export let content: string;

    let editableContainer: EditableContainer;

    const activeInput = getContext<Writable<EditingInputAPI | null>>(activeInputKey);

    $: if (editableContainer && $activeInput?.name !== "editable") {
        editableContainer.editablePromise.then((editable: Editable) => {
            editable.api.fieldHTML = content;
        });
    }

    const dispatch = createEventDispatcher();

    const editingInputs = getContext<EditingInputAPI[]>(editingInputsKey);
    let editingInputIndex: number;

    onMount(() =>
        editableContainer.editablePromise.then((editable: Editable) => {
            editingInputIndex = editingInputs.push(editable.api) - 1;
        })
    );

    onDestroy(() => editingInputs.splice(editingInputIndex, 1));
</script>

<EditableContainer
    bind:this={editableContainer}
    {content}
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
