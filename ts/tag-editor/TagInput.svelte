<script lang="typescript">
    import TagAutocomplete from "./TagAutocomplete.svelte";
    import { createEventDispatcher } from "svelte";
    import { normalizeTagname } from "./helpers";

    export let name: string;
    export let input: HTMLInputElement;

    const dispatch = createEventDispatcher();

    let label: HTMLLabelElement;
    let active = false;

    function onFocus(): void {
        active = true;
    }

    function onBlur(): void {
        console.log('foo')
        active = false;
        dispatch("update", { tagname: normalizeTagname(name) });
    }

    function onKeydown(event: KeyboardEvent): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        } else if (event.code === "Backspace" && name.endsWith("::")) {
            name = name.slice(0, -2);
            event.preventDefault();
        } else if (event.code === "Enter") {
            onBlur();
            event.preventDefault();
        }
    }

    function onPaste({ clipboardData }: ClipboardEvent): void {
        const pasted = name + clipboardData.getData("text/plain");
        const splitted = pasted.split(" ");
        const last = splitted.pop();

        for (const token of splitted) {
            const tagname = normalizeTagname(token);

            if (tagname) {
                dispatch("add", { tagname });
            }
        }

        name = last;
    }
</script>

<style lang="scss">
    label {
        display: inline-grid;

        &::after,
        input {
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

<label data-value={name} bind:this={label}>
    <input
        type="text"
        size="1"
        bind:this={input}
        bind:value={name}
        on:focus={onFocus}
        on:blur={onBlur}
        on:keydown={onKeydown}
        on:paste={onPaste}
        on:click|stopPropagation />
</label>

{#if active}
    <TagAutocomplete {name} target={input} container={label} />
{/if}
