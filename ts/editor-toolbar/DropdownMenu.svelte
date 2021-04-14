<script lang="typescript">
    import type { SvelteComponentDefinition } from "./types";
    import { getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";

    export let id: string;
    export let menuItems: SvelteComponentDefinition[];

    const nightMode = getContext(nightModeKey);
</script>

<style lang="scss">
    @import "ts/node_modules/bootstrap/scss/functions";
    @import "ts/node_modules/bootstrap/scss/variables";

    ul {
        background-color: $light;

        &.nightMode {
            background-color: $secondary;
        }
    }
</style>

<ul {id} class="dropdown-menu" class:nightMode>
    {#each menuItems as menuItem}
        <li class:nightMode>
            <svelte:component this={menuItem.component} {...menuItem} />
        </li>
    {/each}
</ul>
