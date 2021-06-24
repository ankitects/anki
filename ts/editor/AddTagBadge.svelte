<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "components/Badge.svelte";
    import { tagIcon, addTagIcon } from "./icons";

    let theTagIcon = tagIcon;
</script>

<Badge
    class="add-icon d-flex me-1"
    on:click
    on:mouseenter={() => (theTagIcon = addTagIcon)}
    on:mouseleave={() => (theTagIcon = tagIcon)}>{@html theTagIcon}</Badge
>

<style lang="scss">
    :global(.add-icon > svg) {
        cursor: pointer;
        opacity: 0.6;
    }

    :global(.add-icon > svg:hover) {
        opacity: 1;
    }
</style>
