<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, tick } from "svelte";

    import type Dropdown from "bootstrap/js/dist/dropdown";

    import WithDropdown from "../components/WithDropdown.svelte";
    import DropdownMenu from "../components/DropdownMenu.svelte";
    import AutocompleteItem from "./AutocompleteItem.svelte";

    let className: string = "";
    export { className as class };

    export let drop: "down" | "up" = "down";
    export let suggestionsPromise: Promise<string[]>;

    let dropdown: Dropdown;
    let show = false;

    let suggestionsItems: string[] = [];
    $: suggestionsPromise.then((items) => {
        show = items.length > 0;

        if (show) {
            dropdown.show();
        } else {
            dropdown.hide();
        }

        suggestionsItems = items;
    });

    let selected: number | null = null;
    let active: boolean = false;

    const dispatch = createEventDispatcher();

    /**
     * select as currently highlighted item
     */
    function incrementSelected(): void {
        if (selected === null) {
            selected = 0;
        } else if (selected >= suggestionsItems.length - 1) {
            selected = null;
        } else {
            selected++;
        }
    }

    function decrementSelected(): void {
        if (selected === null) {
            selected = suggestionsItems.length - 1;
        } else if (selected === 0) {
            selected = null;
        } else {
            selected--;
        }
    }

    async function updateSelected(): Promise<void> {
        dispatch("select", { selected: suggestionsItems[selected ?? -1] });
        await tick();
        dropdown.update();
    }

    async function selectNext(): Promise<void> {
        incrementSelected();
        await updateSelected();
    }

    async function selectPrevious(): Promise<void> {
        decrementSelected();
        await updateSelected();
    }

    /**
     * choose as accepted suggestion
     */
    async function chooseSelected() {
        active = true;
        dispatch("choose", { chosen: suggestionsItems[selected ?? -1] });

        await tick();
        show = false;
    }

    async function update() {
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

            const api = {
                hide: dropdown.hide,
                show: dropdown.show,
                toggle: dropdown.toggle,
                isVisible: (dropdown as any).isVisible,
                selectPrevious,
                selectNext,
                chooseSelected,
                update,
                hasSelected,
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

    let scroll: () => void;

    $: if (scroll) {
        scroll();
    }
</script>

<WithDropdown {drop} toggleOpen={false} let:createDropdown align="start">
    <slot createAutocomplete={createAutocomplete(createDropdown)} />

    <DropdownMenu class={className} {show}>
        {#each suggestionsItems as suggestion, index}
            {#if index === selected}
                <AutocompleteItem
                    bind:scroll
                    selected
                    {active}
                    on:mousedown={() => setSelectedAndActive(index)}
                    on:mouseup={() => {
                        selectIndex(index);
                        chooseSelected();
                    }}
                    on:mouseenter={(event) => selectIfMousedown(event, index)}
                    on:mouseleave={() => (active = false)}
                    >{suggestion}</AutocompleteItem
                >
            {:else}
                <AutocompleteItem
                    on:mousedown={() => setSelectedAndActive(index)}
                    on:mouseup={() => {
                        selectIndex(index);
                        chooseSelected();
                    }}
                    on:mouseenter={(event) => selectIfMousedown(event, index)}
                    >{suggestion}</AutocompleteItem
                >
            {/if}
        {/each}
    </DropdownMenu>
</WithDropdown>
