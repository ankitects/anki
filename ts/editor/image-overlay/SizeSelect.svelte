<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import * as tr from "../../lib/ftl";
    import { directionProperty } from "../../sveltelib/context-property";
    import { sizeActual, sizeMinimized } from "./icons";

    export let active: boolean;

    $: icon = active ? sizeActual : sizeMinimized;

    const direction = directionProperty.get();
</script>

<ButtonGroup size={1.6}>
    <IconButton
        {active}
        flipX={$direction === "rtl"}
        tooltip="{tr.editingActualSize()} ({tr.editingDoubleClickImage()})"
        on:click
        --border-left-radius="5px"
        --border-right-radius="5px">{@html icon}</IconButton
    >
</ButtonGroup>
