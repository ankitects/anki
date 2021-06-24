<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";

    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";

    export let name: string;
    export const suggestions = ["en::idioms", "anki::functionality", "math"];

    const dispatch = createEventDispatcher();
    const triggerId = "tagLabel" + String(Math.random()).slice(2);
    const triggerClass = "dropdown-toggle";

    let menu: HTMLDivElement;
    let dropdown;
    let activeItem = -1;

    $: tagValues = [...suggestions, name];

    function onItemClick(event: Event) {
        dispatch("nameChosen", { name: event.currentTarget!.innerText });
        event.stopPropagation();
        event.preventDefault();
    }

    function switchUpDown(event: KeyboardEvent): void {
        const target = event.currentTarget as HTMLButtonElement;
        console.log(
            event.code,
            target.nextElementSibling,
            target.previousElementSibling
        );
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

    /* else if (event.code === "Enter") { */
    /* const dropdownActive = dropdown._element.classList.contains("show"); */
    /* if (dropdownActive) { */
    /*     if (typeof activeItem === "number") { */
    /*         name = tagValues[activeItem]; */
    /*         activeItem = null; */
    /*     } */
    /*     dropdown.hide(); */
    /* } else { */
    /*     dispatch("accept"); */
    /* } */
    /* } */
</script>

<div bind:this={menu} class="dropup dropdown-reverse">
    <slot {triggerId} {triggerClass} {dropdown} />

    <DropdownMenu labelledby={triggerId}>
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
