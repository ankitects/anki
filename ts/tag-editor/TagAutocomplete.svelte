<script lang="typescript">
    import { onMount, onDestroy } from "svelte";

    export let name: string;

    let menu: HTMLDivElement;
    let dropdown;

    let triggerId = "tagLabel" + String(Math.random()).slice(2);
    let triggerClass = "dropdown-toggle";

    const tagSuggestions = ["en::idioms", "anki::functionality", "math", name];

    onMount(() => {
        const toggle = menu.querySelector(`#${triggerId}`)
        dropdown = new bootstrap.Dropdown(toggle)
    })
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
</style>

<div class="dropdown" bind:this={menu}>
    <slot {triggerId} {triggerClass} {dropdown}></slot>

    <ul class="dropdown-menu" aria-labelledby={triggerId}>
        {#each tagSuggestions as tag}
            <li><span class="dropdown-item" tabindex="-1">{tag}</span></li>
        {/each}
    </ul>
</div>
