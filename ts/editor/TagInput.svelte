<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, onDestroy, createEventDispatcher, tick } from "svelte";
    import { normalizeTagname } from "./tags";

    export let id: string | undefined = undefined;
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

    function isEmpty(): boolean {
        return name.length === 0;
    }

    function normalize(): void {
        name = normalizeTagname(name);
    }

    async function joinWithPreviousTag(event: Event): Promise<void> {
        const length = input.value.length;
        dispatch("tagjoinprevious");

        await tick();
        setPosition(input.value.length - length);

        event.preventDefault();
    }

    async function onBackspace(event: KeyboardEvent): Promise<void> {
        if (caretAtStart()) {
            joinWithPreviousTag(event);
        } else if (name.endsWith("::")) {
            name = name.slice(0, -2);
            event.preventDefault();
        }
    }

    async function joinWithNextTag(event: Event): Promise<void> {
        const length = input.value.length;
        dispatch("tagjoinnext");

        await tick();
        setPosition(length);

        event.preventDefault();
    }

    async function onDelete(event: KeyboardEvent): Promise<void> {
        if (caretAtEnd()) {
            joinWithNextTag(event);
        } else if (name.endsWith("::")) {
            name = name.slice(0, -2);
            event.preventDefault();
        }
    }

    function onBlur(event: Event): void {
        event.preventDefault();
        normalize();
        console.log("taginput onblur");
        if (name.length === 0) {
            dispatch("tagdelete");
        }

        dispatch("tagaccept");
    }

    function onEnter(event: Event): void {
        event.preventDefault();
        dispatch("tagsplit", { start: input.selectionStart, end: input.selectionEnd });
        event.preventDefault();
    }

    function onKeydown(event: KeyboardEvent): void {
        switch (event.code) {
            case "Enter":
                onEnter(event);
                break;
            case "Space":
                // TODO
                name += "::";
                event.preventDefault();
                break;

            case "Backspace":
                onBackspace(event);
                break;
            case "Delete":
                onDelete(event);
                break;

            case "ArrowLeft":
                if (!caretAtStart()) {
                    break;
                }
                normalize();
                if (isEmpty()) {
                    joinWithPreviousTag(event);
                } else {
                    event.preventDefault();
                    dispatch("tagmoveprevious");
                }
                break;

            case "ArrowRight":
                if (!caretAtEnd()) {
                    break;
                }
                if (isEmpty()) {
                    joinWithNextTag(event);
                } else {
                    event.preventDefault();
                    dispatch("tagmovenext");
                }
                break;
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

    onMount(() => input.focus());
</script>

<label class="ps-2 pe-1" data-value={name}>
    <input
        {id}
        bind:this={input}
        bind:value={name}
        type="text"
        tabindex="-1"
        size="1"
        on:focus
        on:blur={onBlur}
        on:keydown={onKeydown}
        on:keydown
        on:input
        on:paste={onPaste}
    />
</label>

<style lang="scss">
    label {
        display: inline-grid;
        height: 100%;

        &::after,
        input {
            color: var(--text-fg);
            background: none;
            resize: none;
            appearance: none;
            width: auto;
            grid-area: 1 / 1;
            font: inherit;
            /* TODO we need something like --base-font-size for buttons' 13px */
            font-size: 13px;
            outline: none;
            border: none;
            margin: 0;
            /* adjust so deleting all tags does not cause a reflow */
            padding: 1.5px 0;
        }

        &::after {
            /* 7 spaces to minimize reflow on clicking tag */
            content: attr(data-value) "       ";
            visibility: hidden;
            white-space: pre-wrap;
            position: relative;
            top: -2rem;
        }
    }
</style>
