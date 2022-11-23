<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "../components/Badge.svelte";
    import { chevronDown } from "./icons";

    export let collapsed = false;
    export let highlighted = false;
</script>

<div class="collapse-badge" class:collapsed class:highlighted>
    <Badge iconSize={80}>{@html chevronDown}</Badge>
</div>

<style lang="scss">
    .collapse-badge {
        display: inline-block;
        opacity: 0.4;
        transition: opacity 0.2s ease-in-out, transform 80ms ease-in;
        &.highlighted {
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
