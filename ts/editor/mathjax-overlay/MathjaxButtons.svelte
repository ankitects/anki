<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import * as tr from "../../lib/ftl";
    import ClozeButtons from "../ClozeButtons.svelte";
    import { blockIcon, deleteIcon, inlineIcon } from "./icons";

    export let isBlock: boolean;

    const dispatch = createEventDispatcher();
</script>

<ButtonToolbar size={1.6} wrap={false}>
    <ButtonGroup>
        <IconButton
            tooltip={tr.editingMathjaxInline()}
            active={!isBlock}
            on:click={() => dispatch("setInline")}
            --border-left-radius="5px"
        >
            {@html inlineIcon}
        </IconButton>

        <IconButton
            tooltip={tr.editingMathjaxBlock()}
            active={isBlock}
            on:click={() => dispatch("setblock")}
            --border-right-radius="5px"
        >
            {@html blockIcon}
        </IconButton>
    </ButtonGroup>

    <ClozeButtons on:surround />

    <ButtonGroup>
        <IconButton
            tooltip={tr.actionsDelete()}
            on:click={() => dispatch("delete")}
            --border-left-radius="5px"
            --border-right-radius="5px">{@html deleteIcon}</IconButton
        >
    </ButtonGroup>
</ButtonToolbar>
