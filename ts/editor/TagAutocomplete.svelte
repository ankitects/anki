<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type Dropdown from "bootstrap/js/dist/dropdown";

    import WithDropdownMenu from "components/WithDropdownMenu.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";

    export let suggestions: string[];

    let className: string = "";
    export { className as class };

    let autocomplete: Dropdown | undefined;
    let original: string | undefined;

    let displayed: string[] = [];
    let selected: number | null = null;
    let active: boolean = false;

    function select(index: number) {
        selected = index;
    }

    function choose() {}

    function switchUpDown(event: KeyboardEvent): void {
        const target = event.currentTarget as HTMLButtonElement;
        if (event.code === "ArrowUp") {
            if (target.nextElementSibling) {
                (target.nextElementSibling as HTMLButtonElement).focus();
            }

            event.preventDefault();
        } else if (event.code === "ArrowDown") {
            if (target.previousElementSibling) {
                (target.previousElementSibling as HTMLButtonElement).focus();
            }

            event.preventDefault();
        }
    }

    const updateAutocomplete =
        (createDropdown: (element: HTMLElement) => Dropdown) =>
        (event: KeyboardEvent): Dropdown => {
            const target = event.target as HTMLInputElement;
            autocomplete = createDropdown(target);
            autocomplete.show();

            displayed = suggestions;

            if (
                event.code === "ArrowDown" ||
                (event.code === "Tab" && event.shiftKey)
            ) {
                event.preventDefault();
                if (selected === null) {
                    selected = displayed.length - 1;
                } else if (selected === 0) {
                    selected = null;
                } else {
                    selected--;
                }
            } else if (event.code === "ArrowUp" || event.code === "Tab") {
                event.preventDefault();
                if (selected === null) {
                    selected = 0;
                } else if (selected >= displayed.length - 1) {
                    selected = null;
                } else {
                    selected++;
                }
            } else if (event.code === "Enter") {
                event.preventDefault();
                active = true;
            } else {
                original = target.value;
            }

            return autocomplete;
        };

    function destroyAutocomplete(): void {
        if (!autocomplete) {
            return;
        }

        autocomplete.hide();
        selected = null;
        active = false;
    }
</script>

<WithDropdownMenu let:menuId let:createDropdown>
    <slot
        updateAutocomplete={updateAutocomplete(createDropdown)}
        {destroyAutocomplete}
        selected={displayed[selected ?? -1]}
    />

    <DropdownMenu id={menuId} class={className}>
        {#each displayed as tag, i}
            <DropdownItem
                class={i === selected ? (active ? "active" : "focus") : ""}
                on:mouseenter={() => select(i)}
                on:click>{tag}</DropdownItem
            >
        {/each}
    </DropdownMenu>
</WithDropdownMenu>
