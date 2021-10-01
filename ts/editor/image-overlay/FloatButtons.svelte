<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "../../lib/i18n";
    import type { Readable } from "svelte/store";
    import { getContext } from "svelte";
    import { directionKey } from "../../lib/context-keys";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../../components/ButtonGroupItem.svelte";
    import IconButton from "../../components/IconButton.svelte";

    import { createEventDispatcher } from "svelte";
    import { floatNoneIcon, floatLeftIcon, floatRightIcon } from "./icons";
    import { signifyCustomInput } from "../../editable/editable";

    export let image: HTMLImageElement;

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
    const [inlineStartIcon, inlineEndIcon] =
        $direction === "ltr"
            ? [floatLeftIcon, floatRightIcon]
            : [floatRightIcon, floatLeftIcon];

    const dispatch = createEventDispatcher();
</script>

<ButtonGroup size={1.6} wrap={false}>
    <ButtonGroupItem>
        <IconButton
            tooltip={tr.editingFloatLeft()}
            active={image.style.float === "left"}
            flipX={$direction === "rtl"}
            on:click={() => {
                image.style.float = "left";
                signifyCustomInput(image);
                setTimeout(() => dispatch("update"));
            }}>{@html inlineStartIcon}</IconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <IconButton
            tooltip={tr.editingFloatNone()}
            active={image.style.float === "" || image.style.float === "none"}
            flipX={$direction === "rtl"}
            on:click={() => {
                image.style.removeProperty("float");

                if (image.getAttribute("style")?.length === 0) {
                    image.removeAttribute("style");
                }

                signifyCustomInput(image);
                setTimeout(() => dispatch("update"));
            }}>{@html floatNoneIcon}</IconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <IconButton
            tooltip={tr.editingFloatRight()}
            active={image.style.float === "right"}
            flipX={$direction === "rtl"}
            on:click={() => {
                image.style.float = "right";
                signifyCustomInput(image);
                setTimeout(() => dispatch("update"));
            }}>{@html inlineEndIcon}</IconButton
        >
    </ButtonGroupItem>
</ButtonGroup>
