<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";

    export let suggestions: string[];

    let className: string = "";
    export { className as class };

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
</script>

<WithDropdownMenu let:menuId let:createDropdown let:activateDropdown>
    <slot {createDropdown} {activateDropdown} />

    <DropdownMenu id={menuId} class={className}>
        {#each suggestions as tag}
            <DropdownItem on:keydown={switchUpDown}>{tag}</DropdownItem>
        {/each}
    </DropdownMenu>
</WithDropdownMenu>
