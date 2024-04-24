<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { directionKey } from "@tslib/context-keys";
    import { createEventDispatcher, getContext } from "svelte";
    import type { Readable } from "svelte/store";

    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { sizeActual, sizeClear, sizeMinimized } from "$lib/components/icons";

    export let isSizeConstrained: boolean;
    export let shrinkingDisabled: boolean;
    export let restoringDisabled: boolean;

    $: icon = isSizeConstrained ? sizeMinimized : sizeActual;

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
    const dispatch = createEventDispatcher();
</script>

<ButtonGroup size={1.6}>
    <IconButton
        disabled={shrinkingDisabled}
        flipX={$direction === "rtl"}
        tooltip="{tr.editingActualSize()} ({tr.editingDoubleClickImage()})"
        on:click={() => dispatch("imagetoggle")}
        --border-left-radius="5px"
    >
        <Icon {icon} />
    </IconButton>

    <IconButton
        disabled={restoringDisabled}
        tooltip={tr.editingRestoreOriginalSize()}
        on:click={() => dispatch("imageclear")}
        --border-right-radius="5px"
    >
        <Icon icon={sizeClear} />
    </IconButton>
</ButtonGroup>
