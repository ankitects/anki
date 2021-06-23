<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, onMount, onDestroy } from "svelte";
    import Dropdown from "bootstrap/js/dist/dropdown";

    export let name: string;

    const dispatch = createEventDispatcher();
    const triggerId = "tagLabel" + String(Math.random()).slice(2);
    const triggerClass = "dropdown-toggle";

    let originalName = name;
    let menu: HTMLDivElement;
    let dropdown;
    let activeItem = -1;

    const tagSuggestions = ["en::idioms", "anki::functionality", "math"];
    $: tagValues = [...tagSuggestions, originalName];

    onMount(() => {
        const toggle = menu.querySelector(`#${triggerId}`)!;
        dropdown = new Dropdown(toggle, {
            reference: "parent",
        });
    });

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
            const dropdownActive = dropdown._element.classList.contains("show");
            if (dropdownActive) {
                if (typeof activeItem === "number") {
                    name = tagValues[activeItem];
                    activeItem = null;
                }
                dropdown.hide();
            } else {
                dispatch("accept");
            }
        }
    }
</script>

<div class="dropdown" bind:this={menu} on:keydown={onKeydown}>
    <slot {triggerId} {triggerClass} {dropdown} />

    <ul class="dropdown-menu" aria-labelledby={triggerId}>
        {#each tagSuggestions as tag, index}
            <li>
                <a
                    href="#/"
                    class="dropdown-item"
                    class:dropdown-item-active={activeItem === index}
                    on:click={onItemClick}
                >
                    {tag}
                </a>
            </li>
        {/each}
    </ul>
</div>

<style lang="scss">
    :global(.show).dropdown-menu {
        display: flex;
        flex-direction: column-reverse;
    }
    .dropdown-item {
        padding: 0rem 0.3rem;
        font-size: smaller;
        &:focus {
            outline: none;
        }
    }
    .dropdown-item:hover {
        background-color: #c3c5c7;
    }
    .dropdown-item-active {
        color: #1e2125;
        background-color: #c3c5c7;
    }
</style>
