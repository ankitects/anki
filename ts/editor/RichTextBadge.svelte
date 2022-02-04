<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "../components/Badge.svelte";
    import * as tr from "../lib/ftl";
    import { richTextOff, richTextOn } from "./icons";

    export let off: boolean;

    function toggle(): void {
        off = !off;
    }

    $: icon = off ? richTextOff : richTextOn;
</script>

<span class="rich-text-badge" class:highlighted={off} on:click|stopPropagation={toggle}>
    <Badge
        tooltip={tr.editingToggleVisualEditor()}
        iconSize={80}
        --icon-align="text-top">{@html icon}</Badge
    >
</span>

<style lang="scss">
    span {
        opacity: 0.4;

        &.highlighted {
            opacity: 1;
        }
        &:hover {
            opacity: 0.8;
        }
    }
</style>
