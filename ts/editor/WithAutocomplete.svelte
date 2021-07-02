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

    let selected: number | null = null;
    let active: boolean = false;

    const dispatch = createEventDispatcher();

    async function selectNext() {
        const suggestions = await suggestionsPromise;

        if (selected === null) {
            selected = 0;
        } else if (selected >= suggestions.length - 1) {
            selected = null;
        } else {
            selected++;
        }

        dispatch("select", { selected: suggestions[selected ?? -1] });
    }

    async function selectPrevious() {
        const suggestions = await suggestionsPromise;

        if (selected === null) {
            selected = suggestions.length - 1;
        } else if (selected === 0) {
            selected = null;
        } else {
            selected--;
        }

        dispatch("select", { selected: suggestions[selected ?? -1] });
    }

    async function chooseSelected() {
        const suggestions = await suggestionsPromise;

        active = true;
        dispatch("choose", { chosen: suggestions[selected ?? -1] });
    }

    async function update() {
        dropdown = dropdown as Dropdown;
        dropdown.update();
        dispatch("update");

        const [, suggestions] = await Promise.all([tick(), suggestionsPromise]);

        if (suggestions.length > 0) {
            dropdown.show();
            // disabled class will tell Bootstrap not to show menu on clicking
            target.classList.remove("disabled");
        } else {
            dropdown.hide();
            target.classList.add("disabled");
        }
    }

    const createAutocomplete =
        (createDropdown: (element: HTMLElement) => Dropdown) =>
        (element: HTMLElement): any => {
            target = element;
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
            };

            return api;
        };

    onDestroy(() => dropdown?.dispose());

    function setSelected(index: number): void {
        selected = index;
        active = true;
    }

    function setSelectedAndActive(index: number): void {
        setSelected(index);
    }

    async function selectIndex(index: number): Promise<void> {
        const suggestions = await suggestionsPromise;
        active = false;
        dispatch("select", { selected: suggestions[index] });
    }

    function selectIfMousedown(event: MouseEvent, index: number): void {
        if (event.buttons === 1) {
            setSelected(index);
        }
    }
</script>

<WithDropdown {drop} toggleOpen={false} let:createDropdown>
    <slot createAutocomplete={createAutocomplete(createDropdown)} />

    <DropdownMenu class={className}>
        {#await suggestionsPromise}
            <AutocompleteItem>...</AutocompleteItem>
        {:then suggestions}
            {#each suggestions as suggestion, index}
                <AutocompleteItem
                    selected={index === selected}
                    active={index === selected && active}
                    on:mousedown={() => setSelectedAndActive(index)}
                    on:mouseup={() => {
                        selectIndex(index);
                        chooseSelected();
                    }}
                    on:mouseenter={(event) => selectIfMousedown(event, index)}
                    on:mouseleave={() => (active = false)}
                    >{suggestion}</AutocompleteItem
                >
            {/each}
        {/await}
    </DropdownMenu>
</WithDropdown>
