<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import * as tr from "../lib/ftl";
    import CollapseBadge from "./CollapseBadge.svelte";

    export let collapsed: boolean;
    let hovered = false;

    $: tooltip = collapsed ? tr.editingExpandField() : tr.editingCollapseField();

    const dispatch = createEventDispatcher();

    function toggle() {
        dispatch("toggle");
    }
</script>

<div class="label-container" on:mousedown|preventDefault>
    <span
        class="clickable"
        title={tooltip}
        on:click|stopPropagation={toggle}
        on:mouseenter={() => (hovered = true)}
        on:mouseleave={() => (hovered = false)}
    >
        <CollapseBadge {collapsed} highlighted={hovered} />
        <slot name="field-name" />
    </span>
    <slot />
</div>

<style lang="scss">
    .label-container {
        display: flex;
        position: sticky;
        top: 0;
        z-index: 3;
        justify-content: space-between;
        padding-bottom: 1px;

        &::before,
        &::after {
            content: "";
            z-index: -1;
            position: absolute;
        }

        /* pseudo element wider than container
           to cover up field borders on scroll
           - sadly there is no :stuck pseudo class */
        &::before {
            top: -5px;
            bottom: 0;
            left: -2px;
            right: -2px;
            background: var(window-bg);
        }

        /* pseudo element for duplicate background */
        &::after {
            top: 0;
            bottom: 0;
            left: 0;
            right: 0;
            border-top-left-radius: 5px;
            border-top-right-radius: 5px;
            background: var(--label-color, transparent);
        }

        .clickable {
            cursor: pointer;
        }
    }
</style>
