<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "$lib/components/Badge.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import { chevronDown } from "$lib/components/icons";

    export let collapsed = false;
</script>

<div class="collapse-badge" class:collapsed>
    <Badge iconSize={80}><Icon icon={chevronDown} /></Badge>
</div>

<style lang="scss">
    .collapse-badge {
        display: inline-block;
        opacity: 0.4;
        transition:
            opacity var(--transition) ease-in-out,
            transform var(--transition) ease-in;
        :global(.collapse-label:hover) & {
            opacity: 1;
        }
        &.collapsed {
            transform: rotate(-90deg);
        }
    }

    :global([dir="rtl"]) {
        .collapse-badge.collapsed {
            transform: rotate(90deg);
        }
    }
</style>
