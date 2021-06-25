<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";

    export const suggestions = ["en::idioms", "anki::functionality", "math"];
    export let size: number;

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

    function updateActiveItem(even: FocusEvent): void {}
</script>

<ButtonToolbar class={`dropup ${className}`} {size}>
    <slot />

    <DropdownMenu class="d-flex flex-column-reverse">
        {#each suggestions as tag}
            <DropdownItem on:focus={updateActiveItem} on:keydown={switchUpDown}
                >{tag}</DropdownItem
            >
        {/each}
    </DropdownMenu>
</ButtonToolbar>
