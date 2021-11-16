<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Item from "../components/Item.svelte";
    export let id: string | undefined = undefined;
</script>

<Item {id}>
    <div class="row">
        <slot />
    </div>
</Item>

<style lang="scss">
    .row {
        display: flex;
        flex-flow: row wrap;
        align-content: stretch;
        /* margin: 0 var(--gutter-inline, 0); */
        padding: var(--gutter-block, 0) 0;
    }
</style>
