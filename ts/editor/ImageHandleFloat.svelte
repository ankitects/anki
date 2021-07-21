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

    export let float: string;
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
            active={float === "" || float === "none"}
            flipX={isRtl}
            on:click={() => (float = "")}>{@html floatNoneIcon}</IconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <IconButton
            tooltip={inlineStart.label}
            active={float === inlineStart.position}
            flipX={isRtl}
            on:click={() => (float = inlineStart.position)}
            >{@html floatLeftIcon}</IconButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <IconButton
            tooltip={inlineEnd.label}
            active={float === inlineEnd.position}
            flipX={isRtl}
            on:click={() => (float = inlineEnd.position)}
            >{@html floatRightIcon}</IconButton
        >
    </ButtonGroupItem>
</ButtonGroup>
