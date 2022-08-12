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
    import { chevronDown } from "./icons";

    export let collapsed: boolean;

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);

    const dispatch = createEventDispatcher();

    function toggle() {
        dispatch("toggle");
    }

    $: tooltip = collapsed ? tr.editingExpandField() : tr.editingCollapseField();
</script>

<div
    class="label-container"
    class:rtl={$direction === "rtl"}
    on:mousedown|preventDefault
    on:click|stopPropagation={toggle}
>
    <span class="chevron" class:collapsed>
        <Badge {tooltip} iconSize={80} --icon-align="text-bottom"
            >{@html chevronDown}</Badge
        >
    </span>
    <slot name="field-name" />

    <slot />
</div>

<style lang="scss">
    .label-container {
        position: sticky;
        top: 0;
        z-index: 3;
        background: var(--window-bg);
        cursor: pointer;

        &::before {
            content: "";
            z-index: -1;
            position: absolute;
            top: -5px;
            bottom: 0;
            left: -2px;
            right: -2px;
            background: var(--window-bg);
        }
        .chevron {
            opacity: 0.4;
            transition: opacity 0.2s ease-in-out, transform 80ms ease-in;
            &.collapsed {
                transform: rotate(-90deg);
            }
        }
        &.rtl .chevron.collapsed {
            transform: rotate(90deg);
        }
        &:hover .chevron {
            opacity: 1;
        }

        display: flex;
        justify-content: space-between;

        background-color: var(--label-color, transparent);

        padding-bottom: 1px;
    }

    .rtl {
        direction: rtl;
    }
</style>
