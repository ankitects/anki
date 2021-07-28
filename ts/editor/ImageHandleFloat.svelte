<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";

    import { floatNoneIcon, floatLeftIcon, floatRightIcon } from "./icons";

    export let activeImage: HTMLImageElement;
    export let isRtl: boolean;

    const leftValues = {
        position: "left",
        label: tr.editingFloatLeft(),
        icon: floatLeftIcon,
    };

    const rightValues = {
        position: "right",
        label: tr.editingFloatRight(),
    };

    $: inlineStart = isRtl ? rightValues : leftValues;
    $: inlineEnd = isRtl ? leftValues : rightValues;
</script>

<ButtonGroup size={1.6} wrap={false} reverse={isRtl}>
    <ButtonGroupItem>
        <IconButton
            tooltip={tr.editingFloatNone()}
            active={activeImage.style.float === "" ||
                activeImage.style.float === "none"}
            flipX={isRtl}
            on:click={() => (activeImage.style.float = "")}
            >{@html floatNoneIcon}</IconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <IconButton
            tooltip={inlineStart.label}
            active={activeImage.style.float === inlineStart.position}
            flipX={isRtl}
            on:click={() => (activeImage.style.float = inlineStart.position)}
            >{@html floatLeftIcon}</IconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <IconButton
            tooltip={inlineEnd.label}
            active={activeImage.style.float === inlineEnd.position}
            flipX={isRtl}
            on:click={() => (activeImage.style.float = inlineEnd.position)}
            >{@html floatRightIcon}</IconButton
        >
    </ButtonGroupItem>
</ButtonGroup>
