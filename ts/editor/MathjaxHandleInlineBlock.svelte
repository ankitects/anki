<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";

    import { inlineIcon, blockIcon } from "./icons";

    export let activeImage: HTMLImageElement;

    $: mathjaxElement = activeImage.parentElement!;
</script>

<ButtonGroup size={1.6} wrap={false}>
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
