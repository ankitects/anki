<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { directionKey } from "@tslib/context-keys";
    import * as tr from "@tslib/ftl";
    import { removeStyleProperties } from "@tslib/styling";
    import { getContext } from "svelte";
    import { createEventDispatcher } from "svelte";
    import type { Readable } from "svelte/store";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import { floatLeftIcon, floatNoneIcon, floatRightIcon } from "./icons";

    export let image: HTMLImageElement;

    $: floatStyle = getComputedStyle(image).float;

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
    const [inlineStartIcon, inlineEndIcon] =
        $direction === "ltr"
            ? [floatLeftIcon, floatRightIcon]
            : [floatRightIcon, floatLeftIcon];

    const dispatch = createEventDispatcher();
</script>

<ButtonGroup size={1.6} wrap={false}>
    <IconButton
        tooltip={tr.editingFloatLeft()}
        active={floatStyle === "left"}
        flipX={$direction === "rtl"}
        on:click={() => {
            image.style.float = "left";
            setTimeout(() => dispatch("update"));
        }}
        --border-left-radius="5px">{@html inlineStartIcon}</IconButton
    >

    <IconButton
        tooltip={tr.editingFloatNone()}
        active={floatStyle === "none"}
        flipX={$direction === "rtl"}
        on:click={() => {
            // We shortly set to none, because simply unsetting float will not
            // trigger floatStyle being reset
            image.style.float = "none";
            removeStyleProperties(image, "float");
            setTimeout(() => dispatch("update"));
        }}>{@html floatNoneIcon}</IconButton
    >

    <IconButton
        tooltip={tr.editingFloatRight()}
        active={floatStyle === "right"}
        flipX={$direction === "rtl"}
        on:click={() => {
            image.style.float = "right";
            setTimeout(() => dispatch("update"));
        }}
        --border-right-radius="5px">{@html inlineEndIcon}</IconButton
    >
</ButtonGroup>
