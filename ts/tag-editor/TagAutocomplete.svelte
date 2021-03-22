<script lang="typescript">
    import { onMount, onDestroy } from "svelte";

    export let triggerId: string;
    export let name: string;

    let menu: HTMLDivElement;
    let dropdown;

    console.log(name)
    const tagSuggestions = ["en::idioms", "anki::functionality", "math"];

    onMount(() => {
        dropdown = new bootstrap.Dropdown(menu.querySelector('.dropdown-toggle'))
        console.log('dropdown', dropdown)
    })
</script>

<style lang="scss">
    .dropdown-item {
        padding: 0rem .3rem;
        font-size: smaller;

        &:focus {
            outline: none;
        }
    }
</style>

<div class="dropdown" bind:this={menu}>
    <slot {dropdown}></slot>

    <ul class="dropdown-menu" aria-labelledby={triggerId}>
        {#each tagSuggestions as tag}
            <li tabindex="0"><span class="dropdown-item">{tag}</span></li>
        {/each}
    </ul>
</div>
