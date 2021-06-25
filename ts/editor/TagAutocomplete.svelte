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
    let displayed: string[] = [];

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
            const target = event.target as HTMLElement;
            autocomplete = createDropdown(target);
            autocomplete.show();

            return autocomplete;
        };

    function destroyAutocomplete(): void {
        if (!autocomplete) {
            return;
        }

        autocomplete.hide();
    }
</script>

<WithDropdownMenu let:menuId let:createDropdown>
    <slot
        updateAutocomplete={updateAutocomplete(createDropdown)}
        {destroyAutocomplete}
    />

    <DropdownMenu id={menuId} class={className}>
        {#each suggestions as tag}
            <DropdownItem>{tag}</DropdownItem>
        {/each}
    </DropdownMenu>
</WithDropdownMenu>
