<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, getContext } from "svelte";
    import type { Readable } from "svelte/store";

    import Badge from "../components/Badge.svelte";
    import { directionKey } from "../lib/context-keys";
    import * as tr from "../lib/ftl";
    import { chevronDown, chevronRight } from "./icons";

    export let off: boolean;

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);

    const dispatch = createEventDispatcher();

    function toggle() {
        dispatch("toggle");
    }

    $: icon = off ? chevronRight : chevronDown;
    $: tooltip = collapsed ? tr.editingExpandField() : tr.editingCollapseField();
</script>

<div
    class:collapsed={off}
    class="label-container"
    class:rtl={$direction === "rtl"}
    on:mousedown|preventDefault
>
    <span class="clickable" on:click|stopPropagation={toggle}>
        <span class="chevron">
            <Badge {tooltip} iconSize={80} --icon-align="text-bottom"
                >{@html icon}</Badge
            >
        </span>
        <slot name="field-name" />
    </span>
    <slot />
</div>

<style lang="scss">
    .label-container {
        & .chevron {
            opacity: 0.4;
            transition: opacity 0.2s ease-in-out;
        }

        display: flex;
        justify-content: space-between;

        background-color: var(--label-color, transparent);

        padding: 1px 0;
    }
    .clickable {
        cursor: pointer;
        &:hover .chevron {
            opacity: 1;
        }
    }

    .rtl {
        direction: rtl;
    }
</style>
