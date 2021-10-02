<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "../components/Badge.svelte";
    import { editableOn, editableOff } from "./icons";

    export let off: boolean;

    function toggle(): void {
        off = !off;
    }

    $: icon = off ? editableOff : editableOn;
</script>

<span on:click|stopPropagation={toggle}>
    <Badge iconSize={80}>{@html icon}</Badge>
</span>

<style lang="scss">
    span {
        opacity: 0.6;

        &:hover {
            opacity: 1;
        }
    }
</style>
