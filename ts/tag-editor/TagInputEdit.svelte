<script lang="typescript">
    import TagInput from "./TagInput.svelte";

    export let name;

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

    function focus(event: FocusEvent): void {
        event.details.input.focus();
        moveCursorToEnd(event.details.input);
    }

    function onKeydown(event: KeyboardEvent): void {
        console.log("onkeydown");
    }
</script>

<TagInput bind:name on:mount={focus} on:keydown={onKeydown} on:blur />
