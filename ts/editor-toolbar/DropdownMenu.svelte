<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";
    import { getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";

    export let id: string;
    export let menuItems: DynamicSvelteComponent[];

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<style lang="scss">
    @import "ts/sass/bootstrap/functions";
    @import "ts/sass/bootstrap/variables";

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
