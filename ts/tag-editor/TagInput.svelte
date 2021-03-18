<script lang="typescript">
    import { onMount } from "svelte";

    export let name = "";
    export let autofocus = false;

    let input: HTMLInputElement;

    function onKeydown(event: KeyboardEvent): void {
        if (event.code === "Space") {
            name += "::";
            event.preventDefault();
        }
        else if (event.code === "Enter") {
            input.blur();
            event.preventDefault();
        }
    }

    function moveCursorToEnd(element: Element): void {
        element.selectionStart = element.selectionEnd = element.value.length;
    }

    function caretToEnd(element: Element): void {
        const range = document.createRange();
        range.selectNodeContents(element);
        range.collapse(false);
        const selection = currentField.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
    }

    onMount(() => {
        if (autofocus) {
            input.focus();
            moveCursorToEnd(input);
        }
    })
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

<input type="text" bind:this={input} bind:value={name} on:keydown={onKeydown} on:blur />
