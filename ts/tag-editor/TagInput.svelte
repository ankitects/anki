<script lang="typescript">
    import { createEventDispatcher } from "svelte";

    export let name: string;
    export let input: HTMLInputElement;

    const dispatch = createEventDispatcher();

    function onKeydown(event: KeyboardEvent): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        }
    }
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
