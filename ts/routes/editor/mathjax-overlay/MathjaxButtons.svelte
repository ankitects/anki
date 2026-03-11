<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { createEventDispatcher } from "svelte";

    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import ButtonToolbar from "$lib/components/ButtonToolbar.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { blockIcon, deleteIcon, inlineIcon } from "$lib/components/icons";

    import ClozeButtons from "../ClozeButtons.svelte";

    export let isBlock: boolean;
    export let isClozeField: boolean;

    const dispatch = createEventDispatcher();
</script>

<ButtonToolbar size={1.6} wrap={false}>
    <ButtonGroup>
        <IconButton
            tooltip={tr.editingMathjaxInline()}
            active={!isBlock}
            on:click={() => dispatch("setinline")}
            --border-left-radius="5px"
        >
            <Icon icon={inlineIcon} />
        </IconButton>

        <IconButton
            tooltip={tr.editingMathjaxBlock()}
            active={isBlock}
            on:click={() => dispatch("setblock")}
            --border-right-radius="5px"
        >
            <Icon icon={blockIcon} />
        </IconButton>
    </ButtonGroup>

    {#if isClozeField}
        <ClozeButtons on:surround alwaysEnabled={true} />
    {/if}

    <ButtonGroup>
        <IconButton
            tooltip={tr.actionsDelete()}
            on:click={() => dispatch("delete")}
            --border-left-radius="5px"
            --border-right-radius="5px"
        >
            <Icon icon={deleteIcon} />
        </IconButton>
    </ButtonGroup>
</ButtonToolbar>
