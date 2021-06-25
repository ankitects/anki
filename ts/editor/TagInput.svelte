<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, onMount } from "svelte";
    import { normalizeTagname } from "./tags";

    export let name: string;
    export let input: HTMLInputElement;

    const dispatch = createEventDispatcher();

    function onAccept(): void {
        name = normalizeTagname(name);
        dispatch("tagupdate", { name });
    }

    function onBackspace(event: KeyboardEvent) {
        if (input.selectionStart === 0 && input.selectionEnd === 0) {
            dispatch("tagjoinprevious");
            event.preventDefault();
        } else if (name.endsWith("::")) {
            name = name.slice(0, -2);
            event.preventDefault();
        }
    }

    function onDelete(event: KeyboardEvent) {
        if (
            input.selectionStart === input.value.length &&
            input.selectionEnd === input.value.length
        ) {
            dispatch("tagjoinnext");
            event.preventDefault();
        } else if (name.endsWith("::")) {
            name = name.slice(0, -2);
            event.preventDefault();
        }
    }

    function onKeydown(event: KeyboardEvent): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        } else if (event.code === "Backspace") {
            onBackspace(event);
        } else if (event.code === "Delete") {
            onDelete(event);
        } else if (event.code === "Enter") {
            onAccept();
            event.preventDefault();
        }
    }

    function onPaste(event: ClipboardEvent): void {
        event.preventDefault();

        if (!event.clipboardData) {
            return;
        }

        const pasted = name + event.clipboardData.getData("text/plain");
        const splitted = pasted
            .split(/\s+/)
            .map(normalizeTagname)
            .filter((name: string) => name.length > 0);

        if (splitted.length === 0) {
            return;
        } else if (splitted.length === 1) {
            name = splitted.shift()!;
        } else {
            name = splitted.shift()!;
            dispatch("tagadd");

            const last = splitted.pop()!;

            for (const pastedName of splitted) {
                name = pastedName;
                dispatch("tagadd");
            }

            name = last;
        }
    }

    onMount(() => dispatch("mount", { input }));
</script>

<label class="ps-2 pe-1" data-value={name}>
    <input
        bind:this={input}
        type="text"
        tabindex="-1"
        size="1"
        bind:value={name}
        on:focus
        on:focusout
        on:blur={onAccept}
        on:keydown={onKeydown}
        on:paste={onPaste}
        on:click
    />
</label>

<style lang="scss">
    label {
        display: inline-grid;
        height: 100%;

        cursor: text;

        &:focus-within {
            cursor: default;
        }

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
