<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher, tick } from "svelte";
    import {
        normalizeTagname,
        delimChar,
        replaceWithUnicodeSeparator,
        replaceWithColons,
    } from "./tags";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let name: string;
    export let input: HTMLInputElement;

    const dispatch = createEventDispatcher();

    function isCollapsed(): boolean {
        return input.selectionStart === input.selectionEnd;
    }

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

    async function joinWithPreviousTag(event: Event): Promise<void> {
        const length = input.value.length;
        dispatch("tagjoinprevious");

        await tick();
        setPosition(input.value.length - length);

        event.preventDefault();
    }

    async function maybeDeleteDelimiter(event: Event, position: number): Promise<void> {
        if (position > name.length) {
            return;
        }

        const nameUptoCaret = name.slice(0, position);

        if (nameUptoCaret.endsWith(delimChar)) {
            name = name.slice(0, position - 1) + name.slice(position, name.length);
            await tick();

            setPosition(position - 1);
            event.preventDefault();
        }
    }

    function onBackspace(event: KeyboardEvent): void {
        if (caretAtStart()) {
            joinWithPreviousTag(event);
        } else {
            maybeDeleteDelimiter(event, input.selectionStart!);
        }
    }

    async function joinWithNextTag(event: Event): Promise<void> {
        const length = input.value.length;
        dispatch("tagjoinnext");

        await tick();
        setPosition(length);

        event.preventDefault();
    }

    function onDelete(event: KeyboardEvent): void {
        if (caretAtEnd()) {
            joinWithNextTag(event);
        } else {
            maybeDeleteDelimiter(event, input.selectionStart! + 2);
        }
    }

    function onBlur(): void {
        name = normalizeTagname(name);

        if (name.length === 0) {
            dispatch("tagdelete");
        }

        dispatch("tagaccept");
    }

    function onEnter(event: Event): void {
        dispatch("tagsplit", { start: input.selectionStart, end: input.selectionEnd });
        event.preventDefault();
    }

    async function onDelimiter(event: Event, single: boolean = false): Promise<void> {
        const positionStart = input.selectionStart!;
        const positionEnd = input.selectionEnd!;

        const before = name.slice(0, positionStart);
        const after = name.slice(positionEnd, name.length);

        if (before.endsWith(delimChar)) {
            event.preventDefault();
            event.stopPropagation();
            return;
        } else if (before.endsWith(":")) {
            event.preventDefault();
            name = `${before.slice(0, -1)}${delimChar}${name.slice(
                positionEnd,
                name.length
            )}`;

            await tick();
            setPosition(positionStart);
            return;
        } else if (after.startsWith(":")) {
            event.preventDefault();
            name = `${before}${delimChar}${name.slice(positionEnd + 1, name.length)}`;
        } else if (single) {
            return;
        } else {
            event.preventDefault();
            name = `${before}${delimChar}${after}`;
        }

        await tick();
        setPosition(positionStart + 1);
    }

    function onKeydown(event: KeyboardEvent): void {
        switch (event.code) {
            case "Enter":
                onEnter(event);
                break;

            case "Space":
                onDelimiter(event);
                break;

            case "Backspace":
                if (isCollapsed()) {
                    onBackspace(event);
                }
                break;

            case "Delete":
                if (isCollapsed()) {
                    onDelete(event);
                }
                break;

            case "ArrowLeft":
                if (isEmpty()) {
                    joinWithPreviousTag(event);
                } else if (caretAtStart()) {
                    dispatch("tagmoveprevious");
                    event.preventDefault();
                }
                break;

            case "ArrowRight":
                if (isEmpty()) {
                    joinWithNextTag(event);
                } else if (caretAtEnd()) {
                    dispatch("tagmovenext");
                    event.preventDefault();
                }
                break;
        }

        if (event.key === ":") {
            onDelimiter(event, true);
        }
    }

    function onCopy(event: ClipboardEvent): void {
        const selection = document.getSelection();
        event.clipboardData!.setData(
            "text/plain",
            replaceWithColons(selection!.toString())
        );
    }

    function onPaste(event: ClipboardEvent): void {
        if (!event.clipboardData) {
            return;
        }

        const pasted = name + event.clipboardData.getData("text/plain");
        const splitted = pasted
            .split(/\s+/)
            .map(normalizeTagname)
            .filter((name: string) => name.length > 0)
            .map(replaceWithUnicodeSeparator);

        if (splitted.length === 0) {
            return;
        }

        const last = splitted.pop()!;

        for (const pastedName of splitted.reverse()) {
            name = pastedName;
            dispatch("tagadd");
        }

        name = last;
    }

    onMount(() => input.focus());
</script>

<input
    {id}
    class={className}
    bind:this={input}
    bind:value={name}
    type="text"
    tabindex="-1"
    size="1"
    on:focus
    on:blur|preventDefault={onBlur}
    on:keydown={onKeydown}
    on:keydown
    on:keyup
    on:input
    on:copy|preventDefault={onCopy}
    on:paste|preventDefault={onPaste}
/>

<style lang="scss">
    input {
        width: 100%;
        height: 100%;

        color: var(--text-fg);
        background: none;
        resize: none;
        appearance: none;
        font: inherit;
        /* TODO we need something like --base-font-size for buttons' 13px */
        font-size: 13px;
        outline: none;
        border: none;
        margin: 0;
    }
</style>
