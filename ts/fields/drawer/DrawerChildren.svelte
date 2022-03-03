<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DrawerItemType } from "./drawer";
    import DrawerEntry from "./DrawerEntry.svelte";

    type T = any;

    export let items: DrawerItemType<T>[];
</script>

<div class="drawer-children">
    {#each items as item (item.name)}
        <DrawerEntry {item} let:path let:data>
            <slot name="before" slot="before" />
            <slot {path} {data} />
            <slot name="after" slot="after" />
        </DrawerEntry>
    {/each}
</div>

<style lang="scss">
    .drawer-children {
        margin-left: 12px;
    }
</style>
