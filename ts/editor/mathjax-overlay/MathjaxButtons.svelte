<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import Item from "../../components/Item.svelte";
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../../components/ButtonGroupItem.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import * as tr from "../../lib/ftl";
    import { inlineIcon, blockIcon, deleteIcon } from "./icons";
    import { createEventDispatcher } from "svelte";

    export let activeImage: HTMLImageElement;
    export let mathjaxElement: HTMLElement;

    const dispatch = createEventDispatcher();
</script>

<ButtonToolbar size={1.6} wrap={false}>
    <Item>
        <ButtonGroup>
            <ButtonGroupItem>
                <IconButton
                    tooltip={tr.editingMathjaxInline()}
                    active={activeImage.getAttribute("block") === "true"}
                    on:click={() => mathjaxElement.setAttribute("block", "false")}
                    on:click>{@html inlineIcon}</IconButton
                >
            </ButtonGroupItem>

            <ButtonGroupItem>
                <IconButton
                    tooltip={tr.editingMathjaxBlock()}
                    active={activeImage.getAttribute("block") === "false"}
                    on:click={() => mathjaxElement.setAttribute("block", "true")}
                    on:click>{@html blockIcon}</IconButton
                >
            </ButtonGroupItem>
        </ButtonGroup>
    </Item>

    <Item>
        <ButtonGroup>
            <ButtonGroupItem>
                <IconButton
                    tooltip={tr.actionsDelete()}
                    on:click={() => dispatch("delete")}>{@html deleteIcon}</IconButton
                >
            </ButtonGroupItem>
        </ButtonGroup>
    </Item>
</ButtonToolbar>
