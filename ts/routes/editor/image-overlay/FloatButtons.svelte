<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { removeStyleProperties } from "@tslib/styling";
    import { createEventDispatcher } from "svelte";

    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import {
        floatLeftIcon,
        floatNoneIcon,
        floatRightIcon,
    } from "$lib/components/icons";

    export let image: HTMLImageElement;

    $: floatStyle = getComputedStyle(image).float;

    const dispatch = createEventDispatcher();
</script>

<ButtonGroup size={1.6} wrap={false}>
    <IconButton
        tooltip={tr.editingFloatLeft()}
        active={floatStyle === "left"}
        on:click={() => {
            image.style.float = "left";
            setTimeout(() => dispatch("update"));
        }}
        --border-left-radius="5px"
    >
        <Icon icon={floatLeftIcon} />
    </IconButton>

    <IconButton
        tooltip={tr.editingFloatNone()}
        active={floatStyle === "none"}
        on:click={() => {
            // We shortly set to none, because simply unsetting float will not
            // trigger floatStyle being reset
            image.style.float = "none";
            removeStyleProperties(image, "float");
            setTimeout(() => dispatch("update"));
        }}
    >
        <Icon icon={floatNoneIcon} />
    </IconButton>

    <IconButton
        tooltip={tr.editingFloatRight()}
        active={floatStyle === "right"}
        on:click={() => {
            image.style.float = "right";
            setTimeout(() => dispatch("update"));
        }}
        --border-right-radius="5px"
    >
        <Icon icon={floatRightIcon} />
    </IconButton>
</ButtonGroup>
