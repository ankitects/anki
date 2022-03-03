<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DrawerItemType } from "./drawer";
    import DrawerEntry from "./DrawerEntry.svelte";

    type T = any;

    export let root: DrawerItemType<T>;
</script>

<aside class="drawer-container">
    <DrawerEntry item={root} let:path let:data>
        <slot {path} {data} name="before" slot="before" />
        <slot {path} {data} />
        <slot {path} {data} name="after" slot="after" />
    </DrawerEntry>
</aside>

<style lang="scss">
    @use "sass/elevation" as elevation;

    .drawer-container {
        position: relative;
        flex: 0 0 180px;

        overflow-x: hidden;
        overflow-y: scroll;
        touch-action: pan-y;

        background-color: salmon;

        @include elevation.elevation(4);
        z-index: 40;

        animation: slideIn 0.25s cubic-bezier(0.84, 1.47, 0.62, 0.89);
    }

    @keyframes slideIn {
        0% {
            transform: translateX(-180px);
        }
        100% {
            transform: translateX(0px);
        }
    }

    /** TODO experimental scrollbars */

    :global(::-webkit-scrollbar) {
        width: 8px;
        height: 10px;
        border-radius: 100px;
        background-color: rgb(0 0 0 / 0.2);
    }

    :global(::-webkit-scrollbar-thumb) {
        background-color: cornflowerblue;
        border-radius: 100px;
    }

    :global(::-webkit-scrollbar-corner) {
        background-color: transparent;
    }
</style>
