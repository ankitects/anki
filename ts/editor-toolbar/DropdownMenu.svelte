<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { ToolbarItem } from "./types";
    import { getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";

    export let id: string;
    export let items: ToolbarItem[];

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<style lang="scss">
    @use 'ts/sass/button_mixins' as button;

    ul {
        background-color: white;
        border-color: var(--medium-border);
    }

    .night-mode {
        background-color: var(--bg-color);
    }
</style>

<ul {id} class="dropdown-menu" class:night-mode={nightMode}>
    {#each items as menuItem}
        <li>
            <svelte:component this={menuItem.component} {...menuItem} />
        </li>
    {/each}
</ul>
