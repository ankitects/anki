<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import { hasBlockAttribute } from "../../lib/dom";
    import * as tr from "../../lib/ftl";
    import { blockIcon, deleteIcon, inlineIcon } from "./icons";

    export let element: Element;

    $: isBlock = hasBlockAttribute(element);

    function updateBlock() {
        element.setAttribute("block", String(isBlock));
    }

    const dispatch = createEventDispatcher();
</script>

<ButtonToolbar size={1.6} wrap={false}>
    <ButtonGroup>
        <IconButton
            tooltip={tr.editingMathjaxInline()}
            active={!isBlock}
            on:click={() => {
                isBlock = false;
                updateBlock();
            }}
            on:click
            --border-left-radius="5px">{@html inlineIcon}</IconButton
        >

        <IconButton
            tooltip={tr.editingMathjaxBlock()}
            active={isBlock}
            on:click={() => {
                isBlock = true;
                updateBlock();
            }}
            on:click
            --border-right-radius="5px">{@html blockIcon}</IconButton
        >
    </ButtonGroup>

    <ButtonGroup>
        <IconButton
            tooltip={tr.actionsDelete()}
            on:click={() => dispatch("delete")}
            --border-left-radius="5px"
            --border-right-radius="5px">{@html deleteIcon}</IconButton
        >
    </ButtonGroup>
</ButtonToolbar>
