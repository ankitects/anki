<script lang="typescript">
    import { createEventDispatcher } from "svelte";

    import TagAutocomplete from "./TagAutocomplete.svelte";
    import { normalizeTagname } from "./helpers";

    export let name: string;
    export let input: HTMLInputElement;

    const dispatch = createEventDispatcher();

    function onFocus(event: FocusEvent, dropdown: bootstrap.Dropdown): void {
        dropdown.show();
    }

    function onBlur(event: Event, dropdown: bootstrap.Dropdown): void {
        dropdown.hide();
        dispatch("update", { tagname: normalizeTagname(name) });
    }

    function onKeydown(event: KeyboardEvent, dropdown: bootstrap.Dropdown): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        }
        else if (event.code === "Backspace" && name.endsWith("::")) {
            name = name.slice(0, -2);
            event.preventDefault();
        }
        else if (event.code === "Enter") {
            onBlur(event, dropdown);
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

<TagAutocomplete {name} let:triggerId let:triggerClass let:dropdown>
    <label data-value={name} id={triggerId} class={triggerClass}>
        <input
            type="text"
            size="1"
            bind:this={input}
            bind:value={name}
            on:focus={(event) => onFocus(event, dropdown)}
            on:blur={(event) => onBlur(event, dropdown)}
            on:keydown={(event) => onKeydown(event, dropdown)}
            on:paste={onPaste}
            on:click|stopPropagation />
    </label>
</TagAutocomplete>
