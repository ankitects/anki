<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "../../components/IconConstrain.svelte";
    import { childrenClosedIcon, childrenOpenIcon } from "./icons";

    export let collapsed: boolean;
    export let hide: boolean;

    function toggleCollapse() {
        collapsed = !collapsed;
    }
</script>

<div class="drawer-collapse-icon" class:hide on:click={toggleCollapse}>
    <IconConstrain iconSize={60} top={-2}>
        {#if collapsed}
            {@html childrenClosedIcon}
        {:else}
            {@html childrenOpenIcon}
        {/if}
    </IconConstrain>
</div>

<style lang="scss">
    .drawer-collapse-icon.hide {
        opacity: 0;
    }
</style>
