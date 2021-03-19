<script lang="typescript">
    import { onMount } from "svelte";

    import TagInput from "./TagInput.svelte";

    export let name;

    let input: HTMLInputElement;

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
        input.focus();
        moveCursorToEnd(input);
    });

    function onKeydown(event: KeyboardEvent): void {
        console.log("onkeydown");
    }
</script>

<TagInput bind:name bind:input on:keydown={onKeydown} on:blur />
