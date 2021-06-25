<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";

    export const suggestions = ["en::idioms", "anki::functionality", "math"];

    let className: string = "";
    export { className as class };

    let menu: HTMLDivElement;

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

<div bind:this={menu} class={`dropup dropdown-reverse ${className}`}>
    <slot />

    <DropdownMenu>
        {#each suggestions as tag}
            <DropdownItem on:focus={updateActiveItem} on:keydown={switchUpDown}
                >{tag}</DropdownItem
            >
        {/each}
    </DropdownMenu>
</div>

<style lang="scss">
    .dropdown-reverse :global(.dropdown-menu.show) {
        display: flex;
        flex-direction: column-reverse;
        font-size: 13px;
    }
</style>
