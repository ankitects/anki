<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let tooltip: string | undefined = undefined;
</script>

<div
    class="handle-background"
    title={tooltip}
    on:mousedown|preventDefault
    on:click|stopPropagation
    on:dblclick
/>

<style lang="scss">
    .handle-background {
        width: 100%;
        height: 100%;
        background-color: black;
        opacity: 0.2;
    }
</style>
