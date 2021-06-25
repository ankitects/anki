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

    const createAutocomplete =
        (createDropdown: (element: HTMLElement) => Dropdown) =>
        (element: HTMLElement) => {
            autocomplete = createDropdown(element);
            return autocomplete;
        };
</script>

<WithDropdownMenu let:menuId let:createDropdown>
    <slot createAutocomplete={createAutocomplete(createDropdown)} />

    <DropdownMenu id={menuId} class={className}>
        {#each suggestions as tag}
            <DropdownItem>{tag}</DropdownItem>
        {/each}
    </DropdownMenu>
</WithDropdownMenu>
