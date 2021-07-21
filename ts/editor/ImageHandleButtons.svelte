<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";

    import { floatLeftIcon, floatRightIcon, resetIcon } from "./icons";

    export let float: string;
</script>

<ButtonToolbar size={1.6} wrap={false}>
    <ButtonGroup>
        <ButtonGroupItem>
            <IconButton active={float === "left"} on:click={() => (float = "left")}
                >{@html floatLeftIcon}</IconButton
            >
        </ButtonGroupItem>

        <ButtonGroupItem>
            <IconButton active={float === "right"} on:click={() => (float = "right")}
                >{@html floatRightIcon}</IconButton
            >
        </ButtonGroupItem>

        <ButtonGroupItem>
            <IconButton
                active={float === "" || float === "none"}
                on:click={() => (float = "")}>{@html resetIcon}</IconButton
            >
        </ButtonGroupItem>
    </ButtonGroup>
</ButtonToolbar>
