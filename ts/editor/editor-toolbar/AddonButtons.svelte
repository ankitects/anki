<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";

    export let buttons: string[];
</script>

<ButtonGroup>
    {#each buttons as button, index}
        {@html button}
    {/each}
</ButtonGroup>

<style lang="scss">
    div {
        display: contents;
    }
</style>
