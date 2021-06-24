<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, onMount, onDestroy } from "svelte";

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

    function onKeydown(event: KeyboardEvent) {
        if (event.code === "ArrowUp") {
            activeItem = activeItem === tagValues.length - 1 ? 0 : ++activeItem;
            name = tagValues[activeItem];
            event.preventDefault();
        } else if (event.code === "ArrowDown") {
            activeItem = activeItem === 0 ? tagValues.length - 1 : --activeItem;
            name = tagValues[activeItem];
            event.preventDefault();
        } else if (event.code === "Enter") {
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
        }
    }
</script>

<div bind:this={menu} class="dropdown dropdown-reverse" on:keydown={onKeydown}>
    <slot {triggerId} {triggerClass} {dropdown} />

    <DropdownMenu labelledby={triggerId}>
        {#each suggestions as tag}
            <DropdownItem>{tag}</DropdownItem>
        {/each}
    </DropdownMenu>
</div>

<style lang="scss">
    .dropdown-reverse :global(.dropdown-menu) {
        display: flex;
        flex-direction: column-reverse;
    }
</style>
