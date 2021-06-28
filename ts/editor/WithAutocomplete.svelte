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

    export let suggestionsPromise: Promise<string[]>;

    let target: HTMLElement;
    let dropdown: Dropdown;
    let autocomplete: any;
    let selected: number | null = null;

    // blue highlight
    let active: boolean = false;

    const dispatch = createEventDispatcher();

    function selectNext() {
        suggestionsPromise.then((suggestions) => {
            if (selected === null) {
                selected = 0;
            } else if (selected >= suggestions.length - 1) {
                selected = null;
            } else {
                selected++;
            }

            dispatch("autocomplete", { selected: suggestions[selected ?? -1] });
        });
    }

    function selectPrevious() {
        suggestionsPromise.then((suggestions) => {
            if (selected === null) {
                selected = suggestions.length - 1;
            } else if (selected === 0) {
                selected = null;
            } else {
                selected--;
            }

            dispatch("autocomplete", { selected: suggestions[selected ?? -1] });
        });
    }

    function chooseSelected() {
        active = true;
    }

    async function update() {
        dropdown = dropdown as Dropdown;
        dropdown.update();
        dispatch("update");

        await tick();

        suggestionsPromise.then((suggestions) => {
            if (suggestions.length > 0) {
                dropdown.show();
                // disabled class will tell Bootstrap not to show menu on clicking
                target.classList.remove("disabled");
            } else {
                dropdown.hide();
                target.classList.add("disabled");
            }
        });
    }

    const createAutocomplete =
        (createDropdown: (element: HTMLElement) => Dropdown) =>
        (element: HTMLElement): any => {
            target = element;
            dropdown = createDropdown(element);
            autocomplete = {
                hide: dropdown.hide.bind(dropdown),
                show: dropdown.show.bind(dropdown),
                toggle: dropdown.toggle.bind(dropdown),
                isVisible: (dropdown as any).isVisible,
                selectPrevious,
                selectNext,
                chooseSelected,
                update,
            };

            return autocomplete;
        };

    onDestroy(() => dropdown?.dispose());
</script>

<WithDropdownMenu let:menuId let:createDropdown>
    <slot createAutocomplete={createAutocomplete(createDropdown)} {autocomplete} />

    <DropdownMenu id={menuId} class={className}>
        {#await suggestionsPromise}
            <div class="suggestion-item">
                <DropdownItem>...</DropdownItem>
            </div>
        {:then suggestions}
            {#each suggestions as suggestion, i}
                <div class="suggestion-item">
                    <DropdownItem
                        class={i === selected ? (active ? "active" : "focus") : ""}
                        on:click>{suggestion}</DropdownItem
                    >
                </div>
            {/each}
        {/await}
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
