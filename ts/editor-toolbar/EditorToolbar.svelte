<script lang="typescript">
    import type { Readable } from "svelte/store";
    import { setContext } from "svelte";
    import { disabledKey, nightModeKey } from "./contextKeys";

    import ButtonGroup from "./ButtonGroup.svelte";
    import type { Buttons } from "./types";

    export let buttons: Buttons = [];
    export let menus: SvelteComponent[];

    export let nightMode: boolean;
    export let disabled: Readable<boolean> = false;

    setContext(nightModeKey, nightMode);
    setContext(disabledKey, disabled);
</script>

<style lang="scss">
    .base {
        --toolbar-size: 30px;
    }

    nav {
        position: sticky;
        top: 0;
        left: 0;
        z-index: 10;

        background: var(--bg-color);
        border-bottom: 1px solid var(--border);

        /* Remove outermost marigns */
        & > :global(ul) {
            & > :global(li:nth-child(1)) {
                margin-left: 0;
            }

            & > :global(li:nth-last-child(1)) {
                margin-right: 0;
            }
        }
    }
</style>

<div class="base">
    <div>
        {#each menus as menu}
            <svelte:component this={menu.component} {...menu} />
        {/each}
    </div>

    <nav>
        <ButtonGroup {buttons} />
    </nav>
</div>
