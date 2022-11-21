<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { createEventDispatcher } from "svelte";
    
    import CollapseBadge from "./CollapseBadge.svelte";

    export let collapsed: boolean;
    let hovered = false;

    $: tooltip = collapsed ? tr.editingExpandField() : tr.editingCollapseField();

    const dispatch = createEventDispatcher();

    function toggle() {
        dispatch("toggle");
    }
</script>

<div class="label-container">
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
        justify-content: space-between;
        background: var(--canvas);
        padding: 0 3px 1px;

        position: sticky;
        top: 0;
        z-index: 50;

        .clickable {
            cursor: pointer;
        }
    }
</style>
