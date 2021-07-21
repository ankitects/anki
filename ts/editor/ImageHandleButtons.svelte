<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";

    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";

    import { floatLeftIcon, floatRightIcon, resetIcon } from "./icons";

    const dispatch = createEventDispatcher();
</script>

<ButtonToolbar size={1.6} wrap={false}>
    <ButtonGroup>
        <ButtonGroupItem>
            <IconButton on:click={() => dispatch("floatleft")}
                >{@html floatLeftIcon}</IconButton
            >
        </ButtonGroupItem>

        <ButtonGroupItem>
            <IconButton on:click={() => dispatch("floatright")}
                >{@html floatRightIcon}</IconButton
            >
        </ButtonGroupItem>

        <ButtonGroupItem>
            <IconButton on:click={() => dispatch("floatreset")}
                >{@html resetIcon}</IconButton
            >
        </ButtonGroupItem>
    </ButtonGroup>
</ButtonToolbar>
