<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import CollapseLabel from "./CollapseLabel.svelte";

    export let collapsed: boolean;

    $: tooltip = collapsed ? tr.editingExpandField() : tr.editingCollapseField();
</script>

<div class="label-container">
    <CollapseLabel {collapsed} {tooltip} on:toggle>
        <slot name="field-name" />
    </CollapseLabel>
    <slot />
</div>

<style lang="scss">
    .label-container {
        display: flex;
        justify-content: space-between;
        background: var(--canvas);
        border-top-right-radius: var(--border-radius);
        border-top-left-radius: var(--border-radius);
        padding: 0 3px 1px;

        position: sticky;
        top: 0;
        z-index: 50;
    }
</style>
