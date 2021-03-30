<script lang="typescript">
    import DropdownItem from "./DropdownItem.svelte";
    import type { Readable } from "svelte/store";
    import { setContext } from "svelte";
    import { disabledKey, nightModeKey } from "./contextKeys";

    import ButtonGroup from "./ButtonGroup.svelte";
    import type { Buttons } from "./ButtonGroup.svelte";

    export let buttons: Buttons = [];
    export let nightMode: boolean;
    export let disabled: Readable<boolean> = false;

    setContext(disabledKey, disabled);
    setContext(nightModeKey, nightMode);
</script>

<style lang="scss">
    nav {
        position: sticky;
        top: 0;
        left: 0;
        z-index: 10;

        background: var(--bg-color);
        border-bottom: 1px solid var(--border);

        /* Remove most outer marigns */
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

<ul class="dropdown-menu" id="dropdownMenuButton123" aria-labelledby="dropdownMenuButton123">
    <DropdownItem>
        <svelte:fragment slot="start">Action</svelte:fragment>
        <svelte:fragment slot="end">Shortcut</svelte:fragment>
    </DropdownItem>
    <DropdownItem>Action 1</DropdownItem>
    <DropdownItem>Action 2</DropdownItem>
</ul>

<nav>
    <ButtonGroup {buttons} />
</nav>
