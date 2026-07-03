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
    on:dblclick
    tabindex="-1"
    role="button"
></div>

<style lang="scss">
    .handle-background {
        width: 100%;
        height: 100%;
        background-color: var(--handle-background-color, #aaa);
        border-radius: 5px;
        opacity: 0.2;
    }
</style>
