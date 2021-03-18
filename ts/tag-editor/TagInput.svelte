<script lang="typescript">
    import { createEventDispatcher, onMount } from "svelte";

    export let name: string;

    const dispatch = createEventDispatcher();
    let input: HTMLInputElement?;

    function onKeydown(event: KeyboardEvent): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        }
    }

    onMount(() => {
        /* dispatch("mount", { input }); */
    });
</script>

<style lang="scss">
    input {
        color: var(--text-fg);
        background-color: transparent;

        border: none;

        &:focus {
            outline: none;
        }
    }
</style>

<input type="text" bind:value={name} bind:this={input} on:keydown={onKeydown} on:blur />
