<script lang="typescript">
    import { onMount } from "svelte";

    export let name: string;
    export let input: HTMLInputElement;

    let menu: HTMLDivElement;

    const tagSuggestions = ["en::idioms", "anki::functionality", "math"];

    onMount(() => {
        const popover = new bootstrap.Popover(input, {
            trigger: "manual",
            content: menu,
        });

        popover.show();
    });
</script>

<div bind:this={menu} class="dropdown-menu">
    <span>{name}</span>:
    {#each tagSuggestions as tag}<span class="dropdown-item">{tag}</span>{/each}
</div>
