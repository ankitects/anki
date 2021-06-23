<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import TagAutocomplete from "./TagAutocomplete.svelte";
    import { normalizeTagname } from "./tags";

    import type Dropdown from "bootstrap/js/dist/dropdown";

    export let name: string;
    export let input: HTMLInputElement;

    const dispatch = createEventDispatcher();

    function onFocus(event: FocusEvent, dropdown: Dropdown): void {
        dropdown.show();
    }

    function onAccept(event: Event): void {
        dispatch("update", { tagname: normalizeTagname(name) });
    }

    function dropdownBlur(event: Event, dropdown: Dropdown): void {
        onAccept(event);
        dropdown.hide();
    }

    function onKeydown(event: KeyboardEvent, dropdown: Dropdown): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        } else if (event.code === "Backspace" && name.endsWith("::")) {
            name = name.slice(0, -2);
            event.preventDefault();
        }
    }

    function onPaste({ clipboardData }: ClipboardEvent): void {
        const pasted = name + clipboardData!.getData("text/plain");
        const splitted = pasted.split(" ");
        const last = splitted.pop();
        for (const token of splitted) {
            const tagname = normalizeTagname(token);
            if (tagname) {
                dispatch("add", { tagname });
            }
        }
        name = last!;
    }

    function setTagname({ detail }: CustomEvent): void {
        name = detail.name;
    }
</script>

<TagAutocomplete
    bind:name
    let:triggerId
    let:triggerClass
    let:dropdown
    on:nameChosen={setTagname}
    on:accept={onAccept}
>
    <label data-value={name} id={triggerId} class={triggerClass}>
        <input
            type="text"
            size="1"
            bind:this={input}
            bind:value={name}
            on:focus={(event) => onFocus(event, dropdown)}
            on:blur={(event) => dropdownBlur(event, dropdown)}
            on:focusout
            on:keydown={(event) => onKeydown(event, dropdown)}
            on:paste={onPaste}
            on:click
        />
    </label>
</TagAutocomplete>

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
