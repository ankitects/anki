<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher, onMount, tick } from "svelte";
    import { normalizeTagname } from "./tags";

    export let name: string;
    export let input: HTMLInputElement;

    const dispatch = createEventDispatcher();

    function caretAtStart(): boolean {
        return input.selectionStart === 0 && input.selectionEnd === 0;
    }

    function caretAtEnd(): boolean {
        return (
            input.selectionStart === input.value.length &&
            input.selectionEnd === input.value.length
        );
    }

    function setPosition(position: number): void {
        input.setSelectionRange(position, position);
    }

    function onAccept(): void {
        name = normalizeTagname(name);
        dispatch("tagupdate", { name });
    }

    async function onBackspace(event: KeyboardEvent): Promise<void> {
        if (caretAtStart()) {
            const length = input.value.length;
            dispatch("tagjoinprevious");
            await tick();
            setPosition(input.value.length - length);
            event.preventDefault();
        } else if (name.endsWith("::")) {
            name = name.slice(0, -2);
            event.preventDefault();
        }
    }

    async function onDelete(event: KeyboardEvent): Promise<void> {
        if (caretAtEnd()) {
            const length = input.value.length;
            dispatch("tagjoinnext");
            await tick();
            setPosition(length);
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
        } else if (event.code === "ArrowLeft" && caretAtStart()) {
            dispatch("tagmoveprevious");
            event.preventDefault();
        } else if (event.code === "ArrowRight" && caretAtEnd()) {
            dispatch("tagmovenext");
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
        bind:value={name}
        type="text"
        tabindex="-1"
        size="1"
        on:focus
        on:blur
        on:blur={onAccept}
        on:keydown={onKeydown}
        on:keydown
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
            /* adjust so deleting all tags does not cause a reflow */
            padding: 1.5px 0;
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
