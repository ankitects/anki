<script lang="typescript">
    import { createEventDispatcher, onMount, onDestroy } from "svelte";

    export let name: string;

    const dispatch = createEventDispatcher();

    const triggerId = "tagLabel" + String(Math.random()).slice(2);
    const triggerClass = "dropdown-toggle";

    let menu: HTMLDivElement;
    let dropdown;

    let activeItem = null;

    const tagSuggestions = ["en::idioms", "anki::functionality", "math", name];

    onMount(() => {
        const toggle = menu.querySelector(`#${triggerId}`)
        dropdown = new bootstrap.Dropdown(toggle, {
            reference: 'parent',
        })
    })

    function onItemClick(event: ClickEvent) {
        dispatch("nameChosen", { name: event.currentTarget.innerText })
        event.stopPropagation()
        event.preventDefault()
    }

    function onKeydown(event: KeyboardEvent) {
        if (event.code === "ArrowUp") {
            activeItem = activeItem === null || activeItem === tagSuggestions.length - 1
                ? 0
                : ++activeItem;
            event.preventDefault();
        }

        else if (event.code === "ArrowDown") {
            activeItem = activeItem === null || activeItem === 0
                ? tagSuggestions.length - 1
                : --activeItem;
            event.preventDefault();
        }

        else if (event.code === "Enter") {
            const dropdownActive = dropdown._element.classList.contains("show")
            console.log('dropdownactive', dropdownActive)

            if (dropdownActive) {
                if (typeof activeItem === "number") {
                    name = tagSuggestions[activeItem];
                    activeItem = null;
                }

                dropdown.hide();
            }
            else {
                dispatch("accept")
            }
        }
    }
</script>

<style lang="scss">
    :global(.show).dropdown-menu {
        display: flex;
        flex-direction: column-reverse;
    }

    .dropdown-item {
        padding: 0rem .3rem;
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

<div class="dropdown" bind:this={menu} on:keydown={onKeydown}>
    <slot {triggerId} {triggerClass} {dropdown}></slot>

    <ul class="dropdown-menu" aria-labelledby={triggerId}>
        {#each tagSuggestions as tag, index}
            <li>
                <a
                    href="#/"
                    class="dropdown-item"
                    class:dropdown-item-active={activeItem === index}
                    on:click={onItemClick}>
                    {tag}
                </a>
            </li>
        {/each}
    </ul>
</div>
