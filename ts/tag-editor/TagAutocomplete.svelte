<script lang="typescript">
    import { onMount, onDestroy } from "svelte";

    export let container: HTMLElement;
    export let target: HTMLElement;
    export let name: string;

    let menu: HTMLDivElement;
    let popover;

    const tagSuggestions = ["en::idioms", "anki::functionality", "math"];

    onMount(() => {
        popover = new bootstrap.Popover(target, {
            trigger: "manual",
            container,
            content: menu,
        });

        popover.show();
    });

    onDestroy(() => {
        popover.dispose();
    })
</script>

<div bind:this={menu} class="dropdown-menu">
    <span>{name}</span>:
    {#each tagSuggestions as tag}<span class="dropdown-item">{tag}</span>{/each}
</div>
