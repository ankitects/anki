<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { directionKey } from "@tslib/context-keys";
    import * as tr from "@tslib/ftl";
    import { createEventDispatcher, getContext } from "svelte";
    import type { Readable } from "svelte/store";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import { sizeActual, sizeClear, sizeMinimized } from "./icons";

    export let isSizeConstrained: boolean;
    export let shrinkingDisabled: boolean;
    export let restoringDisabled: boolean;

    $: icon = isSizeConstrained ? sizeMinimized : sizeActual;

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
    const dispatch = createEventDispatcher();
</script>

<ButtonGroup size={1.6}>
    <IconButton
        disabled={shrinkingDisabled}
        flipX={$direction === "rtl"}
        tooltip="{tr.editingActualSize()} ({tr.editingDoubleClickImage()})"
        on:click={() => dispatch("imagetoggle")}
        --border-left-radius="5px">{@html icon}</IconButton
    >

    <IconButton
        disabled={restoringDisabled}
        tooltip={tr.editingRestoreOriginalSize()}
        on:click={() => dispatch("imageclear")}
        --border-right-radius="5px">{@html sizeClear}</IconButton
    >
</ButtonGroup>
