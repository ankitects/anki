<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "../lib/i18n";

    import ButtonGroup from "../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../components/ButtonGroupItem.svelte";
    import IconButton from "../components/IconButton.svelte";

    import { sizeActual, sizeMinimized } from "./icons";

    export let active: boolean;
    export let isRtl: boolean;

    $: icon = active ? sizeActual : sizeMinimized;
</script>

<ButtonGroup size={1.6}>
    <ButtonGroupItem>
        <IconButton {active} flipX={isRtl} tooltip={tr.editingActualSize()} on:click
            >{@html icon}</IconButton
        >
    </ButtonGroupItem>
</ButtonGroup>
