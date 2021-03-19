<script lang="typescript">
    import { createEventDispatcher } from "svelte";

    export let name: string;
    export let input: HTMLInputElement;
    let inputSizer: HTMLLabelElement;

    const dispatch = createEventDispatcher();

    function onKeydown(event: KeyboardEvent): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        }
    }
</script>

<style lang="scss">
    label {
        display: inline-grid;

        &::after, input {
            color: var(--text-fg);
            background: none;
            resize: none;
            appearance: none;

            width: auto;
            grid-area: 1 / 1;
            font: inherit;

            outline: none;
            border: none;
            margin: 0;
            padding: 0;
        }

        &::after {
            /* 8 spaces to minimize reflow on clicking tag */
            content: attr(data-value) "        ";
            visibility: hidden;
            white-space: pre-wrap;

            position: relative;
            top: -2rem;
        }
    }
</style>

<label data-value={name}>
    <input
        type="text"
        size="1"
        bind:value={name}
        bind:this={input}
        on:keydown={onKeydown}
        on:blur
        />
</label>
