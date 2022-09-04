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
        justify-content: space-between;
        top: 0;
        padding-bottom: 1px;

        /* slightly wider than EditingArea
           to cover field borders on scroll */
        left: -1px;
        right: -1px;
        z-index: 10;
        background: var(--label-color);

        .clickable {
            cursor: pointer;
        }
    }
</style>
