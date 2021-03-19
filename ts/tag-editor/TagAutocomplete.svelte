<script lang="typescript">
    import { onMount } from "svelte";

    export let name: string;

    let menu: HTMLDivElement;

    const tagSuggestions = ["en::idioms", "anki::functionality", "math"];

    onMount(() => {
        const popover = new bootstrap.Popover(menu, {
            trigger: "manual",
            title: "foo",
            content: "test",
        });

        console.log(name);
        popover.show();
    });
</script>

<div bind:this={menu} class="dropdown-menu">
    {#each tagSuggestions as tag}<span class="dropdown-item">{tag}</span>{/each}
</div>
