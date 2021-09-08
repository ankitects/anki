<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, onDestroy, tick } from "svelte";

    import type Dropdown from "bootstrap/js/dist/dropdown";

    import WithDropdown from "components/WithDropdown.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import AutocompleteItem from "./AutocompleteItem.svelte";

    let className: string = "";
    export { className as class };

    export let drop: "down" | "up" | "left" | "right" = "down";
    export let suggestionsPromise: Promise<string[]>;

    let target: HTMLElement;
    let dropdown: Dropdown;

    let suggestionsItems: string[] = [];
    $: suggestionsPromise.then((items) => {
        if (items.length > 0) {
            // disabled class will tell Bootstrap not to show menu on clicking
            target.classList.remove("disabled");
            dropdown.show();
        } else {
            dropdown.hide();
            target.classList.add("disabled");
        }

        suggestionsItems = items;
    });

    let selected: number | null = null;
    let active: boolean = false;

    const dispatch = createEventDispatcher();

    /**
     * select as currently highlighted item
     */
    async function selectNext() {
        if (selected === null) {
            selected = 0;
        } else if (selected >= suggestionsItems.length - 1) {
            selected = null;
        } else {
            selected++;
        }

        dispatch("select", { selected: suggestionsItems[selected ?? -1] });
    }

    async function selectPrevious() {
        if (selected === null) {
            selected = suggestionsItems.length - 1;
        } else if (selected === 0) {
            selected = null;
        } else {
            selected--;
        }

        dispatch("select", { selected: suggestionsItems[selected ?? -1] });
    }

    /**
     * choose as accepted suggestion
     */
    async function chooseSelected() {
        active = true;
        dispatch("choose", { chosen: suggestionsItems[selected ?? -1] });
    }

    async function update() {
        dropdown = dropdown as Dropdown;
        dropdown.update();
        await tick();

        dispatch("update");
    }

    function hasSelected(): boolean {
        return selected !== null;
    }

    const createAutocomplete =
        (createDropdown: (element: HTMLElement) => Dropdown) =>
        (element: HTMLElement): any => {
            dropdown = createDropdown(element);
            target = element;

            const api = {
                hide: dropdown.hide,
                show: dropdown.show,
                toggle: dropdown.toggle,
                isVisible: (dropdown as any).isVisible,
                selectPrevious,
                selectNext,
                chooseSelected,
                hasSelected,
                update,
            };

            return api;
        };

    function setSelected(index: number): void {
        selected = index;
        active = true;
    }

    function setSelectedAndActive(index: number): void {
        setSelected(index);
    }

    async function selectIndex(index: number): Promise<void> {
        active = false;
        dispatch("select", { selected: suggestionsItems[index] });
    }

    function selectIfMousedown(event: MouseEvent, index: number): void {
        if (event.buttons === 1) {
            setSelected(index);
        }
    }

    onDestroy(() => dropdown?.dispose());
</script>

<WithDropdown {drop} toggleOpen={false} let:createDropdown>
    <slot createAutocomplete={createAutocomplete(createDropdown)} />

    <DropdownMenu class={className}>
        {#each suggestionsItems as suggestion, index}
            <AutocompleteItem
                selected={index === selected}
                active={index === selected && active}
                on:mousedown={() => setSelectedAndActive(index)}
                on:mouseup={() => {
                    selectIndex(index);
                    chooseSelected();
                }}
                on:mouseenter={(event) => selectIfMousedown(event, index)}
                on:mouseleave={() => (active = false)}>{suggestion}</AutocompleteItem
            >
        {/each}
    </DropdownMenu>
</WithDropdown>
