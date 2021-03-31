<script lang="typescript">
    import type { SvelteComponentDefinition } from "./types";

    export let id: string;
    export let menuItems: SvelteComponentDefinition[];
</script>

<ul class="dropdown-menu" {id}>
    {#each menuItems as menuItem}
        <li>
            <svelte:component this={menuItem.component} {...menuItem} />
        </li>
    {/each}
</ul>
