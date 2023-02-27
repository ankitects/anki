<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import CollapseBadge from "./CollapseBadge.svelte";

    export let collapsed: boolean;
    export let tooltip: string;

    const dispatch = createEventDispatcher();

    function toggle() {
        dispatch("toggle");
    }
</script>

<span class="collapse-label" title={tooltip} on:click|stopPropagation={toggle}>
    <CollapseBadge {collapsed} />
    <slot />
</span>

<style lang="scss">
    .collapse-label {
        cursor: pointer;
    }
</style>
