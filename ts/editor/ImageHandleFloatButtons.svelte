<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "../lib/i18n";

    import ButtonGroup from "../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../components/ButtonGroupItem.svelte";
    import IconButton from "../components/IconButton.svelte";

    import { createEventDispatcher } from "svelte";
    import { floatNoneIcon, floatLeftIcon, floatRightIcon } from "./icons";

    export let image: HTMLImageElement;
    export let isRtl: boolean;

    const [inlineStartIcon, inlineEndIcon] = isRtl
        ? [floatRightIcon, floatLeftIcon]
        : [floatLeftIcon, floatRightIcon];

    const dispatch = createEventDispatcher();
</script>

<ButtonGroup size={1.6} wrap={false}>
    <ButtonGroupItem>
        <IconButton
            tooltip={tr.editingFloatLeft()}
            active={image.style.float === "left"}
            flipX={isRtl}
            on:click={() => {
                image.style.float = "left";
                setTimeout(() => dispatch("update"));
            }}>{@html inlineStartIcon}</IconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <IconButton
            tooltip={tr.editingFloatNone()}
            active={image.style.float === "" || image.style.float === "none"}
            flipX={isRtl}
            on:click={() => {
                image.style.removeProperty("float");

                if (image.getAttribute("style")?.length === 0) {
                    image.removeAttribute("style");
                }

                setTimeout(() => dispatch("update"));
            }}>{@html floatNoneIcon}</IconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <IconButton
            tooltip={tr.editingFloatRight()}
            active={image.style.float === "right"}
            flipX={isRtl}
            on:click={() => {
                image.style.float = "right";
                setTimeout(() => dispatch("update"));
            }}>{@html inlineEndIcon}</IconButton
        >
    </ButtonGroupItem>
</ButtonGroup>
