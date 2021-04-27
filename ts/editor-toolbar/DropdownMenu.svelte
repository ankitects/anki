<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { ToolbarItem } from "./types";

    export let id: string;
    export let items: ToolbarItem[];
</script>

<style lang="scss">
    ul {
        background-color: var(--frame-bg);
        border-color: var(--medium-border);
    }
</style>

<ul {id} class="dropdown-menu">
    {#each items as menuItem}
        <li>
            <svelte:component this={menuItem.component} {...menuItem} />
        </li>
    {/each}
</ul>
