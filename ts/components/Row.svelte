<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Item from "../components/Item.svelte";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };
</script>

<Item {id}>
    <div class="row {className}">
        <slot />
    </div>
</Item>

<style lang="scss">
    .row {
        display: flex;
        flex-flow: row wrap;
        align-content: stretch;
        padding: var(--gutter-block, 0) 0;
    }
</style>
