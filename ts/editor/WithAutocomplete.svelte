<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, onDestroy, tick } from "svelte";

    import type Dropdown from "bootstrap/js/dist/dropdown";

    import WithDropdownMenu from "components/WithDropdownMenu.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";

    let className: string = "";
    export { className as class };

    export let suggestions: string[];

    let dropdown: Dropdown | undefined;
    let selected: number | null = null;

    // blue highlight
    let active: boolean = false;

    const dispatch = createEventDispatcher();

    const createAutocomplete =
        (createDropdown: (target: HTMLElement) => Dropdown) =>
        (target: HTMLElement): void => {
            dropdown = createDropdown(target);
        };

    function selectPrevious() {
        if (selected === null) {
            selected = suggestions.length - 1;
        } else if (selected === 0) {
            selected = null;
        } else {
            selected--;
        }

        const choice = suggestions[selected ?? -1];
        dispatch("dropdown", { choice });
    }

    function selectNext() {
        if (selected === null) {
            selected = 0;
        } else if (selected >= suggestions.length - 1) {
            selected = null;
        } else {
            selected++;
        }

        const choice = suggestions[selected ?? -1];
        dispatch("autocomplete", { choice });
    }

    function chooseSelected() {
        active = true;
    }

    async function update() {
        dropdown = dropdown as Dropdown;
        dropdown.update();
        dispatch("update");

        await tick();

        if (suggestions.length > 0) {
            dropdown.show();
        } else {
            dropdown.hide();
        }
    }

    const autocomplete = {
        hide: () => dropdown!.hide(),
        show: () => dropdown!.show(),
        toggle: () => dropdown!.toggle(),
        selectPrevious,
        selectNext,
        chooseSelected,
        update,
    };

    onDestroy(() => dropdown?.dispose());
</script>

<WithDropdownMenu let:menuId let:createDropdown>
    <slot createAutocomplete={createAutocomplete(createDropdown)} {autocomplete} />

    <DropdownMenu id={menuId} class={className}>
        {#each suggestions as suggestion, i}
            <div class="suggestion-item">
                <DropdownItem
                    class={i === selected ? (active ? "active" : "focus") : ""}
                    on:click>{suggestion}</DropdownItem
                >
            </div>
        {/each}
    </DropdownMenu>
</WithDropdownMenu>

<style lang="scss">
    .suggestion-item {
        :global(.dropdown-item:hover) {
            background-color: inherit !important;
            border-color: inherit !important;
        }
    }
</style>
