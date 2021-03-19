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
    span {
        color: var(--text-fg);
        background: none;

        &:focus {
            outline: none;
        }
    }
</style>

<span
     contenteditable
     bind:textContent={name}
     bind:this={input}
     on:keydown={onKeydown}
     on:blur
 />
