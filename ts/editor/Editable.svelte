<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import EditableContainer from "editable/EditableContainer.svelte";
    import ImageHandle from "./ImageHandle.svelte";
    import MathjaxHandle from "./MathjaxHandle.svelte";

    import { getContext } from "svelte";

    /* if (fieldChanged) { */
    /*     editingArea.resetHandles(); */
    /* } */

    export let content: string;

    /* const { focusOnMount, fontName, fontSize, direction } = getContext("editingInputOptions"); */
</script>

<EditableContainer
    {content}
    let:imageOverlaySheet
    let:overlayRelative={container}
    on:editinginputfocus
    on:editinginputchanges
    on:editinginputblur
>
    {#await imageOverlaySheet then sheet}
        <ImageHandle
            activeImage={null}
            {container}
            isRtl={direction === "rtl"}
            {sheet}
        />
    {/await}
    <MathjaxHandle activeImage={null} {container} isRtl={direction === "rtl"} />
</EditableContainer>
