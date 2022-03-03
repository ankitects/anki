<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DrawerItemType } from "./drawer";
    import DrawerChildren from "./DrawerChildren.svelte";
    import DrawerCollapseIcon from "./DrawerCollapseIcon.svelte";
    import DrawerItem from "./DrawerItem.svelte";

    type T = any;

    export let item: DrawerItemType<T>;

    let collapsed = false;
</script>

<div class="drawer-entry">
    <slot path={item.name} data={item.data} name="before" />

    <DrawerItem>
        <DrawerCollapseIcon hide={item.children.length === 0} bind:collapsed />
        <slot path={item.name} data={item.data} />
    </DrawerItem>

    <slot name="after" path={item.name} data={item.data} />

    {#if item.children.length > 0 && !collapsed}
        <DrawerChildren items={item.children} let:path let:data>
            <slot {path} {data} name="before" slot="before" />
            <slot {path} {data} />
            <slot {path} {data} name="after" slot="after" />
        </DrawerChildren>
    {/if}
</div>
