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

    let autocomplete: Dropdown | undefined;
    let selected: number | null = null;

    // blue highlight
    let active: boolean = false;

    const dispatch = createEventDispatcher();

    const updateAutocomplete =
        (createDropdown: (element: HTMLElement) => Dropdown) =>
        async (event: KeyboardEvent): Promise<void> => {
            const target = event.target as HTMLInputElement;

            if (!autocomplete) {
                autocomplete = createDropdown(target);
            }

            autocomplete.update();

            if (
                event.code === "ArrowDown" ||
                (event.code === "Tab" && event.shiftKey)
            ) {
                event.preventDefault();
                if (selected === null) {
                    selected = suggestions.length - 1;
                } else if (selected === 0) {
                    selected = null;
                } else {
                    selected--;
                }

                const choice = suggestions[selected ?? -1];
                dispatch("autocomplete", { choice });
            } else if (event.code === "ArrowUp" || event.code === "Tab") {
                event.preventDefault();
                if (selected === null) {
                    selected = 0;
                } else if (selected >= suggestions.length - 1) {
                    selected = null;
                } else {
                    selected++;
                }

                const choice = suggestions[selected ?? -1];
                dispatch("autocomplete", { choice });
            } else if (event.code === "Enter") {
                event.preventDefault();
                active = true;
            } else {
                dispatch("update");
            }

            await tick();

            if (suggestions.length > 0) {
                autocomplete.show();
            } else {
                autocomplete.hide();
            }
        };

    onDestroy(() => autocomplete?.dispose());
</script>

<WithDropdownMenu let:menuId let:createDropdown>
    <slot updateAutocomplete={updateAutocomplete(createDropdown)} />

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
